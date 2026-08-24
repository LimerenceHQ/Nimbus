package cc.nimbus.utils.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashMap;
import java.util.Map;

public class BlockUtils implements IMinecraft {
    public static AABB getBoundingBox(BlockPos pos) {
        return getOutlineShape(pos).bounds().move(pos);
    }

    private static VoxelShape getOutlineShape(BlockPos pos) {
        return getState(pos).getShape(mc.level, pos);
    }

    public static boolean canPlace(BlockPos blockPos, boolean checkEntities) {
        return canPlaceBlock(blockPos, checkEntities, Blocks.OBSIDIAN);
    }

    public static Map<BlockPos, Block> searchBlocks(int radius) {
        Map<BlockPos, Block> blocks = new HashMap<>();

        Level world = mc.level;
        Vec3 playerPos = mc.player.position();
        BlockPos playerBlockPos = mc.player.getOnPos();

        int r = radius;

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = playerBlockPos.offset(x, y, z);

                    double distance = playerPos.distanceTo(Vec3.atCenterOf(pos));

                    if (distance <= radius) {
                        Block block = world.getBlockState(pos).getBlock();
                        blocks.put(pos, block);
                    }
                }
            }
        }

        return blocks;
    }

    public static boolean canPlace(BlockPos blockPos) {
        return canPlace(blockPos, true);
    }

    public static boolean canPlaceBlock(BlockPos blockPos, boolean checkEntities, Block block) {
        if (blockPos == null || mc.level == null) {
            return false;
        }

        Level world = mc.level;
        int y = blockPos.getY();
        if (y < world.getMinBuildHeight() || y >= world.getMaxBuildHeight()) {
            return false;
        }

        BlockState current = world.getBlockState(blockPos);
        if (!current.canBeReplaced()) {
            return false;
        }

        if (!checkEntities) {
            return true;
        }

        BlockState toPlace = block.defaultBlockState();
        return world.isUnobstructed(toPlace, blockPos, net.minecraft.world.phys.shapes.CollisionContext.empty());
    }

    public static BlockState getState(BlockPos pos) {
        return mc.level.getBlockState(pos);
    }

    public static Block getBlock(BlockPos pos) {
        return mc.level.getBlockState(pos).getBlock();
    }

    public static boolean canBeClicked(BlockPos pos) {
        return getOutlineShape(pos) != Shapes.empty();
    }

    public static boolean isAirBlock(BlockPos blockPos) {
        if (mc.level == null || mc.player == null) {
            return false;
        }

        Block block = mc.level.getBlockState(blockPos).getBlock();
        return block instanceof AirBlock;
    }
}
