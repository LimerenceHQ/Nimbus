package cc.nimbus.features.module.modules.movement.scaffold;

import cc.nimbus.utils.client.BlockUtils;
import cc.nimbus.utils.client.IMinecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;

public class PlaceUtils implements IMinecraft {
    private static final Direction[] DIRECTIONS = new Direction[]{
            Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH, Direction.UP
    };

    public enum SearchMode {
        Normal,
        Hypixel,
        Rondos
    }

    @Nullable
    public static PlaceInfo getPlaceInfo(BlockPos origin, SearchMode mode) {
        if (mc.level == null || origin == null) {
            return null;
        }

        return switch (mode) {
            case Normal -> searchNormal(origin);
            case Hypixel -> searchHypixel(origin);
            case Rondos -> searchRondos(origin);
        };
    }

    @Nullable
    private static PlaceInfo searchRondos(BlockPos origin) {
        if (mc.player == null || mc.level == null) {
            return null;
        }

        int yLevel = origin.getY();
        Vec3 baseVec = mc.player.position();
        BlockPos playerPos = mc.player.blockPosition();
        BlockPos base = playerPos.atY(yLevel);
        int baseX = base.getX();
        int baseZ = base.getZ();

        if (mc.level.getBlockState(base).isFaceSturdy(mc.level, base, Direction.UP, SupportType.FULL)) {
            return null;
        }

        PlaceInfo result = checkRondosBlock(baseVec, base);
        if (result != null) {
            return result;
        }

        for (int depth = 1; depth <= 4; depth++) {
            result = checkRondosBlock(baseVec, new BlockPos(baseX, yLevel - depth, baseZ));
            if (result != null) {
                return result;
            }

            for (int x = 1; x <= depth; x++) {
                for (int z = 0; z <= depth - x; z++) {
                    int y = depth - x - z;
                    for (int revX = 0; revX <= 1; revX++) {
                        for (int revZ = 0; revZ <= 1; revZ++) {
                            result = checkRondosBlock(baseVec, new BlockPos(
                                    baseX + (revX == 0 ? x : -x),
                                    yLevel - y,
                                    baseZ + (revZ == 0 ? z : -z)
                            ));
                            if (result != null) {
                                return result;
                            }
                        }
                    }
                }
            }
        }

        return null;
    }

    @Nullable
    private static PlaceInfo checkRondosBlock(Vec3 baseVec, BlockPos pos) {
        if (mc.level == null || mc.player == null) {
            return null;
        }

        BlockState state = mc.level.getBlockState(pos);
        Block block = state.getBlock();
        FluidState fluidState = state.getFluidState();

        if (!(block instanceof AirBlock)
                && !(block instanceof SnowLayerBlock)
                && !(block instanceof VineBlock)
                && !fluidState.is(Fluids.WATER)
                && !fluidState.is(Fluids.LAVA)
                && !(block instanceof FireBlock)) {
            return null;
        }

        Vec3 center = new Vec3(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);

        for (Direction face : Direction.values()) {
            Vec3 faceVector = face.getUnitVec3();
            Vec3 hit = center.add(faceVector.scale(0.5D));
        BlockPos offsetPos = pos.relative(face);

            if (BlockUtils.canPlace(offsetPos) || mc.level.getBlockState(offsetPos).isAir()) {
                continue;
            }

            Vec3 relevant = hit.subtract(baseVec);
            if (hit.distanceTo(mc.player.getEyePosition()) <= 4.5D && relevant.dot(faceVector) >= 0.0D) {
                return new PlaceInfo(offsetPos.immutable(), face.getOpposite());
            }
        }

        return null;
    }

