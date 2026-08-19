package cc.nimbus.features.module.modules.player;

import cc.nimbus.core.Core;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.game.EventWorldLoad;
import cc.nimbus.event.impl.player.EventUpdate;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.ModeSetting;
import cc.nimbus.features.module.api.setting.impl.NumberSetting;
import cc.nimbus.features.module.modules.combat.KillAura;
import cc.nimbus.managers.GameStateManager;
import cc.nimbus.utils.entity.player.MovementUtils;
import org.lwjgl.glfw.GLFW;

@ModuleInfo(name = "Timer", description = "Speeds up your game ticks.", category = ModuleCategory.Player)
public class Timer extends BasicModule {
    private final ModeSetting mode = new ModeSetting("Mode", new String[]{"Always", "SlowBalance"}, "Always");
    private final NumberSetting timerSpeed = new NumberSetting("Timer Speed", 2.0, 0.1, 10.0, 0.1);
    private final NumberSetting ticks = new NumberSetting("Max Balance", 8.0, 1.0, 20.0, 1.0,() -> mode.is("SlowBalance"));
    private final ModeSetting keyMode = new ModeSetting("Key Mode", new String[]{"Middle", "Mouse3", "Mouse4"}, "Middle",() -> mode.is("SlowBalance"));

    private float balance = 0.0F;

    @Override
    protected void onEnable() {
        balance = 0.0F;
        GameStateManager.timerSpeed = 1F;
    }

    @Override
    protected void onDisable() {
        balance = 0.0F;
        GameStateManager.timerSpeed = 1F;
    }

    @EventTarget
    public void onUpdate(EventUpdate event) {
        if (!event.isPre() || mc.player == null || mc.level == null) {
            return;
        }

        if (mode.is("Always")) {
            GameStateManager.timerSpeed = timerSpeed.getValue().floatValue();
            return;
        }

        boolean shouldRelease = isReleaseButtonDown();
        boolean isMoving = MovementUtils.isMoving();
        boolean onGround = mc.player.onGround();
        KillAura killAura = Core.INSTANCE.getModuleManager().getModule(KillAura.class);
        boolean hasTarget = killAura != null && killAura.attackTarget != null;

        if (shouldRelease && balance > 0.1F) {
            float timer = timerSpeed.getValue().floatValue();
            float consume = timer - 1.0F;
            if (consume <= 0.0F) {
                GameStateManager.timerSpeed = 1F;
            } else if (balance >= consume) {
                GameStateManager.timerSpeed = timer;
                balance -= consume;
            } else {
                GameStateManager.timerSpeed = 1F;
            }
        } else if (!hasTarget && balance < ticks.getValue().floatValue()) {
            float timer = (!isMoving && onGround) ? 0.8F : 0.96F;
            GameStateManager.timerSpeed = timer;
            balance += (1.0F - timer);
        } else {
            GameStateManager.timerSpeed = 1F;
        }

        balance = Math.clamp(balance, 0.0F, ticks.getValue().floatValue());
    }

    @EventTarget
    public void onWorld(EventWorldLoad event) {
        balance = 0.0F;
        GameStateManager.timerSpeed = 1F;
    }

    private boolean isReleaseButtonDown() {
        long window = mc.getWindow().handle();
        return GLFW.glfwGetMouseButton(window, getButtonCode()) == GLFW.GLFW_PRESS;
    }

    private int getButtonCode() {
        return switch (keyMode.getValue()) {
            case "Mouse3" -> 3;
            case "Mouse4" -> 4;
            default -> GLFW.GLFW_MOUSE_BUTTON_MIDDLE;
        };
    }
}
