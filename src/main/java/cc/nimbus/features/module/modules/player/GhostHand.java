package cc.nimbus.features.module.modules.player;

import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.player.EventItemUse;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.rotation.RotationRaycast;
import cc.nimbus.managers.GameStateManager;
import cc.nimbus.mixin.accessor.client.DeltaTrackerTimerAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(name = "GhostHand", category = ModuleCategory.Player, description = "Use containers through blocks")
public class GhostHand extends BasicModule {
    private static final double MAX_REACH_DISTANCE = 4.5;

    @EventTarget
    public void onItemUse(EventItemUse event) {
        if (mc.getConnection() == null) {
            return;
        }

        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult hitResult = (BlockHitResult) mc.hitResult;
            if (isChestBlock(mc.level.getBlockState(hitResult.getBlockPos()))) {
                return;
            }
        }

        BlockHitResult hitResult = raycastForChest();
        if (hitResult != null) {
            GameStateManager.sendSequencedPacket(id -> new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, hitResult, id));
            event.cancelEvent();
        }
    }

    public static BlockHitResult raycastForChest() {
        float tickDelta = ((DeltaTrackerTimerAccessor)mc.getDeltaTracker()).nimbus$getDeltaTickResidual();
        Vec3 start = mc.player.getEyePosition(tickDelta);
        Vec3 direction = mc.player.getViewVector(tickDelta);

        double stepSize = 0.1;
        int steps = (int) (MAX_REACH_DISTANCE / stepSize);
        Vec3 step = direction.scale(stepSize);
        Vec3 current = start;

        for (int i = 0; i <= steps; i++) {
            BlockPos pos = BlockPos.containing(current);
            BlockState state = mc.level.getBlockState(pos);

            if (isChestBlock(state) && hasChestCollision(pos, current)) {
                BlockHitResult clipped = RotationRaycast.getFacedContainerBlock(mc.player.getYRot(), mc.player.getXRot());
                if (clipped != null && clipped.getType() != HitResult.Type.MISS) {
                    return new BlockHitResult(clipped.getLocation(), clipped.getDirection(), pos, false);
                }
                return new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
            }

            current = current.add(step);
        }

        return null;
    }

    private static boolean isChestBlock(BlockState state) {
        return state.getBlock() instanceof ChestBlock
                || state.getBlock() instanceof EnderChestBlock
                || state.getBlock() instanceof ShulkerBoxBlock;
    }

    private static boolean hasChestCollision(BlockPos pos, Vec3 rayPos) {
        try {
            double minX = pos.getX() + 0.1;
            double minY = pos.getY() + 0.1;
            double minZ = pos.getZ() + 0.1;
            double maxX = pos.getX() + 0.9;
            double maxY = pos.getY() + 0.9;
            double maxZ = pos.getZ() + 0.9;

            return rayPos.x >= minX && rayPos.x <= maxX
                    && rayPos.y >= minY && rayPos.y <= maxY
                    && rayPos.z >= minZ && rayPos.z <= maxZ;
        } catch (Exception e) {
            return false;
        }
    }
}