    @Nullable
    private static PlaceInfo searchNormal(BlockPos blockPos) {
        if (mc.level == null || blockPos == null) {
            return null;
        }

        ArrayDeque<BlockInfo> blockInfos = new ArrayDeque<>();

        for (Direction face : DIRECTIONS) {
            BlockPos neighbor = blockPos.relative(face.getOpposite());
            if (isAttachable(neighbor)) {
                return new PlaceInfo(neighbor, face);
            }
            blockInfos.addLast(new BlockInfo(neighbor, 1));
        }

        while (!blockInfos.isEmpty()) {
            BlockInfo current = blockInfos.removeFirst();
            if (current.depth() >= 4) {
                continue;
            }

            for (Direction nextFace : DIRECTIONS) {
                BlockPos next = current.pos().relative(nextFace.getOpposite());
                if (isAttachable(next)) {
                    return new PlaceInfo(next, nextFace);
                }
                blockInfos.addLast(new BlockInfo(next, current.depth() + 1));
            }
        }

        for (int i = 1; i <= 2; i++) {
            BlockPos below = blockPos.below(i);
            if (isAttachable(below)) {
                return new PlaceInfo(below, Direction.UP);
            }
        }

        return null;
    }

    @Nullable
    private static PlaceInfo searchHypixel(BlockPos original) {
        if (mc.player == null || mc.level == null) {
            return null;
        }

        ArrayList<BlockPos> candidates = new ArrayList<>();

        for (int x = -4; x <= 4; x++) {
            for (int y = 0; y >= -1; y--) {
                for (int z = -4; z <= 4; z++) {
                    BlockPos base = original.offset(x, y, z);
                    if (!canPlaceAt(base) && !isInteractable(base) && withinReachSquared(base)) {
                        for (Direction direction : DIRECTIONS) {
                            BlockPos neighbor = base.relative(direction);
                            if (canPlaceAt(neighbor)) {
                                candidates.add(base);
                                break;
                            }
                        }
                    }
                }
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }

        candidates.sort(Comparator.comparingDouble(pos -> center(pos).distanceToSqr(center(original))));
        for (BlockPos candidate : candidates) {
            Direction direction = getBestDirection(candidate, original);
            if (direction != null) {
                BlockPos neighbor = candidate.relative(direction);
                return new PlaceInfo(neighbor, direction.getOpposite());
            }
        }

        return null;
    }

    private static Direction getBestDirection(BlockPos target, BlockPos original) {
        Direction bestDirection = null;
        double minDistance = 0.0D;
        for (Direction direction : DIRECTIONS) {
            BlockPos neighbor = target.relative(direction);
            double distance = center(neighbor).distanceToSqr(center(original));
            if (bestDirection == null || distance < minDistance || (distance == minDistance && direction == Direction.UP)) {
                bestDirection = direction;
                minDistance = distance;
            }
        }
        return bestDirection;
    }

    public static boolean canPlaceAt(BlockPos pos) {
        if (mc.player == null || mc.level == null) {
            return false;
        }

        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir()) {
            return true;
        }

        if (state.getBlock() instanceof SnowLayerBlock) {
            Integer layers = state.getValue(SnowLayerBlock.LAYERS);
            if (layers != null && layers <= 1) {
                return true;
            }
        }

        return state.canBeReplaced();
    }

    private static boolean isInteractable(BlockPos blockPos) {
        Block block = mc.level.getBlockState(blockPos).getBlock();
        if (block instanceof EntityBlock) return true;
        if (block instanceof CraftingTableBlock) return true;
        if (block instanceof AnvilBlock) return true;
        if (block instanceof BedBlock) return true;
        if (block instanceof DoorBlock) return true;
        if (block instanceof TrapDoorBlock) return true;
        if (block instanceof FenceGateBlock) return true;
        if (block instanceof FenceBlock) return true;
        if (block instanceof ButtonBlock) return true;
        if (block instanceof LeverBlock) return true;
        return block instanceof JukeboxBlock;
    }

    private static boolean isAttachable(BlockPos pos) {
        if (mc.level == null || pos == null) {
            return false;
        }

        BlockState state = mc.level.getBlockState(pos);
        return !state.isAir() && !state.getCollisionShape(mc.level, pos).isEmpty();
    }

    private static boolean withinReachSquared(BlockPos pos) {
        return mc.player != null && mc.player.distanceToSqr(center(pos)) <= 20.25D;
    }

    private static Vec3 center(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
    }

    private record BlockInfo(BlockPos pos, int depth) {
    }
}
