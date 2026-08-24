package cc.nimbus.features.module.modules.movement;

import cc.nimbus.core.Core;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.game.EventTickUpdate;
import cc.nimbus.event.impl.player.EventMoveInput;
import cc.nimbus.event.impl.player.EventSlowMovement;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.submodule.ModeValue;
import cc.nimbus.features.module.modules.combat.Velocity;
import cc.nimbus.features.module.modules.combat.velocity.Legit;
import cc.nimbus.managers.GameStateManager;
import cc.nimbus.utils.entity.player.MovementUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

@ModuleInfo(name = "FastWeb", category = ModuleCategory.Move,description = "Reduce the slowdown you take in cobwebs")
public class FastWeb extends BasicModule {
    private final ModeValue antiCheat = new ModeValue("AntiCheat",new String[]{"Vanilla","Grim"},"Vanilla");

    private boolean jumping = false;
    private boolean sneaking = false;
    public boolean isWorking = false;

    @EventTarget
    public void onUpdate(EventTickUpdate event) {
        if (mc.player == null || mc.level == null) return;

        if (isInWeb()) {
            float baseSpeed = 0.6f;
            if (GameStateManager.onGroundTicks >= 2) {
                MovementUtils.strafe(0.5f);
            } else {
                MovementUtils.strafe(0.35f);
            }

            // 向上
            if (mc.options.keyJump.isDown()) {
                mc.player.setDeltaMovement(mc.player.getDeltaMovement().x, baseSpeed * 2f, mc.player.getDeltaMovement().z);
                return;
            }

            // 向下
            if (mc.options.keyShift.isDown()) {
                mc.player.setDeltaMovement(mc.player.getDeltaMovement().x, -baseSpeed * 1.9, mc.player.getDeltaMovement().z);
            }
        }
    }
    public boolean isInWeb() {
        if (mc.player == null || mc.level == null) return false;

        AABB pBox = mc.player.getBoundingBox();
        BlockPos pBlockPos = BlockPos.containing(mc.player.position());

        for (int x = pBlockPos.getX() - 2; x <= pBlockPos.getX() + 2; x++) {
            for (int y = pBlockPos.getY() - 1; y <= pBlockPos.getY() + 4; y++) {
                for (int z = pBlockPos.getZ() - 2; z <= pBlockPos.getZ() + 2; z++) {
                    BlockPos bp = new BlockPos(x, y, z);
                    if (pBox.intersects(new AABB(bp)) && mc.level.getBlockState(bp).getBlock() == Blocks.COBWEB)
                        return true;
                }
            }
        }

        return false;
    }
}