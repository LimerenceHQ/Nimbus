package cc.nimbus.features.module.modules.movement;

import cc.nimbus.core.Core;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.game.EventClick;
import cc.nimbus.event.impl.game.EventTick;
import cc.nimbus.event.impl.game.EventWorldLoad;
import cc.nimbus.event.impl.player.EventMoveInput;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.submodule.ModeValue;
import cc.nimbus.features.module.api.setting.impl.NumberSetting;
import cc.nimbus.features.rotation.MovementFix;
import cc.nimbus.features.rotation.Rotation;
import cc.nimbus.features.rotation.RotationMath;
import cc.nimbus.features.rotation.RotationPriority;
import cc.nimbus.features.rotation.RotationRequest;
import cc.nimbus.features.rotation.SmoothMode;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@ModuleInfo(name = "NoFall", category = ModuleCategory.Move, description = "Prevents fall damage with water-bucket and ladder MLG actions.")
public final class NoFall extends BasicModule {
    private final Minecraft minecraft = Minecraft.getInstance();
    private static final int LADDER_ONLY_TICKS = 1;
    private static final int LADDER_WITH_BLOCK_MAX_TICKS = 3;
    private static final Direction[] HORIZONTAL_DIRECTIONS = new Direction[]{
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };

    public final ModeValue mode = new ModeValue("Mode", new String[]{"MLG"}, "MLG");
    public final ModeValue distanceMode = new ModeValue("Fall Distance Mode", new String[]{"Safe Distance", "Custom"}, "Safe Distance");
    public final NumberSetting fallDistance = new NumberSetting("Fall Distance", 3.0, 0.0, 10.0, 0.1, () -> distanceMode.is("Custom"));

    private boolean interactRequired = false;
    private int oldSlot = -1;
    private boolean shouldReceive = false;
    private PlaceData lastData;
    private PendingAction pendingAction = PendingAction.NONE;
    private LadderStep ladderStep = LadderStep.NONE;
    private LadderPlan ladderPlan;
    private BlockHitResult pendingHit;
    private Rotation pendingRotation;

    private record PlaceData(BlockPos pos, AABB bb) {
    }

    private record LandingPrediction(BlockPos groundPos, BlockPos feetPos, AABB playerBox, AABB fallPathBox, int ticksToGround) {
    }

    private record LadderPlan(BlockPos ladderPos, BlockPos supportPos, Direction supportFace, BlockHitResult supportHit,
                              Rotation supportRotation, BlockHitResult ladderHit, Rotation ladderRotation,
                              boolean needsSupportBlock) {
    }

    private enum PendingAction {
        NONE,
        ITEM_USE,
        BLOCK_USE
    }

    private enum LadderStep {
        NONE,
        PLACE_SUPPORT,
        PLACE_LADDER
    }

    @Override
    protected void onEnable() {
        interactRequired = false;
        shouldReceive = false;
        oldSlot = -1;
        lastData = null;
        clearPendingAction();
        clearLadderPlan();
    }

    @Override
    protected void onDisable() {
        interactRequired = false;
        shouldReceive = false;
        lastData = null;
        restoreSlot();
        clearPendingAction();
        clearLadderPlan();
        cancelRotation();
    }

    @EventTarget
    private void onWorldLoad(EventWorldLoad event) {
        onDisable();
    }

