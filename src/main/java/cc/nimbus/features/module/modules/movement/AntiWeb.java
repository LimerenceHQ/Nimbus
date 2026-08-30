package cc.nimbus.features.module.modules.movement;

import cc.nimbus.core.Core;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.game.EventRotation;
import cc.nimbus.event.impl.game.EventTick;
import cc.nimbus.event.impl.game.RotationAppliedEvent;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.ModeSetting;
import cc.nimbus.features.module.api.setting.impl.NumberSetting;
import cc.nimbus.features.rotation.*;
import cc.nimbus.utils.entity.rotation.RotationUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


@ModuleInfo(name = "AntiWeb", category = ModuleCategory.Move, description = "Uses water to clear cobwebs trapping you")
public class AntiWeb extends BasicModule {
    private static final double MAX_BLOCK_INTERACTION_RANGE = 4.5D;
    private static final int MAX_CLEAR_WAIT_TICKS = 25;
    private static final int WATER_SOURCE_HORIZONTAL_SEARCH = 2;
    private static final int WATER_SOURCE_DOWN_SEARCH = 1;
    private static final int WATER_SOURCE_UP_SEARCH = 2;
    private static final int MAX_WATER_CANDIDATES_TO_RAYCAST = 40;
    private static final int MAX_FLOW_DISTANCE = 3;
    private static final int MAX_FLOW_NODES = 64;
    private static final Direction[] PLACE_DIRECTIONS = new Direction[]{
            Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN
    };
    private static final Direction[] HORIZONTAL_DIRECTIONS = new Direction[]{
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };
    private static final double[] SAMPLE_OFFSETS = new double[]{
            0.08D, 0.20D, 0.32D, 0.44D, 0.56D, 0.68D, 0.80D, 0.92D
    };

    private final NumberSetting delay = new NumberSetting("Delay", 4.0, 0.0, 20.0, 1.0);
    private final NumberSetting rotationSpeed = new NumberSetting("Rotation Speed", 180.0, 0.0, 180.0, 1.0);
    private final ModeSetting swingMode = new ModeSetting("Swing Mode", new String[]{"Off", "Normal", "Silent"}, "Normal");
    private final ModeSetting movementFix = new ModeSetting("Movement Fix", new String[]{"None", "Strict", "Silent"}, "Silent");

    private Stage stage = Stage.IDLE;
    private WaterAction waterAction;
    private PickupAction pickupAction;
    private BlockPos placedWaterPos;
    private int oldSlot = -1;
    private int cooldownTicks;
    private int waitClearTicks;

    @Override
    public void onEnable() {
        reset(true);
        cooldownTicks = 0;
    }

    @Override
    public void onDisable() {
        reset(true);
        cooldownTicks = 0;
    }

    @EventTarget
    public void onTick(EventTick event) {
        if (!event.isPre()) return;
        if (cooldownTicks > 0) {
            cooldownTicks--;
        }
        if (stage == Stage.WAIT_CLEAR) {
            waitClearTicks++;
        }
    }

    @EventTarget
    public void onRotation(EventRotation event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null || mc.screen != null) {
            reset(true);
            return;
        }

        if (stage == Stage.WAIT_CLEAR) {
            if (isPlayerInWeb() && waitClearTicks <= MAX_CLEAR_WAIT_TICKS) {
                return;
            }

            pickupAction = findPickupAction();
            if (pickupAction == null) {
                if (!hasKnownWaterSource()) {
                    reset(false);
                }
                return;
            }

            int bucketSlot = findHotbarSlot(Items.BUCKET);
            if (bucketSlot == -1) {
                reset(true);
                return;
            }

            mc.player.getInventory().selected = bucketSlot;
            stage = Stage.PICKUP;
            applyRotation(pickupAction.rotation());
            return;
        }

        if (stage == Stage.PICKUP && pickupAction != null) {
            applyRotation(pickupAction.rotation());
            return;
        }

        if (stage == Stage.PLACE && waterAction != null) {
            applyRotation(waterAction.rotation());
            return;
        }

        if (cooldownTicks > 0 || findHotbarSlot(Items.WATER_BUCKET) == -1) {
            return;
        }

