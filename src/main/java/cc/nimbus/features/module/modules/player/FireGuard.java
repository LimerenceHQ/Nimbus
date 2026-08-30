package cc.nimbus.features.module.modules.player;

import cc.nimbus.core.Core;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.game.*;
import cc.nimbus.event.impl.player.EventMoveInput;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.BooleanSetting;
import cc.nimbus.features.module.api.setting.impl.ModeSetting;
import cc.nimbus.features.module.api.setting.impl.submodule.ModeValue;
import cc.nimbus.features.module.api.setting.impl.NumberSetting;
import cc.nimbus.features.module.modules.combat.KillAura;
import cc.nimbus.features.module.modules.combat.AutoTrap;
import cc.nimbus.features.module.modules.movement.Scaffold;
import cc.nimbus.features.rotation.*;
import cc.nimbus.utils.entity.rotation.RotationUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static cc.nimbus.features.module.modules.movement.AntiWeb.findHotbarSlot;

@ModuleInfo(name = "FireGuard", description = "Protects the player from nearby fire and lava hazards.", category = ModuleCategory.Player)
public final class FireGuard extends BasicModule {
    private static final double MAX_BLOCK_INTERACTION_RANGE = 4.5D;
    private static final int SEARCH_RADIUS = 5;
    private static final Direction[] PLACE_DIRECTIONS = new Direction[]{
            Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN
    };
    private static final double[] SAMPLE_OFFSETS = new double[]{
            0.08D, 0.20D, 0.32D, 0.44D, 0.56D, 0.68D, 0.80D, 0.92D
    };

    private final BooleanSetting lavaSeal = new BooleanSetting("Lava Seal", true);
    private final BooleanSetting flameClear = new BooleanSetting("Flame Clear", true);
    private final BooleanSetting waterRescue = new BooleanSetting("Water Rescue", true);
    private final NumberSetting rotationSpeed = new NumberSetting("Rotation Speed", 180.0, 0.0, 180.0, 1.0);
    private final ModeSetting movementFix = new ModeSetting("Movement Fix", new String[]{"None", "Strict", "Silent"}, "Silent");
    private final ModeSetting swingMode = new ModeSetting("Swing Mode", new String[]{"Off", "Normal", "Silent"}, "Normal");

    private Stage stage = Stage.IDLE;
    private PlaceAction lavaAction;
    private BreakAction fireAction;
    private BucketAction waterAction;
    private BucketAction pickupAction;
    private int oldSlot = -1;
    private int cooldownTicks;
    private boolean shouldPickupWater;

    @Override
    public void onEnable() {
        reset(true);
        cooldownTicks = 0;
        shouldPickupWater = false;
    }

    @Override
    public void onDisable() {
        reset(true);
        cooldownTicks = 0;
        shouldPickupWater = false;
    }

    @EventTarget
    public void onTick(EventTick event) {
        if (event.isPost()) return;
        if (cooldownTicks > 0) {
            cooldownTicks--;
        }
    }

    @EventTarget
    public void onRotation(EventRotation event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null || mc.screen != null) {
            reset(true);
            return;
        }

        if (stage == Stage.WATER_PLACE && waterAction != null) {
            applyRotation(waterAction.rotation());
            return;
        }

        if (stage == Stage.WATER_PICKUP && pickupAction != null) {
            applyRotation(pickupAction.rotation());
            return;
        }

        if (stage == Stage.LAVA_SEAL && lavaAction != null) {
            mc.player.getInventory().selected = findHotbarBlockSlot();
            applyRotation(lavaAction.rotation());
            return;
        }

        if (stage == Stage.FIRE_CLEAR && fireAction != null) {
            applyRotation(fireAction.rotation());
            return;
        }

        if (cooldownTicks > 0 || Core.INSTANCE.getModuleManager().getModule(Scaffold.class).isEnabled()) {
            return;
        }

        if (waterRescue.getValue()) {
            if (shouldPlaceWater()) {
                BucketAction action = findWaterPlaceAction();
                int waterSlot = findHotbarSlot(Items.WATER_BUCKET);
                if (action != null && waterSlot != -1) {
                    oldSlot = mc.player.getInventory().selected;
                    mc.player.getInventory().selected = waterSlot;
                    waterAction = action;
                    stage = Stage.WATER_PLACE;
                    applyRotation(action.rotation());
                    return;
                }
            } else if (shouldPickupWater && findHotbarSlot(Items.BUCKET) != -1) {
                pickupAction = findWaterPickupAction();
                if (pickupAction != null) {
                    oldSlot = mc.player.getInventory().selected;
                    mc.player.getInventory().selected = findHotbarSlot(Items.BUCKET);
                    stage = Stage.WATER_PICKUP;
                    applyRotation(pickupAction.rotation());
                    return;
                }
            }
        }

