package cc.nimbus.features.module.modules.movement;


import cc.nimbus.core.Core;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.game.EventTick;
import cc.nimbus.event.impl.player.EventMoveInput;
import cc.nimbus.event.impl.player.EventPreUpdate;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.BooleanSetting;
import cc.nimbus.features.module.api.setting.impl.submodule.ModeValue;
import cc.nimbus.features.module.modules.combat.KillAura;
import cc.nimbus.features.module.modules.combat.Velocity;
import cc.nimbus.features.module.modules.combat.velocitys.Legit;
import cc.nimbus.features.rotation.*;
import cc.nimbus.managers.GameStateManager;
import cc.nimbus.utils.entity.player.MovementUtils;
import net.minecraft.util.Mth;

@ModuleInfo(name = "Speed",description = "Increase your speed when you are moving",category = ModuleCategory.Move)
public class Speed extends BasicModule {
    private static final float[] RUN = new float[]{0.89f, 0.9f, 0.9f, 0.9f, 0.86f, 0.92f, 0.92f, 1.5f, 1.53f, 1.5f};
    private static final float[] SCA = new float[]{0.85f, 0.85f, 0.84f, 0.84f, 0.83f, 1.0f, 1.0f, 1.62f, 1.64f, 1.64f};

    private final ModeValue mode = new ModeValue("Mode", new String[]{"Run", "Sca"}, "Sca");
    private final BooleanSetting onlyWhenSpace = new BooleanSetting("OnlyWhenSpace", true);
    private final BooleanSetting handleVelocityCheck = new BooleanSetting("HandleVelocity", true);

    private int airTickCounter = 0;

    public void onDisable(){
        airTickCounter = 0;
        GameStateManager.timerSpeed = 1F;
    }

    @EventTarget
    public void onKeyInput(EventMoveInput event){
        if ((mc.options.keyJump.isDown() || !onlyWhenSpace.getValue()) && MovementUtils.isMoving())
            event.setJump(true);
    }

    @EventTarget
    public void onTick(EventTick event){
        if (!event.isPre()) return;
        RotationPriority priority = RotationPriority.VERY_LOW;
        boolean active = ((mc.options.keyJump.isDown() || !onlyWhenSpace.getValue()) && MovementUtils.isMoving()) && !Core.INSTANCE.getModuleManager().getModule(Scaffold.class).isEnabled();

        if (Core.INSTANCE.getModuleManager().getModule(KillAura.class).attackTarget != null){
            priority = mc.player.onGround() ? RotationPriority.VERY_HIGH : RotationPriority.VERY_LOW;
        }

        if (active) {
            Rotation rotation = new Rotation(getPlayerYaw() + (mc.player.onGround() ? 0 : -45), Core.INSTANCE.getRotationManager().getServerRotation().pitch()).normalized();
            Core.INSTANCE.getRotationManager().submit(this, new RotationRequest(
                    rotation,
                    priority,
                    MovementFix.SILENT,
                    false,
                    SmoothMode.ADVANCED,
                    180F,
                    0,
                    2
            ));
        }
    }

    @EventTarget
    public void onUpdate(EventPreUpdate event){
        Velocity antiKb = Core.INSTANCE.getModuleManager().getModule(Velocity.class);
        boolean active = (!onlyWhenSpace.getValue() || mc.options.keyJump.isDown()) && MovementUtils.isMoving();
        boolean handleVelocity = false;
        if (this.handleVelocityCheck.getValue() && antiKb.isEnabled()) {
            Legit legit = antiKb.getSubModule(Legit.class);
            handleVelocity = legit.handleVelocity;
        }
        if (!active || handleVelocity) {
            GameStateManager.timerSpeed = 1F;
            this.airTickCounter = 0;
            return;
        }

        float[] currentTimers = mode.is("Run") ? RUN : SCA;
        if (mc.player.onGround()) {
            GameStateManager.timerSpeed = currentTimers[0];
            airTickCounter = 0;
        } else {
            airTickCounter++;
            if (airTickCounter >= 1 && airTickCounter <= 9) {
                GameStateManager.timerSpeed = currentTimers[airTickCounter];
            } else {
                GameStateManager.timerSpeed = 1;
            }
        }
    }

    private float getPlayerYaw() {
        int forward = multiplier(mc.options.keyUp) - multiplier(mc.options.keyDown);
        int strafe = multiplier(mc.options.keyLeft) - multiplier(mc.options.keyRight);
        if (forward == 0 && strafe == 0) {
            return mc.player.getYRot();
        }
        float yaw = (float) Math.toDegrees(Math.atan2(strafe, forward));
        return Mth.wrapDegrees(mc.player.getYRot() + yaw);
    }

    private int multiplier(net.minecraft.client.KeyMapping key) {
        return key.isDown() ? 1 : 0;
    }
}