    @EventTarget
    private void onTick(EventTick event) {
        if (event.getPhase() != EventTick.Phase.PRE) return;
        if (minecraft.player == null || minecraft.level == null || minecraft.gameMode == null) return;
        if (minecraft.player.isCreative()) return;
        if (minecraft.player.isUsingItem() || minecraft.screen != null) return;

        if (mode.is("MLG")) {
            if (pendingAction != PendingAction.NONE && pendingRotation != null) {
                submitRotation(pendingRotation, 1, MovementFix.SILENT);
                return;
            }

            if (minecraft.player.onGround()) {
                clearLadderPlan();
            }

            if (ladderStep == LadderStep.PLACE_LADDER && ladderPlan != null) {
                beginLadderPlace();
                return;
            }

            boolean shouldMlg = shouldMlgClutch();
            if (shouldMlg) {
                int waterBucketSlot = findHotbarSlot(Items.WATER_BUCKET);
                if (waterBucketSlot != -1 && findLandingPrediction(1) != null) {
                    placeWaterBucket(waterBucketSlot);
                    return;
                }

                if (waterBucketSlot == -1 && tryBeginLadderClutch()) {
                    return;
                }
            }

            if (!shouldMlg && hasEmptyBucket() && shouldReceive) {
                retrieveWaterBlock();
            }
        }
    }

    @EventTarget
    private void onClick(EventClick event) {
        if (Core.INSTANCE.getRotationManager().getTargetRotation() == null || minecraft.player == null || minecraft.gameMode == null) return;

        if (mode.is("MLG")) {
            if (interactRequired) {
                performPendingAction();
            }
        }
    }

    @EventTarget
    private void onMoveInput(EventMoveInput event) {
        if ((lastData != null || ladderPlan != null) && minecraft.player != null && mode.is("MLG")) {
            minecraft.player.setSprinting(false);
        }
    }

    private boolean shouldMlgClutch() {
        if (minecraft.player == null) {
            return false;
        }

        boolean enoughFallDistance = distanceMode.is("Safe Distance")
                ? minecraft.player.fallDistance > minecraft.player.getMaxFallDistance()
                : minecraft.player.fallDistance > fallDistance.getValue().doubleValue();
        return enoughFallDistance
                && !minecraft.player.isInWater()
                && !minecraft.player.onGround()
                && minecraft.player.getDeltaMovement().y < 0.0D;
    }

    private boolean tryBeginLadderClutch() {
        int ladderSlot = findHotbarSlot(Items.LADDER);
        if (ladderSlot == -1) {
            return false;
        }

        LandingPrediction prediction = findLandingPrediction(LADDER_WITH_BLOCK_MAX_TICKS);
        if (prediction == null) {
            return false;
        }

        LadderPlan plan = findLadderPlan(prediction);
        if (plan == null) {
            return false;
        }

        if (plan.needsSupportBlock()) {
            if (prediction.ticksToGround() > LADDER_WITH_BLOCK_MAX_TICKS) {
                return false;
            }

            int blockSlot = findFullBlockSlot();
            if (blockSlot == -1) {
                return false;
            }

            ladderPlan = plan;
            ladderStep = LadderStep.PLACE_SUPPORT;
            beginBlockUse(blockSlot, plan.supportRotation(), plan.supportHit());
            return true;
        }

        if (prediction.ticksToGround() > LADDER_ONLY_TICKS) {
            return false;
        }

        ladderPlan = plan;
        ladderStep = LadderStep.PLACE_LADDER;
        beginLadderPlace();
        return true;
    }

    private void beginLadderPlace() {
        if (ladderPlan == null) {
            clearLadderPlan();
            return;
        }

        int ladderSlot = findHotbarSlot(Items.LADDER);
        if (ladderSlot == -1 || !canPlaceLadderAt(ladderPlan.ladderPos()) || !canSupportLadder(ladderPlan.supportPos(), ladderPlan.supportFace())) {
            clearLadderPlan();
            return;
        }

        ladderStep = LadderStep.PLACE_LADDER;
        beginBlockUse(ladderSlot, ladderPlan.ladderRotation(), ladderPlan.ladderHit());
    }

    private void beginBlockUse(int slot, Rotation rotation, BlockHitResult hit) {
        if (minecraft.player == null || slot == -1 || rotation == null || hit == null) {
            return;
        }

        oldSlot = minecraft.player.getInventory().getSelectedSlot();
        minecraft.player.getInventory().setSelectedSlot(slot);
        pendingAction = PendingAction.BLOCK_USE;
        pendingRotation = rotation;
        pendingHit = hit;
        interactRequired = true;
        submitRotation(rotation, 1, MovementFix.SILENT, RotationPriority.HIGH);
    }

