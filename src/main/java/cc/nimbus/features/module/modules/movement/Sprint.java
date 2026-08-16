package cc.nimbus.features.module.modules.movement;

import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.player.EventMoveInput;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import net.minecraft.client.Minecraft;

@ModuleInfo(
        name = "Sprint",
        description = "Automatically sprints while moving forward.",
        category = ModuleCategory.Move
)
public final class Sprint extends BasicModule {
    @Override
    protected void onEnable() {
        setSprintKey(false);
    }

    @Override
    protected void onDisable() {
        setSprintKey(false);
    }

    @EventTarget
    private void onMoveInput(EventMoveInput event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            setSprintKey(false);
            return;
        }
        boolean moving = event.getForward() != 0.0f || event.getStrafe() != 0.0f;
        event.setSprint(moving);
        setSprintKey(moving);
    }

    private static void setSprintKey(boolean down) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options != null) minecraft.options.keySprint.setDown(down);
    }
}
