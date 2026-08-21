package cc.nimbus.features.module.modules.movement;

import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.game.EventTick;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.utils.entity.player.MovementUtils;

@ModuleInfo(
        name = "Sprint",
        description = "Automatically sprints while moving forward.",
        category = ModuleCategory.Move
)
public class Sprint extends BasicModule {
    public boolean canSprint = true;

    @Override
    protected void onEnable() {
        canSprint = true;
        mc.options.keySprint.setDown(false);
    }

    @Override
    protected void onDisable() {
        canSprint = false;
        mc.options.keySprint.setDown(false);
    }

    @EventTarget
    public void onStrafe(EventTick event) {
        if (!event.isPre()) return;
        if (canSprint && MovementUtils.isMoving()) {
            mc.options.keySprint.setDown(true);
        }
    }
}