    private void performPendingAction() {
        if (minecraft.player == null || minecraft.gameMode == null || pendingAction == PendingAction.NONE) {
            restoreSlot();
            clearPendingAction();
            return;
        }

        InteractionResult result = InteractionResult.FAIL;
        if (pendingAction == PendingAction.ITEM_USE) {
            Rotation targetRotation = Core.INSTANCE.getRotationManager().getTargetRotation();
            if (targetRotation != null) {
                BlockHitResult raycast = rayCastBlock(targetRotation, minecraft.player, minecraft.level, 4.5, false);
                if (raycast != null) {
                    minecraft.hitResult = raycast;
                    result = useItemWithRotation(pendingRotation);
                }
            }
        } else if (pendingAction == PendingAction.BLOCK_USE && pendingHit != null) {
            minecraft.hitResult = pendingHit;
            result = useBlockWithRotation(pendingRotation, pendingHit);
        }

        boolean accepted = result.consumesAction();
        if (accepted) {
            minecraft.player.swing(InteractionHand.MAIN_HAND);
        }

        boolean bucketInHand = minecraft.player.getInventory().getItem(minecraft.player.getInventory().getSelectedSlot()).is(Items.BUCKET);
        PendingAction completedAction = pendingAction;
        LadderStep completedStep = ladderStep;

        restoreSlot();
        clearPendingAction();

        if (completedAction == PendingAction.ITEM_USE && bucketInHand) {
            shouldReceive = true;
        } else if (completedAction == PendingAction.BLOCK_USE && completedStep == LadderStep.PLACE_SUPPORT && accepted) {
            if (findLandingPrediction(1) != null) {
                placeLadderImmediately();
            } else {
                ladderStep = LadderStep.PLACE_LADDER;
            }
        } else if (completedAction == PendingAction.BLOCK_USE && completedStep == LadderStep.PLACE_LADDER && accepted) {
            clearLadderPlan();
        }
    }

    private void placeLadderImmediately() {
        if (minecraft.player == null || minecraft.gameMode == null || ladderPlan == null) {
            clearLadderPlan();
            return;
        }

        int ladderSlot = findHotbarSlot(Items.LADDER);
        if (ladderSlot == -1) {
            clearLadderPlan();
            return;
        }

        oldSlot = minecraft.player.getInventory().getSelectedSlot();
        minecraft.player.getInventory().setSelectedSlot(ladderSlot);
        minecraft.hitResult = ladderPlan.ladderHit();
        InteractionResult ladderResult = useBlockWithRotation(ladderPlan.ladderRotation(), ladderPlan.ladderHit());
        if (ladderResult.consumesAction()) {
            minecraft.player.swing(InteractionHand.MAIN_HAND);
            clearLadderPlan();
        } else {
            ladderStep = LadderStep.PLACE_LADDER;
        }
        restoreSlot();
    }

    private InteractionResult useItemWithRotation(Rotation rotation) {
        if (minecraft.player == null || minecraft.gameMode == null || rotation == null) {
            return InteractionResult.FAIL;
        }

        float currentYaw = minecraft.player.getYRot();
        float currentPitch = minecraft.player.getXRot();
        minecraft.player.setYRot(rotation.yaw());
        minecraft.player.setXRot(rotation.pitch());
        InteractionResult result = minecraft.gameMode.useItem(minecraft.player, InteractionHand.MAIN_HAND);
        minecraft.player.setYRot(currentYaw);
        minecraft.player.setXRot(currentPitch);
        return result;
    }