        List<BlockPos> trappingWebs = getTrappingWebs();
        if (trappingWebs.isEmpty()) {
            return;
        }

        waterAction = findWaterAction(trappingWebs);
        if (waterAction == null) {
            return;
        }

        oldSlot = mc.player.getInventory().selected;
        mc.player.getInventory().selected = findHotbarSlot(Items.WATER_BUCKET);
        stage = Stage.PLACE;
        applyRotation(waterAction.rotation());
    }

    @EventTarget
    public void onRotationApplied(RotationAppliedEvent event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            reset(true);
            return;
        }

        if (stage == Stage.PLACE && waterAction != null) {
            InteractionResult result = useItem(waterAction.rotation(), waterAction.hit());
            boolean placed = result.consumesAction()
                    || mc.player.getMainHandItem().getItem() == Items.BUCKET
                    || isWaterSource(waterAction.waterPos());
            if (placed) {
                swing();
                placedWaterPos = waterAction.waterPos();
                waitClearTicks = 0;
                waterAction = null;
                restoreSlot(false);
                stage = Stage.WAIT_CLEAR;
            } else {
                reset(true);
            }
            return;
        }

        if (stage == Stage.PICKUP && pickupAction != null) {
            InteractionResult result = useItem(pickupAction.rotation(), pickupAction.hit());
            if (result.consumesAction() || mc.player.getMainHandItem().getItem() == Items.WATER_BUCKET) {
                swing();
            }
            reset(true);
            cooldownTicks = delay.getValue().intValue();
        }
    }

    public static int findHotbarSlot(net.minecraft.world.item.Item item) {
        if (mc.player == null) {
            return -1;
        }

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                return i;
            }
        }
        return -1;
    }

    private boolean isPlayerInWeb() {
        return !getTrappingWebs().isEmpty();
    }

    private List<BlockPos> getTrappingWebs() {
        List<BlockPos> webs = new ArrayList<>();
        if (mc.player == null || mc.level == null) {
            return webs;
        }

        AABB box = mc.player.getBoundingBox().inflate(1.0E-4D);
        int minX = (int) Math.floor(box.minX);
        int maxX = (int) Math.floor(box.maxX);
        int minY = (int) Math.floor(box.minY);
        int maxY = (int) Math.floor(box.maxY);
        int minZ = (int) Math.floor(box.minZ);
        int maxZ = (int) Math.floor(box.maxZ);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (mc.level.getBlockState(pos).getBlock() == Blocks.COBWEB
                            && new AABB(pos).intersects(box)) {
                        webs.add(pos.immutable());
                    }
                }
            }
        }

        Vec3 center = box.getCenter();
        webs.sort(Comparator.comparingDouble(pos -> Vec3.atCenterOf(pos).distanceToSqr(center)));
        return webs;
    }

    private WaterAction findWaterAction(List<BlockPos> trappingWebs) {
        if (mc.player == null || mc.level == null) {
            return null;
        }

        List<WaterAction> actions = new ArrayList<>();
        for (WaterCandidate candidate : getWaterCandidates(trappingWebs)) {
            BucketHit hit = findBucketHit(candidate.waterPos());
            if (hit == null) {
                continue;
            }
            actions.add(new WaterAction(candidate.waterPos(), hit.hit(), hit.rotation(), candidate.affectedWebs(), candidate.flowTicks()));
        }

        if (actions.isEmpty()) {
            return null;
        }

        Rotation current = Core.INSTANCE.getRotationManager().getEffectiveRotation();
        return actions.stream()
                .min(Comparator
                        .comparingInt(WaterAction::flowTicks)
                        .thenComparing(Comparator.comparingInt((WaterAction action) -> action.affectedWebs().size()).reversed())
                        .thenComparingDouble(action -> current.distanceTo(action.rotation()).length())
                        .thenComparingDouble(action -> mc.player.getEyePosition().distanceToSqr(action.hit().getLocation())))
                .orElse(null);
    }

    private List<WaterCandidate> getWaterCandidates(List<BlockPos> trappingWebs) {
        Set<BlockPos> seen = new HashSet<>();
        List<WaterCandidate> candidates = new ArrayList<>();

        for (BlockPos web : trappingWebs) {
            for (int x = -WATER_SOURCE_HORIZONTAL_SEARCH; x <= WATER_SOURCE_HORIZONTAL_SEARCH; x++) {
                for (int y = -WATER_SOURCE_DOWN_SEARCH; y <= WATER_SOURCE_UP_SEARCH; y++) {
                    for (int z = -WATER_SOURCE_HORIZONTAL_SEARCH; z <= WATER_SOURCE_HORIZONTAL_SEARCH; z++) {
                        addWaterCandidate(web.offset(x, y, z), trappingWebs, seen, candidates);
                    }
                }
            }
        }

        candidates.sort(Comparator
                .comparingInt(WaterCandidate::flowTicks)
                .thenComparing(Comparator.comparingInt((WaterCandidate candidate) -> candidate.affectedWebs().size()).reversed())
                .thenComparingDouble(candidate -> mc.player == null
                        ? 0.0D
                        : nearestWebDistance(candidate.waterPos(), trappingWebs))
                .thenComparingDouble(candidate -> mc.player == null
                        ? 0.0D
                        : Vec3.atCenterOf(candidate.waterPos()).distanceToSqr(mc.player.getEyePosition())));
        if (candidates.size() > MAX_WATER_CANDIDATES_TO_RAYCAST) {
            return new ArrayList<>(candidates.subList(0, MAX_WATER_CANDIDATES_TO_RAYCAST));
        }
        return candidates;
    }

    private void addWaterCandidate(BlockPos waterPos, List<BlockPos> trappingWebs, Set<BlockPos> seen, List<WaterCandidate> candidates) {
        if (mc.level == null || mc.player == null || !seen.add(waterPos) || !canPlaceWaterAt(waterPos) || !isRoughlyReachable(waterPos)) {
            return;
        }

        FlowResult result = predictAffectedWebs(waterPos, trappingWebs);

        if (!result.affectedWebs().isEmpty()) {
            candidates.add(new WaterCandidate(waterPos.immutable(), result.affectedWebs(), result.minFlowTicks()));
        }
    }

    private boolean isRoughlyReachable(BlockPos pos) {
        if (mc.player == null) {
            return false;
        }

        return mc.player.getEyePosition().distanceTo(Vec3.atCenterOf(pos)) <= getReach() + 1.75D;
    }

    private double nearestWebDistance(BlockPos waterPos, List<BlockPos> trappingWebs) {
        double best = Double.MAX_VALUE;
        Vec3 water = Vec3.atCenterOf(waterPos);

        for (BlockPos web : trappingWebs) {
            best = Math.min(best, water.distanceToSqr(Vec3.atCenterOf(web)));
        }

        return best;
    }

    private FlowResult predictAffectedWebs(BlockPos source, List<BlockPos> trappingWebs) {
        List<BlockPos> affected = new ArrayList<>();
        Set<BlockPos> targets = new HashSet<>(trappingWebs);
        Set<BlockPos> reachedTargets = new HashSet<>();
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<FlowNode> queue = new ArrayDeque<>();
        int minFlowTicks = Integer.MAX_VALUE;

        queue.addLast(new FlowNode(source, MAX_FLOW_DISTANCE, 0));
        visited.add(source);

        int nodes = 0;
        while (!queue.isEmpty() && nodes++ < MAX_FLOW_NODES && reachedTargets.size() < targets.size()) {
            FlowNode node = queue.removeFirst();
            BlockPos pos = node.pos();

            if (targets.contains(pos)) {
                reachedTargets.add(pos);
                minFlowTicks = Math.min(minFlowTicks, node.flowTicks());
            }

            BlockPos below = pos.below();
            if (canWaterFlowInto(below) && isRelevantFlowPos(below, source, trappingWebs) && visited.add(below)) {
                queue.addLast(new FlowNode(below, MAX_FLOW_DISTANCE, node.flowTicks() + 1));
            }

            if (node.remaining() <= 0) {
                continue;
            }

            for (Direction direction : HORIZONTAL_DIRECTIONS) {
                BlockPos next = pos.relative(direction);
                if (canWaterFlowInto(next) && isRelevantFlowPos(next, source, trappingWebs) && visited.add(next)) {
                    queue.addLast(new FlowNode(next, node.remaining() - 1, node.flowTicks() + 1));
                }
            }
        }

        for (BlockPos web : trappingWebs) {
            if (reachedTargets.contains(web)) {
                affected.add(web);
            }
        }

        return new FlowResult(affected, minFlowTicks == Integer.MAX_VALUE ? MAX_FLOW_DISTANCE + 1 : minFlowTicks);
    }

    private boolean canWaterFlowInto(BlockPos pos) {
        if (mc.level == null || pos.getY() < mc.level.dimensionType().minY()
                || pos.getY() >= mc.level.dimensionType().minY() + mc.level.dimensionType().height()) {
            return false;
        }

        BlockState state = mc.level.getBlockState(pos);
        if (state.getFluidState().is(Fluids.LAVA)) {
            return false;
        }

        return state.isAir()
                || state.canBeReplaced()
                || state.getFluidState().is(Fluids.WATER)
                || state.getBlock() == Blocks.COBWEB;
    }

    private boolean isRelevantFlowPos(BlockPos pos, BlockPos source, List<BlockPos> trappingWebs) {
        for (BlockPos web : trappingWebs) {
            if (Math.abs(pos.getX() - web.getX()) <= MAX_FLOW_DISTANCE + 1
                    && Math.abs(pos.getZ() - web.getZ()) <= MAX_FLOW_DISTANCE + 1
                    && pos.getY() >= Math.min(source.getY(), web.getY()) - 4
                    && pos.getY() <= Math.max(source.getY(), web.getY()) + 1) {
                return true;
            }
        }

        return false;
    }

    private boolean canPlaceWaterAt(BlockPos pos) {
        if (mc.level == null) {
            return false;
        }

        BlockState state = mc.level.getBlockState(pos);
        if (state.getFluidState().is(Fluids.WATER) || state.getFluidState().is(Fluids.LAVA)) {
            return false;
        }
        return state.isAir() || state.canBeReplaced() || state.getBlock() == Blocks.COBWEB;
    }

    private BucketHit findBucketHit(BlockPos waterPos) {
        BucketHit direct = findDirectBucketHit(waterPos);
        if (direct != null) {
            return direct;
        }

        List<BucketHit> hits = new ArrayList<>();
        for (Direction face : PLACE_DIRECTIONS) {
            BlockPos clickedPos = waterPos.relative(face.getOpposite());
            if (!canClickBlock(clickedPos)) {
                continue;
            }

            for (Vec3 hitVec : sampleFace(clickedPos, face)) {
                BucketHit hit = resolveBucketHit(hitVec, waterPos);
                if (hit != null) {
                    hits.add(hit);
                }
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

    private BucketHit findDirectBucketHit(BlockPos waterPos) {
        if (mc.level == null) {
            return null;
        }

        BlockState state = mc.level.getBlockState(waterPos);
        if (state.isAir()) {
            return null;
        }

        List<BucketHit> hits = new ArrayList<>();
        for (Vec3 hitVec : sampleBlock(waterPos)) {
            BucketHit hit = resolveBucketHit(hitVec, waterPos);
            if (hit != null) {
                hits.add(hit);
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

    private BucketHit resolveBucketHit(Vec3 hitVec, BlockPos expectedWaterPos) {
        if (mc.player == null || mc.level == null) {
            return null;
        }

        double range = getReach();
        if (mc.player.getEyePosition().distanceTo(hitVec) > range) {
            return null;
        }

        Rotation rotation = RotationUtils.toRotation(hitVec).normalized();
        BlockHitResult raycast = RotationRaycast.rayCast(rotation, mc.player, mc.level, range, false);
        if (raycast.getType() != HitResult.Type.BLOCK) {
            return null;
        }

        BlockPos placementPos = getBucketPlacementPos(raycast);
        if (!placementPos.equals(expectedWaterPos)) {
            return null;
        }

        return new BucketHit(raycast, rotation);
    }

    private BlockPos getBucketPlacementPos(BlockHitResult hit) {
        if (mc.level == null) {
            return hit.getBlockPos();
        }

        BlockPos hitPos = hit.getBlockPos();
        return canPlaceWaterAt(hitPos) ? hitPos : hitPos.relative(hit.getDirection());
    }

    private boolean canClickBlock(BlockPos pos) {
        if (mc.level == null) {
            return false;
        }

        BlockState state = mc.level.getBlockState(pos);
        return !state.isAir() && !state.getShape(mc.level, pos).isEmpty();
    }

    private List<Vec3> sampleBlock(BlockPos blockPos) {
        List<Vec3> points = new ArrayList<>();
        points.add(Vec3.atCenterOf(blockPos));
        for (Direction face : Direction.values()) {
            points.addAll(sampleFace(blockPos, face));
        }
        return points;
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

    private Vec3 offset(BlockPos blockPos, double x, double y, double z) {
        return new Vec3(blockPos.getX() + x, blockPos.getY() + y, blockPos.getZ() + z);
    }

    private PickupAction findPickupAction() {
        if (mc.player == null || mc.level == null || placedWaterPos == null) {
            return null;
        }

        BlockPos waterPos = isWaterSource(placedWaterPos) ? placedWaterPos : findNearbyWaterSource(placedWaterPos);
        if (waterPos == null) {
            return null;
        }

        return findWaterPickupHit(waterPos);
    }

    private PickupAction findWaterPickupHit(BlockPos waterPos) {
        if (mc.player == null || mc.level == null) {
            return null;
        }

        List<PickupAction> actions = new ArrayList<>();
        List<PickupAction> fallbackActions = new ArrayList<>();
        for (Vec3 hitVec : sampleBlock(waterPos)) {
            if (mc.player.getEyePosition().distanceTo(hitVec) > getReach()) {
                continue;
            }

            Rotation rotation = RotationUtils.toRotation(hitVec).normalized();
            BlockHitResult hit = RotationRaycast.rayCast(rotation, mc.player, mc.level, getReach(), true);
            if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(waterPos)) {
                actions.add(new PickupAction(hit, rotation));
            } else {
                fallbackActions.add(new PickupAction(new BlockHitResult(hitVec, Direction.UP, waterPos, false), rotation));
            }
        }

        Rotation current = Core.INSTANCE.getRotationManager().getEffectiveRotation();
        List<PickupAction> candidates = actions.isEmpty() ? fallbackActions : actions;
        if (candidates.isEmpty()) {
            return null;
        }

        return candidates.stream()
                .min(Comparator.comparingDouble(action -> current.distanceTo(action.rotation()).length()))
                .orElse(null);
    }

    private BlockPos findNearbyWaterSource(BlockPos origin) {
        if (mc.level == null) {
            return null;
        }

        List<BlockPos> possible = new ArrayList<>();
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    if (isWaterSource(pos)) {
                        possible.add(pos.immutable());
                    }
                }
            }
        }

        if (possible.isEmpty()) {
            return null;
        }

        possible.sort(Comparator.comparingDouble(pos -> Vec3.atCenterOf(pos).distanceToSqr(Vec3.atCenterOf(origin))));
        return possible.get(0);
    }

    private boolean hasKnownWaterSource() {
        return placedWaterPos != null && (isWaterSource(placedWaterPos) || findNearbyWaterSource(placedWaterPos) != null);
    }

    private boolean isWaterSource(BlockPos pos) {
        return mc.level != null
                && mc.level.getFluidState(pos).is(Fluids.WATER)
                && mc.level.getFluidState(pos).isSource();
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
        waterAction = null;
        pickupAction = null;
        placedWaterPos = null;
        waitClearTicks = 0;
    }

    private enum Stage {
        IDLE,
        PLACE,
        WAIT_CLEAR,
        PICKUP
    }

    private record WaterCandidate(BlockPos waterPos, List<BlockPos> affectedWebs, int flowTicks) {
    }

    private record FlowResult(List<BlockPos> affectedWebs, int minFlowTicks) {
    }

    private record FlowNode(BlockPos pos, int remaining, int flowTicks) {
    }

    private record BucketHit(BlockHitResult hit, Rotation rotation) {
    }

    private record WaterAction(BlockPos waterPos, BlockHitResult hit, Rotation rotation, List<BlockPos> affectedWebs, int flowTicks) {
    }

    private record PickupAction(BlockHitResult hit, Rotation rotation) {
    }
}
