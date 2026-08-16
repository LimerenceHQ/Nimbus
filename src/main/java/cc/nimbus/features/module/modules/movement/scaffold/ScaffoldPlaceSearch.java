package cc.nimbus.features.module.modules.movement.scaffold;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ScaffoldPlaceSearch {
    private static final Direction[] DIRECTIONS = {
            Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH, Direction.UP
    };

    private ScaffoldPlaceSearch() {
    }

    public static ScaffoldPlaceInfo find(BlockPos origin, SearchMode mode) {
        if (origin == null || Minecraft.getInstance().level == null) return null;
        return mode == SearchMode.HYPIXEL ? searchHypixel(origin) : searchNormal(origin);
    }

    public static boolean canPlaceAt(BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || pos == null) return false;
        BlockState state = minecraft.level.getBlockState(pos);
        if (state.isAir()) return true;
        if (state.getBlock() instanceof SnowLayerBlock && state.getValue(SnowLayerBlock.LAYERS) <= 1) return true;
        return state.canBeReplaced();
    }

    private static ScaffoldPlaceInfo searchNormal(BlockPos origin) {
        ArrayDeque<BlockDepth> queue = new ArrayDeque<>();
        for (Direction face : DIRECTIONS) {
            BlockPos neighbor = origin.relative(face.getOpposite());
            if (isAttachable(neighbor)) return new ScaffoldPlaceInfo(neighbor.immutable(), face);
            queue.addLast(new BlockDepth(neighbor, 1));
        }

        while (!queue.isEmpty()) {
            BlockDepth current = queue.removeFirst();
            if (current.depth >= 4) continue;
            for (Direction face : DIRECTIONS) {
                BlockPos next = current.pos.relative(face.getOpposite());
                if (isAttachable(next)) return new ScaffoldPlaceInfo(next.immutable(), face);
                queue.addLast(new BlockDepth(next, current.depth + 1));
            }
        }

        for (int depth = 1; depth <= 2; depth++) {
            BlockPos below = origin.below(depth);
            if (isAttachable(below)) return new ScaffoldPlaceInfo(below.immutable(), Direction.UP);
        }
        return null;
    }

    private static ScaffoldPlaceInfo searchHypixel(BlockPos origin) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return null;
        List<BlockPos> candidates = new ArrayList<>();

        for (int x = -4; x <= 4; x++) {
            for (int y = 0; y >= -1; y--) {
                for (int z = -4; z <= 4; z++) {
                    BlockPos base = origin.offset(x, y, z);
                    if (canPlaceAt(base) || isInteractable(base) || !withinReach(base)) continue;
                    for (Direction direction : DIRECTIONS) {
                        if (canPlaceAt(base.relative(direction))) {
                            candidates.add(base.immutable());
                            break;
                        }
                    }
                }
            }
        }

        candidates.sort(Comparator.comparingDouble(pos -> center(pos).distanceToSqr(center(origin))));
        for (BlockPos base : candidates) {
            Direction direction = bestPlaceableDirection(base, origin);
            if (direction != null) {
                return new ScaffoldPlaceInfo(base.immutable(), direction);
            }
        }
        return null;
    }

    private static Direction bestPlaceableDirection(BlockPos base, BlockPos origin) {
        Direction best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (Direction direction : DIRECTIONS) {
            BlockPos target = base.relative(direction);
            if (!canPlaceAt(target)) continue;
            double distance = center(target).distanceToSqr(center(origin));
            if (distance < bestDistance || distance == bestDistance && direction == Direction.UP) {
                best = direction;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static boolean isAttachable(BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || pos == null) return false;
        BlockState state = minecraft.level.getBlockState(pos);
        return !state.isAir() && !state.getCollisionShape(minecraft.level, pos).isEmpty();
    }

    private static boolean isInteractable(BlockPos pos) {
        var block = Minecraft.getInstance().level.getBlockState(pos).getBlock();
        return block instanceof EntityBlock
                || block instanceof CraftingTableBlock
                || block instanceof AnvilBlock
                || block instanceof BedBlock
                || block instanceof DoorBlock
                || block instanceof TrapDoorBlock
                || block instanceof FenceGateBlock
                || block instanceof FenceBlock
                || block instanceof ButtonBlock
                || block instanceof LeverBlock
                || block instanceof JukeboxBlock;
    }

    private static boolean withinReach(BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && minecraft.player.distanceToSqr(center(pos)) <= 20.25;
    }

    private static Vec3 center(BlockPos pos) {
        return Vec3.atCenterOf(pos);
    }

    public enum SearchMode {
        NORMAL,
        HYPIXEL
    }

    private record BlockDepth(BlockPos pos, int depth) {
    }
}