        if (lavaSeal.getValue() && notInStillLava() && findHotbarBlockSlot() != -1) {
            lavaAction = findLavaAction();
            if (lavaAction != null) {
                oldSlot = mc.player.getInventory().selected;
                mc.player.getInventory().selected = findHotbarBlockSlot();
                stage = Stage.LAVA_SEAL;
                applyRotation(lavaAction.rotation());
                return;
            }
        }

        if (flameClear.getValue()) {
            fireAction = findFireAction();
            if (fireAction != null) {
                stage = Stage.FIRE_CLEAR;
                applyRotation(fireAction.rotation());
            }
        }
    }

    @EventTarget
    public void onRotationApplied(RotationAppliedEvent event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            reset(true);
            return;
        }

        if (stage == Stage.WATER_PLACE && waterAction != null) {
            InteractionResult result = useItem(waterAction.rotation(), waterAction.hit());
            if (result.consumesAction() || mc.player.getMainHandItem().getItem() == Items.BUCKET) {
                swing();
                shouldPickupWater = true;
            }
            reset(true);
            cooldownTicks = 1;
            return;
        }

        if (stage == Stage.WATER_PICKUP && pickupAction != null) {
            InteractionResult result = useItem(pickupAction.rotation(), pickupAction.hit());
            if (result.consumesAction() || mc.player.getMainHandItem().getItem() == Items.WATER_BUCKET) {
                swing();
                shouldPickupWater = false;
            }
            reset(true);
            cooldownTicks = 1;
            return;
        }

        if (stage == Stage.LAVA_SEAL && lavaAction != null) {
            InteractionResult result = useItemOn(lavaAction.rotation(), lavaAction.hit());
            if (result.consumesAction() || !isStillLava(lavaAction.pos())) {
                swing();
            }
            reset(true);
            cooldownTicks = 1;
            return;
        }

        if (stage == Stage.FIRE_CLEAR && fireAction != null) {
            mc.hitResult = fireAction.hit();
            if (mc.gameMode.startDestroyBlock(fireAction.pos(), fireAction.hit().getDirection())) {
                swing();
            }
            reset(true);
            cooldownTicks = 1;
        }
    }

    @EventTarget
    public void onMoveInput(EventMoveInput event) {
        if ((stage == Stage.WATER_PLACE || shouldPickupWater) && mc.player != null) {
            mc.player.setSprinting(false);
        }
    }

    private boolean shouldPlaceWater() {
        if (mc.player == null || mc.level == null) {
            return false;
        }

        KillAura killAura = Core.INSTANCE.getModuleManager().getModule(KillAura.class);
        return mc.player.isOnFire()
                && !mc.player.hasEffect(MobEffects.FIRE_RESISTANCE)
                && !mc.player.isInWaterOrBubble()
                && !mc.player.isCreative()
                && !mc.player.isUsingItem()
                && (killAura == null || killAura.attackTarget == null)
                && predictLandingSupport() != null
                && findHotbarSlot(Items.WATER_BUCKET) != -1;
    }

    private BucketAction findWaterPlaceAction() {
        SupportHit support = predictLandingSupport();
        if (support == null) {
            return null;
        }

        return new BucketAction(support.hit(), support.rotation());
    }

    private SupportHit predictLandingSupport() {
        if (mc.player == null || mc.level == null) {
            return null;
        }

        AABB box = mc.player.getBoundingBox();
        Vec3 velocity = mc.player.getDeltaMovement();
        Vec3 clipped = Entity.collideBoundingBox(mc.player, velocity, box, mc.level, List.of());
        if (velocity.y >= 0.0D || clipped.y == velocity.y) {
            return null;
        }

        return findBestSupport(box.move(clipped));
    }

    private SupportHit findBestSupport(AABB predictedBox) {
        if (mc.player == null || mc.level == null) {
            return null;
        }

        List<SupportHit> supports = new ArrayList<>();
        AABB feetBox = new AABB(
                predictedBox.minX + 1.0E-4D,
                predictedBox.minY - 0.08D,
                predictedBox.minZ + 1.0E-4D,
                predictedBox.maxX - 1.0E-4D,
                predictedBox.minY + 0.08D,
                predictedBox.maxZ - 1.0E-4D
        );

        int minX = (int) Math.floor(feetBox.minX);
        int maxX = (int) Math.floor(feetBox.maxX);
        int minY = (int) Math.floor(feetBox.minY);
        int maxY = (int) Math.floor(feetBox.maxY);
        int minZ = (int) Math.floor(feetBox.minZ);
        int maxZ = (int) Math.floor(feetBox.maxZ);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = mc.level.getBlockState(pos);
                    VoxelShape shape = state.getCollisionShape(mc.level, pos);
                    if (state.isAir() || shape.isEmpty()) {
                        continue;
                    }

                    for (AABB localBox : shape.toAabbs()) {
                        AABB worldBox = localBox.move(pos);
                        if (!worldBox.intersects(feetBox)) {
                            continue;
                        }

                        Vec3 hitVec = new Vec3(
                                clamp(predictedBox.getCenter().x, worldBox.minX + 0.001D, worldBox.maxX - 0.001D),
                                worldBox.maxY,
                                clamp(predictedBox.getCenter().z, worldBox.minZ + 0.001D, worldBox.maxZ - 0.001D)
                        );
                        Rotation rotation = RotationUtils.toRotation(hitVec).normalized();
                        BlockHitResult hit = RotationRaycast.rayCast(rotation, mc.player, mc.level, getReach(), false);
                        if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos)) {
                            supports.add(new SupportHit(pos, hit, rotation, worldBox));
                        }
                    }
                }
            }
        }

        if (supports.isEmpty()) {
            return null;
        }

        Vec3 feetCenter = new Vec3(predictedBox.getCenter().x, predictedBox.minY, predictedBox.getCenter().z);
        return supports.stream()
                .min(Comparator
                        .comparingDouble((SupportHit support) -> -support.box().maxY)
                        .thenComparingDouble(support -> support.hit().getLocation().distanceToSqr(feetCenter)))
                .orElse(null);
    }

    private PlaceAction findLavaAction() {
        if (mc.player == null || mc.level == null) {
            return null;
        }

        List<PlaceAction> actions = new ArrayList<>();
        for (BlockPos pos : searchAroundPlayer(SEARCH_RADIUS)) {
            if (!isStillLava(pos) || hasEntityInside(pos)) {
                continue;
            }

            PlaceHit hit = findPlaceHit(pos);
            if (hit != null) {
                actions.add(new PlaceAction(pos, hit.hit(), hit.rotation()));
            }
        }

        if (actions.isEmpty()) {
            return null;
        }

        Rotation current = Core.INSTANCE.getRotationManager().getEffectiveRotation();
        return actions.stream()
                .min(Comparator
                        .comparingDouble((PlaceAction action) -> current.distanceTo(action.rotation()).length())
                        .thenComparingDouble(action -> mc.player.getEyePosition().distanceToSqr(action.hit().getLocation())))
                .orElse(null);
    }

    private BreakAction findFireAction() {
        if (mc.player == null || mc.level == null) {
            return null;
        }

        List<BreakAction> actions = new ArrayList<>();
        for (BlockPos pos : searchAroundPlayer(SEARCH_RADIUS)) {
            if (!isFire(pos) || hasTargetInside(pos)) {
                continue;
            }

            BreakHit hit = findBreakHit(pos);
            if (hit != null) {
                actions.add(new BreakAction(pos, hit.hit(), hit.rotation()));
            }
        }

        if (actions.isEmpty()) {
            return null;
        }

        Rotation current = Core.INSTANCE.getRotationManager().getEffectiveRotation();
        return actions.stream()
                .min(Comparator
                        .comparingDouble((BreakAction action) -> current.distanceTo(action.rotation()).length())
                        .thenComparingDouble(action -> mc.player.getEyePosition().distanceToSqr(action.hit().getLocation())))
                .orElse(null);
    }

    private PlaceHit findPlaceHit(BlockPos placePos) {
        if (mc.player == null || mc.level == null) {
            return null;
        }

        List<PlaceHit> hits = new ArrayList<>();
        double range = getReach();
        for (Direction face : PLACE_DIRECTIONS) {
            BlockPos clickedPos = placePos.relative(face.getOpposite());
            if (!canClickBlock(clickedPos)) {
                continue;
            }

            for (Vec3 hitVec : sampleFace(clickedPos, face)) {
                if (mc.player.getEyePosition().distanceTo(hitVec) > range) {
                    continue;
                }

                Rotation rotation = RotationUtils.toRotation(hitVec).normalized();
                BlockHitResult raycast = RotationRaycast.rayCast(rotation, mc.player, mc.level, range, false);
                if (raycast.getType() != HitResult.Type.BLOCK
                        || !raycast.getBlockPos().equals(clickedPos)
                        || raycast.getDirection() != face) {
                    continue;
                }

                hits.add(new PlaceHit(new BlockHitResult(hitVec, face, clickedPos, false), rotation));
            }
        }

        if (hits.isEmpty()) {
            return null;
        }

        Rotation current = Core.INSTANCE.getRotationManager().getEffectiveRotation();
        return hits.stream()
                .min(Comparator.comparingDouble(hit -> current.distanceTo(hit.rotation()).length()))
                .orElse(null);
    }

    private BreakHit findBreakHit(BlockPos pos) {
        if (mc.player == null || mc.level == null) {
            return null;
        }

        List<BreakHit> hits = new ArrayList<>();
        double range = getReach();
        for (Vec3 hitVec : sampleBlockShape(pos)) {
            if (mc.player.getEyePosition().distanceTo(hitVec) > range) {
                continue;
            }

            Rotation rotation = RotationUtils.toRotation(hitVec).normalized();
            BlockHitResult raycast = RotationRaycast.rayCast(rotation, mc.player, mc.level, range, false);
            if (raycast.getType() == HitResult.Type.BLOCK && raycast.getBlockPos().equals(pos)) {
                hits.add(new BreakHit(raycast, rotation));
            }
        }

        if (hits.isEmpty()) {
            return null;
        }

        Rotation current = Core.INSTANCE.getRotationManager().getEffectiveRotation();
        return hits.stream()
                .min(Comparator.comparingDouble(hit -> current.distanceTo(hit.rotation()).length()))
                .orElse(null);
    }

    private BucketAction findWaterPickupAction() {
        if (mc.player == null || mc.level == null) {
            return null;
        }

        List<BucketAction> actions = new ArrayList<>();
        for (BlockPos pos : searchAroundPlayer((int) Math.ceil(getReach()))) {
            if (!isWaterSource(pos)) {
                continue;
            }

            for (Vec3 hitVec : sampleBlockShape(pos)) {
                if (mc.player.getEyePosition().distanceTo(hitVec) > getReach()) {
                    continue;
                }

                Rotation rotation = RotationUtils.toRotation(hitVec).normalized();
                BlockHitResult hit = RotationRaycast.rayCast(rotation, mc.player, mc.level, getReach(), true);
                if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos)) {
                    actions.add(new BucketAction(hit, rotation));
                }
            }
        }

        if (actions.isEmpty()) {
            return null;
        }

        Rotation current = Core.INSTANCE.getRotationManager().getEffectiveRotation();
        return actions.stream()
                .min(Comparator.comparingDouble(action -> current.distanceTo(action.rotation()).length()))
                .orElse(null);
    }

    private List<BlockPos> searchAroundPlayer(int radius) {
        List<BlockPos> positions = new ArrayList<>();
        if (mc.player == null) {
            return positions;
        }

        BlockPos playerPos = mc.player.blockPosition();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    positions.add(playerPos.offset(x, y, z));
                }
            }
        }

        positions.sort(Comparator.comparingDouble(pos -> Vec3.atCenterOf(pos).distanceToSqr(mc.player.getEyePosition())));
        return positions;
    }

    private boolean canClickBlock(BlockPos pos) {
        if (mc.level == null) {
            return false;
        }

        BlockState state = mc.level.getBlockState(pos);
        return !state.isAir() && !state.getShape(mc.level, pos).isEmpty();
    }

    private boolean hasEntityInside(BlockPos pos) {
        if (mc.level == null) {
            return false;
        }

        AABB box = new AABB(pos);
        for (Entity entity : mc.level.getEntities((Entity) null, box)) {
            if (entity.isAlive() && !entity.isSpectator() && entity.isPickable()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasTargetInside(BlockPos pos) {
        if (mc.level == null) {
            return false;
        }

        AABB box = new AABB(pos);
        for (Entity entity : mc.level.getEntities((Entity) null, box)) {
            if (Core.INSTANCE.getTargetManager().isTarget(entity)) {
                return true;
            }
        }
        return false;
    }

    private boolean notInStillLava() {
        if (mc.player == null || mc.level == null) {
            return false;
        }

        AABB box = mc.player.getBoundingBox();
        int minX = (int) Math.floor(box.minX);
        int maxX = (int) Math.floor(box.maxX);
        int minY = (int) Math.floor(box.minY);
        int maxY = (int) Math.floor(box.maxY);
        int minZ = (int) Math.floor(box.minZ);
        int maxZ = (int) Math.floor(box.maxZ);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (isStillLava(new BlockPos(x, y, z))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private boolean isStillLava(BlockPos pos) {
        if (mc.level == null) {
            return false;
        }

        FluidState fluid = mc.level.getFluidState(pos);
        return fluid.is(FluidTags.LAVA) && fluid.isSource();
    }

    private boolean isWaterSource(BlockPos pos) {
        return mc.level != null
                && mc.level.getFluidState(pos).is(Fluids.WATER)
                && mc.level.getFluidState(pos).isSource();
    }

    private boolean isFire(BlockPos pos) {
        if (mc.level == null) {
            return false;
        }

        BlockState state = mc.level.getBlockState(pos);
        return state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE);
    }

    private int findHotbarBlockSlot() {
        if (mc.player == null) {
            return -1;
        }

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem) {
                return i;
            }
        }
        return -1;
    }

    private List<Vec3> sampleBlockShape(BlockPos blockPos) {
        List<Vec3> points = new ArrayList<>();
        if (mc.level == null) {
            return points;
        }

        VoxelShape shape = mc.level.getBlockState(blockPos).getShape(mc.level, blockPos);
        if (shape.isEmpty()) {
            points.add(Vec3.atCenterOf(blockPos));
            return points;
        }

        for (AABB localBox : shape.toAabbs()) {
            AABB box = localBox.move(blockPos);
            points.add(box.getCenter());
            addBoxFaceSamples(points, box);
        }

        Vec3 eyes = mc.player != null ? mc.player.getEyePosition() : Vec3.ZERO;
        points.sort(Comparator.comparingDouble(eyes::distanceToSqr));
        return points;
    }

    private void addBoxFaceSamples(List<Vec3> points, AABB box) {
        double minX = box.minX + 1.0E-4D;
        double maxX = box.maxX - 1.0E-4D;
        double minY = box.minY + 1.0E-4D;
        double maxY = box.maxY - 1.0E-4D;
        double minZ = box.minZ + 1.0E-4D;
        double maxZ = box.maxZ - 1.0E-4D;

        for (double xOffset : SAMPLE_OFFSETS) {
            double x = lerp(minX, maxX, xOffset);
            for (double zOffset : SAMPLE_OFFSETS) {
                double z = lerp(minZ, maxZ, zOffset);
                points.add(new Vec3(x, minY, z));
                points.add(new Vec3(x, maxY, z));
            }
        }

        for (double yOffset : SAMPLE_OFFSETS) {
            double y = lerp(minY, maxY, yOffset);
            for (double xOffset : SAMPLE_OFFSETS) {
                double x = lerp(minX, maxX, xOffset);
                points.add(new Vec3(x, y, minZ));
                points.add(new Vec3(x, y, maxZ));
            }
            for (double zOffset : SAMPLE_OFFSETS) {
                double z = lerp(minZ, maxZ, zOffset);
                points.add(new Vec3(minX, y, z));
                points.add(new Vec3(maxX, y, z));
            }
        }
    }

    private List<Vec3> sampleFace(BlockPos blockPos, Direction face) {
        List<Vec3> points = new ArrayList<>();
        double min = SAMPLE_OFFSETS[0];
        double max = SAMPLE_OFFSETS[SAMPLE_OFFSETS.length - 1];

        switch (face) {
            case UP, DOWN -> {
                double y = face == Direction.UP ? max : min;
                for (double x : SAMPLE_OFFSETS) {
                    for (double z : SAMPLE_OFFSETS) {
                        points.add(offset(blockPos, x, y, z));
                    }
                }
            }
            case NORTH, SOUTH -> {
                double z = face == Direction.SOUTH ? max : min;
                for (double x : SAMPLE_OFFSETS) {
                    for (double y : SAMPLE_OFFSETS) {
                        points.add(offset(blockPos, x, y, z));
                    }
                }
            }
            case EAST, WEST -> {
                double x = face == Direction.EAST ? max : min;
                for (double y : SAMPLE_OFFSETS) {
                    for (double z : SAMPLE_OFFSETS) {
                        points.add(offset(blockPos, x, y, z));
                    }
                }
            }
        }

        Vec3 eyes = mc.player != null ? mc.player.getEyePosition() : Vec3.ZERO;
        points.sort(Comparator.comparingDouble(eyes::distanceToSqr));
        return points;
    }

    public boolean isActing() {
        return isEnabled() && stage != Stage.IDLE;
    }

    private Vec3 offset(BlockPos blockPos, double x, double y, double z) {
        return new Vec3(blockPos.getX() + x, blockPos.getY() + y, blockPos.getZ() + z);
    }

    private InteractionResult useItem(Rotation rotation, BlockHitResult hit) {
        if (mc.player == null || mc.gameMode == null) {
            return InteractionResult.FAIL;
        }

        mc.hitResult = hit;
        float currentYaw = mc.player.getYRot();
        float currentPitch = mc.player.getXRot();
        mc.player.setYRot(rotation.yaw());
        mc.player.setXRot(rotation.pitch());
        InteractionResult result = mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        mc.player.setYRot(currentYaw);
        mc.player.setXRot(currentPitch);
        return result;
    }

    private InteractionResult useItemOn(Rotation rotation, BlockHitResult hit) {
        if (mc.player == null || mc.gameMode == null) {
            return InteractionResult.FAIL;
        }

        mc.hitResult = hit;
        float currentYaw = mc.player.getYRot();
        float currentPitch = mc.player.getXRot();
        mc.player.setYRot(rotation.yaw());
        mc.player.setXRot(rotation.pitch());
        InteractionResult result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        mc.player.setYRot(currentYaw);
        mc.player.setXRot(currentPitch);
        return result;
    }

    private void applyRotation(Rotation rotation) {
        MovementFix moveFix = switch (movementFix.getValue()) {
            case "Strict" -> MovementFix.STRICT;
            case "Silent" -> MovementFix.SILENT;
            default -> MovementFix.NONE;
        };
        Core.INSTANCE.getRotationManager().setRotations(
                rotation,
                moveFix,
                false,
                SmoothMode.ADVANCED,
                rotationSpeed.getValue().floatValue(),
                0,
                0,
                Priority.MEDIUM
        );
    }

    private void swing() {
        if (mc.player == null) {
            return;
        }
        if (swingMode.is("Normal")) {
            mc.player.swing(InteractionHand.MAIN_HAND);
        } else if (swingMode.is("Silent") && mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
        }
    }

    private double getReach() {
        return Math.min(mc.player == null ? MAX_BLOCK_INTERACTION_RANGE : mc.player.blockInteractionRange(), MAX_BLOCK_INTERACTION_RANGE);
    }

    private double clamp(double value, double min, double max) {
        if (min > max) {
            return (min + max) * 0.5D;
        }
        return Math.max(min, Math.min(max, value));
    }

    private double lerp(double min, double max, double progress) {
        return min + (max - min) * progress;
    }

    private void restoreSlot(boolean clear) {
        if (oldSlot != -1 && mc.player != null) {
            mc.player.getInventory().selected = oldSlot;
        }
        if (clear) {
            oldSlot = -1;
        }
    }

    private void reset(boolean restoreSlot) {
        if (restoreSlot) {
            restoreSlot(true);
        } else {
            oldSlot = -1;
        }

        stage = Stage.IDLE;
        lavaAction = null;
        fireAction = null;
        waterAction = null;
        pickupAction = null;
    }

    private enum Stage {
        IDLE,
        WATER_PLACE,
        WATER_PICKUP,
        LAVA_SEAL,
        FIRE_CLEAR
    }

    private record SupportHit(BlockPos pos, BlockHitResult hit, Rotation rotation, AABB box) {
    }

    private record PlaceHit(BlockHitResult hit, Rotation rotation) {
    }

    private record BreakHit(BlockHitResult hit, Rotation rotation) {
    }

    private record BucketAction(BlockHitResult hit, Rotation rotation) {
    }

    private record PlaceAction(BlockPos pos, BlockHitResult hit, Rotation rotation) {
    }

    private record BreakAction(BlockPos pos, BlockHitResult hit, Rotation rotation) {
    }
}
