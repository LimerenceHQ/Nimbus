package cc.nimbus.features.module.modules.movement;

import cc.nimbus.core.Core;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.player.EventMoveInput;
import cc.nimbus.event.impl.player.EventSlowMovement;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.ModeSetting;
import cc.nimbus.features.module.modules.combat.Velocity;
import cc.nimbus.utils.entity.player.MovementUtils;
import net.minecraft.world.level.block.Blocks;

@ModuleInfo(name = "FastWeb", category = ModuleCategory.Move,description = "Reduce the slowdown you take in cobwebs")
public class FastWeb extends BasicModule {
    private final ModeSetting antiCheat = new ModeSetting("AntiCheat",new String[]{"Vanilla","Grim"},"Vanilla");

    private boolean jumping = false;
    private boolean sneaking = false;
    public boolean isWorking = false;

    @EventTarget
    public void onSlowMovement(EventSlowMovement event) {
        if (mc.player == null) return;
        if (event.getState().getBlock() == Blocks.COBWEB) {
            Velocity legit = Core.INSTANCE.getModuleManager().getModule(Velocity.class);
            legit.handleVelocity = false;
            switch (antiCheat.getValue()) {
                case "Vanilla":
                    event.cancelEvent();
                    break;
                case "Grim":
                    if (MovementUtils.isMoving()) {
                        if (!jumping && !sneaking || jumping && sneaking) {
                            MovementUtils.strafe(0.64);
                        } else {
                            MovementUtils.strafe(0.61);
                        }
                    }
                    if (jumping && sneaking) {
                        return;
                    }
                    if (jumping) {
                        mc.player.setDeltaMovement(mc.player.getDeltaMovement().x, 1.4F, mc.player.getDeltaMovement().z);
                    }

                    if (sneaking) {
                        mc.player.setDeltaMovement(mc.player.getDeltaMovement().x, -3.77F, mc.player.getDeltaMovement().z);
                    }
                    break;
            }
        }
    }
    @EventTarget
    public void onMoveInput(EventMoveInput event) {
        jumping = event.getJump();
        sneaking = event.getSneak();
    }
}