    private InteractionResult useBlockWithRotation(Rotation rotation, BlockHitResult hit) {
        if (minecraft.player == null || minecraft.gameMode == null || rotation == null || hit == null) {
            return InteractionResult.FAIL;
        }

        float currentYaw = minecraft.player.getYRot();
        float currentPitch = minecraft.player.getXRot();
        minecraft.player.setYRot(rotation.yaw());
        minecraft.player.setXRot(rotation.pitch());
        InteractionResult result = minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit);
        minecraft.player.setYRot(currentYaw);
        minecraft.player.setXRot(currentPitch);
        return result;
    }

    private void retrieveWaterBlock() {
        if (minecraft.player == null) return;

        int emptyBucketSlot = findHotbarSlot(Items.BUCKET);
        if (emptyBucketSlot == -1) return;
        BlockPos pos = findScoopableWaterBlock();
        if (pos == null) {
            return;
        }
        oldSlot = minecraft.player.getInventory().getSelectedSlot();
        Rotation r = getLookAtWaterBlock(pos);

        if (!minecraft.player.getMainHandItem().is(Items.BUCKET))
            minecraft.player.getInventory().setSelectedSlot(emptyBucketSlot);
        interactItem(r);
        shouldReceive = false;
    }

    private BlockPos findScoopableWaterBlock() {
        if (minecraft.player == null || minecraft.level == null) return null;

        Level level = minecraft.level;
        BlockPos playerBlockPos = minecraft.player.blockPosition();
        double maxReachDistance = minecraft.player.blockInteractionRange();
        int reach = (int) Math.ceil(maxReachDistance);
        List<BlockPos> possible = new ArrayList<>();
        for (int x = -reach; x <= reach; x++) {
            for (int y = -reach; y <= reach; y++) {
                for (int z = -reach; z <= reach; z++) {

                    BlockPos currentPos = playerBlockPos.offset(x, y, z);

                    FluidState fluidState = level.getFluidState(currentPos);

                    if (fluidState.isSource() && fluidState.getType() == Fluids.WATER) {
                        possible.add(currentPos);
                    }
                }
            }
        }
        possible.removeIf(blockPos -> {
            Rotation rotation = getLookAtWaterBlock(blockPos);
            BlockHitResult hit = rayCastBlock(rotation, minecraft.player, minecraft.level, 4.5, true);
            return hit.getType() != HitResult.Type.BLOCK;
        });
        if (possible.isEmpty()) return null;
        return possible.get(0);
    }

    private LandingPrediction findLandingPrediction(int maxTicks) {
        if (minecraft.player == null || minecraft.level == null || maxTicks <= 0) {
            return null;
        }

        AABB box = minecraft.player.getBoundingBox();
        AABB fallPathBox = box;
        Vec3 velocity = minecraft.player.getDeltaMovement();
        for (int tick = 1; tick <= maxTicks; tick++) {
            Vec3 clipped = Entity.collideBoundingBox(minecraft.player, velocity, box, minecraft.level, List.of());
            AABB nextBox = box.move(clipped);
            fallPathBox = union(fallPathBox, nextBox);
            if (velocity.y < 0.0D && clipped.y > velocity.y + 1.0E-5D) {
                BlockPos groundPos = findGroundUnder(nextBox);
                if (groundPos != null) {
                    BlockPos feetPos = BlockPos.containing(nextBox.getCenter().x, groundPos.getY() + 1.0D, nextBox.getCenter().z);
                    return new LandingPrediction(groundPos, feetPos, nextBox, fallPathBox, tick);
                }
            }

            box = nextBox;
            double nextY = clipped.y == velocity.y ? (velocity.y - 0.08D) * 0.98D : 0.0D;
            velocity = new Vec3(velocity.x * 0.91D, nextY, velocity.z * 0.91D);
        }

        return null;
    }

    private BlockPos findGroundUnder(AABB box) {
        if (minecraft.level == null) {
            return null;
        }

        int minX = Mth.floor(box.minX + 1.0E-4D);
        int maxX = Mth.floor(box.maxX - 1.0E-4D);
        int minZ = Mth.floor(box.minZ + 1.0E-4D);
        int maxZ = Mth.floor(box.maxZ - 1.0E-4D);
        int y = Mth.floor(box.minY - 1.0E-4D);
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        Vec3 center = box.getCenter();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos pos = new BlockPos(x, y, z);
                if (!isSolidBlock(pos)) {
                    continue;
                }

                double distance = Vec3.atCenterOf(pos).distanceToSqr(center);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = pos;
                }
            }
        }

        return best;
    }

    private LadderPlan findLadderPlan(LandingPrediction prediction) {
        if (minecraft.player == null || minecraft.level == null || prediction == null || !canPlaceLadderAt(prediction.feetPos())) {
            return null;
        }

        LadderPlan existingSupport = findExistingSupportLadderPlan(prediction);
        if (existingSupport != null) {
            return existingSupport;
        }

        if (findFullBlockSlot() == -1) {
            return null;
        }

        return findSupportBlockLadderPlan(prediction);
    }

    private LadderPlan findExistingSupportLadderPlan(LandingPrediction prediction) {
        List<LadderPlan> plans = new ArrayList<>();
        for (BlockPos ladderPos : getBodyLadderPositions(prediction)) {
            if (!canPlaceLadderAt(ladderPos)) {
                continue;
            }

            for (Direction direction : HORIZONTAL_DIRECTIONS) {
                BlockPos supportPos = ladderPos.relative(direction);
                Direction supportFace = direction.getOpposite();
                if (!canSupportLadder(supportPos, supportFace)) {
                    continue;
                }

                BlockHitResult ladderHit = getFaceHit(supportPos, supportFace);
                Rotation ladderRotation = getHitRotation(ladderHit);
                if (isReachableBlockHit(ladderHit, ladderRotation)) {
                    plans.add(new LadderPlan(ladderPos, supportPos, supportFace, null, null, ladderHit, ladderRotation, false));
                }
            }
        }

        return getBestLadderPlan(plans);
    }

    private LadderPlan findSupportBlockLadderPlan(LandingPrediction prediction) {
        List<LadderPlan> plans = new ArrayList<>();
        for (BlockPos ladderPos : getBodyLadderPositions(prediction)) {
            if (!canPlaceLadderAt(ladderPos)) {
                continue;
            }

            for (Direction direction : HORIZONTAL_DIRECTIONS) {
                BlockPos supportPos = ladderPos.relative(direction);
                Direction supportFace = direction.getOpposite();
                if (!canPlaceSupportBlock(supportPos, prediction)) {
                    continue;
                }

                BlockHitResult supportHit = getSupportBlockPlaceHit(supportPos);
                Rotation supportRotation = getHitRotation(supportHit);
                BlockHitResult ladderHit = getFaceHit(supportPos, supportFace);
                Rotation ladderRotation = getHitRotation(ladderHit);
                if (isReachableBlockHit(supportHit, supportRotation) && isWithinReach(ladderHit)) {
                    plans.add(new LadderPlan(ladderPos, supportPos, supportFace, supportHit, supportRotation, ladderHit, ladderRotation, true));
                }
            }
        }

        return getBestLadderPlan(plans);
    }

    private List<BlockPos> getBodyLadderPositions(LandingPrediction prediction) {
        Set<BlockPos> positions = new LinkedHashSet<>();
        AABB box = prediction.playerBox();
        int y = prediction.feetPos().getY();
        int minX = Mth.floor(box.minX + 1.0E-4D);
        int maxX = Mth.floor(box.maxX - 1.0E-4D);
        int minZ = Mth.floor(box.minZ + 1.0E-4D);
        int maxZ = Mth.floor(box.maxZ - 1.0E-4D);

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                positions.add(new BlockPos(x, y, z));
            }
        }

        Vec3 center = box.getCenter();
        List<BlockPos> sorted = new ArrayList<>(positions);
        sorted.sort(Comparator.comparingDouble(pos -> Vec3.atCenterOf(pos).distanceToSqr(center)));
        return sorted;
    }

    private LadderPlan getBestLadderPlan(List<LadderPlan> plans) {
        if (plans.isEmpty()) {
            return null;
        }

        Rotation current = currentRotation();
        return plans.stream()
                .min(Comparator
                        .comparingDouble((LadderPlan plan) -> current.distanceTo(
                                plan.needsSupportBlock() ? plan.supportRotation() : plan.ladderRotation()))
                        .thenComparingDouble(plan -> minecraft.player == null ? 0.0D : Vec3.atCenterOf(plan.ladderPos()).distanceToSqr(minecraft.player.position())))
                .orElse(null);
    }

    private BlockHitResult getSupportBlockPlaceHit(BlockPos supportPos) {
        BlockPos clicked = supportPos.below();
        Vec3 hitVec = new Vec3(clicked.getX() + 0.5D, clicked.getY() + 1.0D, clicked.getZ() + 0.5D);
        return new BlockHitResult(hitVec, Direction.UP, clicked, false);
    }

    private BlockHitResult getFaceHit(BlockPos blockPos, Direction face) {
        Vec3 hitVec = Vec3.atCenterOf(blockPos).add(
                face.getStepX() * 0.5D,
                face.getStepY() * 0.5D,
                face.getStepZ() * 0.5D
        );
        return new BlockHitResult(hitVec, face, blockPos, false);
    }

    private Rotation getHitRotation(BlockHitResult hit) {
        return hit == null ? null : toRotation(hit.getLocation());
    }

    private boolean isReachableBlockHit(BlockHitResult hit, Rotation rotation) {
        if (minecraft.player == null || minecraft.level == null || hit == null || rotation == null) {
            return false;
        }
        if (!isWithinReach(hit)) {
            return false;
        }

        BlockHitResult raycast = rayCastBlock(
                rotation, minecraft.player, minecraft.level, minecraft.player.blockInteractionRange(), false);
        return raycast != null
                && raycast.getType() == HitResult.Type.BLOCK
                && raycast.getBlockPos().equals(hit.getBlockPos())
                && raycast.getDirection() == hit.getDirection();
    }

    private boolean isWithinReach(BlockHitResult hit) {
        if (minecraft.player == null || hit == null) return false;
        double reach = minecraft.player.blockInteractionRange();
        return minecraft.player.getEyePosition().distanceTo(hit.getLocation()) <= reach;
    }

    private boolean canPlaceSupportBlock(BlockPos supportPos, LandingPrediction prediction) {
        return minecraft.level != null
                && supportPos != null
                && prediction != null
                && isReplaceable(supportPos)
                && !wouldSupportBlockCatchPlayer(supportPos, prediction)
                && isSolidBlock(supportPos.below())
                && minecraft.level.getBlockState(supportPos.below()).isFaceSturdy(minecraft.level, supportPos.below(), Direction.UP);
    }

    private boolean wouldSupportBlockCatchPlayer(BlockPos supportPos, LandingPrediction prediction) {
        AABB supportBox = new AABB(supportPos).inflate(0.03D);
        if (supportBox.intersects(prediction.playerBox().inflate(0.03D))) {
            return true;
        }

        return supportBox.intersects(prediction.fallPathBox().inflate(0.03D, 0.0D, 0.03D));
    }

    private boolean canPlaceLadderAt(BlockPos pos) {
        return minecraft.level != null
                && pos != null
                && isReplaceable(pos)
                && minecraft.level.getFluidState(pos).isEmpty();
    }

    private boolean canSupportLadder(BlockPos supportPos, Direction clickedFace) {
        return minecraft.level != null
                && supportPos != null
                && minecraft.level.getBlockState(supportPos).isFaceSturdy(minecraft.level, supportPos, clickedFace);
    }

    private boolean isReplaceable(BlockPos pos) {
        if (minecraft.level == null) {
            return false;
        }

        BlockState state = minecraft.level.getBlockState(pos);
        return (state.isAir() || state.canBeReplaced()) && state.getFluidState().isEmpty();
    }

    private boolean isSolidBlock(BlockPos pos) {
        if (minecraft.level == null || pos == null) {
            return false;
        }

        BlockState state = minecraft.level.getBlockState(pos);
        return !state.isAir() && !state.getCollisionShape(minecraft.level, pos).isEmpty();
    }

    private boolean hasEmptyBucket() {
        if (minecraft.player == null) return false;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = minecraft.player.getInventory().getItem(i);
            if (stack != null && stack.is(Items.BUCKET)) {
                return true;
            }
        }
        return false;
    }

    private void interactItem(Rotation rotation) {
        pendingAction = PendingAction.ITEM_USE;
        pendingRotation = rotation;
        pendingHit = null;
        submitRotation(rotation, 1, MovementFix.SILENT);
        interactRequired = true;
    }

    private int findFullBlockSlot() {
        if (minecraft.player == null || minecraft.level == null) {
            return -1;
        }

        for (int i = 0; i < 9; i++) {
            ItemStack stack = minecraft.player.getInventory().getItem(i);
            if (stack != null && stack.getItem() instanceof BlockItem blockItem && isFullBlockItem(blockItem)) {
                return i;
            }
        }

        return -1;
    }

    private boolean isFullBlockItem(BlockItem blockItem) {
        if (minecraft.level == null || blockItem == null) {
            return false;
        }

        BlockState state = blockItem.getBlock().defaultBlockState();
        BlockPos origin = BlockPos.ZERO;
        if (state.isAir() || state.getDestroySpeed(minecraft.level, origin) < 0.0F) {
            return false;
        }

        for (Direction direction : Direction.values()) {
            if (!state.isFaceSturdy(minecraft.level, origin, direction)) {
                return false;
            }
        }

        return true;
    }

    private void restoreSlot() {
        if (oldSlot != -1 && minecraft.player != null) {
            minecraft.player.getInventory().setSelectedSlot(oldSlot);
        }
        oldSlot = -1;
    }

    private void clearPendingAction() {
        interactRequired = false;
        pendingAction = PendingAction.NONE;
        pendingHit = null;
        pendingRotation = null;
    }

    private void clearLadderPlan() {
        ladderPlan = null;
        ladderStep = LadderStep.NONE;
    }

    private PlaceData findBestPlacePos() {
        if (minecraft.player == null || minecraft.level == null) return null;

        LandingPrediction prediction = findLandingPrediction(1);
        if (prediction != null) {
            return new PlaceData(prediction.groundPos(), aabbAtHeight(prediction.groundPos(), 3));
        }

        List<PlaceData> possibleBoxes = new ArrayList<>();

        AABB playerBox = minecraft.player.getBoundingBox();
        AABB movedBox = playerBox.move(minecraft.player.getDeltaMovement());

        int xMotion = getXMotion();
        int zMotion = getZMotion();
        int yMotion = getYMotion();

        for (int x = -xMotion; x <= xMotion; x++) {
            for (int z = -zMotion; z <= zMotion; z++) {
                for (int y = 0; y <= yMotion; y++) {
                    BlockPos pos = minecraft.player.blockPosition().offset(x, 0, z).below(y);
                    BlockState state = minecraft.level.getBlockState(pos);

                    AABB bb = aabbAtHeight(pos, 3);
                    if (bb.intersects(movedBox) && !state.isAir()) {
                        possibleBoxes.add(new PlaceData(pos, bb));
                    }
                }
            }
        }

        if (possibleBoxes.isEmpty()) return null;

        possibleBoxes.sort(Comparator.comparingDouble(p -> p.pos.getY()));
        Collections.reverse(possibleBoxes);
        PlaceData best = possibleBoxes.get(0);

        possibleBoxes.removeIf(p -> p.pos.getY() != best.pos().getY());

        possibleBoxes.sort(Comparator.comparingDouble(p -> Vec3.atLowerCornerOf(p.pos()).distanceToSqr(
                new Vec3(movedBox.getCenter().x, movedBox.minY, movedBox.getCenter().z)
        )));
        possibleBoxes.sort(Comparator.comparing(p -> !p.bb.contains(minecraft.player.position())));
        return possibleBoxes.get(0);
    }

    private Rotation getLookAtWaterBlock(BlockPos targetPos) {
        if (minecraft.player == null || targetPos == null) {
            return currentRotation();
        }

        return toRotation(Vec3.atLowerCornerOf(targetPos));
    }

    private void placeWaterBucket(int waterBucketSlot) {
        if (waterBucketSlot == -1 || minecraft.player == null) {
            return;
        }

        PlaceData best = findBestPlacePos();
        lastData = best;

        if (best == null) return;

        oldSlot = minecraft.player.getInventory().getSelectedSlot();

        Rotation r = getLookAtWaterBlock(best.pos());

        if (!minecraft.player.getMainHandItem().is(Items.WATER_BUCKET))
            minecraft.player.getInventory().setSelectedSlot(waterBucketSlot);
        interactItem(r);
        shouldReceive = true;
    }

    private int getXMotion() {
        if (minecraft.player == null) return 1;
        return (int) Math.ceil(minecraft.player.getDeltaMovement().x * minecraft.player.getDeltaMovement().x);
    }

    private int getYMotion() {
        if (minecraft.player == null) return 1;
        return (int) Math.ceil(minecraft.player.getDeltaMovement().y * minecraft.player.getDeltaMovement().y);
    }

    private int getZMotion() {
        if (minecraft.player == null) return 1;
        return (int) Math.ceil(minecraft.player.getDeltaMovement().z * minecraft.player.getDeltaMovement().z);
    }

    private AABB union(AABB a, AABB b) {
        return new AABB(
                Math.min(a.minX, b.minX), Math.min(a.minY, b.minY), Math.min(a.minZ, b.minZ),
                Math.max(a.maxX, b.maxX), Math.max(a.maxY, b.maxY), Math.max(a.maxZ, b.maxZ)
        );
    }

    private AABB aabbAtHeight(BlockPos pos, int height) {
        return new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + height, pos.getZ() + 1);
    }

    private int findHotbarSlot(Item item) {
        if (minecraft.player == null) return -1;
        for (int slot = 0; slot < 9; slot++) {
            if (minecraft.player.getInventory().getItem(slot).is(item)) return slot;
        }
        return -1;
    }

    private Rotation toRotation(Vec3 point) {
        return minecraft.player == null
                ? new Rotation(0.0f, 0.0f)
                : RotationMath.toRotation(minecraft.player.getEyePosition(), point);
    }

    private Rotation currentRotation() {
        if (Core.INSTANCE.isInitialized()) return Core.INSTANCE.getRotationManager().getEffectiveRotation();
        return minecraft.player == null
                ? new Rotation(0.0f, 0.0f)
                : new Rotation(minecraft.player.getYRot(), minecraft.player.getXRot());
    }

    private BlockHitResult rayCastBlock(Rotation rotation, Entity source, Level level,
                                        double range, boolean includeFluids) {
        Vec3 start = source.getEyePosition();
        Vec3 end = start.add(RotationMath.direction(rotation).scale(range));
        return level.clip(new ClipContext(
                start, end, ClipContext.Block.OUTLINE,
                includeFluids ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE, source));
    }

    private void submitRotation(Rotation rotation, int keepTicks, MovementFix movementFix) {
        submitRotation(rotation, keepTicks, movementFix, RotationPriority.HIGH);
    }

    private void submitRotation(Rotation rotation, int keepTicks, MovementFix movementFix,
                                RotationPriority priority) {
        Core.INSTANCE.getRotationManager().submit(this, new RotationRequest(
                rotation, priority, movementFix, true, SmoothMode.ADVANCED,
                180.0f, keepTicks, 0));
    }

    private void cancelRotation() {
        if (Core.INSTANCE.isInitialized()) Core.INSTANCE.getRotationManager().cancel(this);
    }
}
