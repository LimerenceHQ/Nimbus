package cc.nimbus.features.module.modules.movement;

import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.game.EventTick;
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
        Minecraft.getInstance().options.keySprint.setDown(false);
    }

    @Override
    protected void onDisable() {
        Minecraft.getInstance().options.keySprint.setDown(false);
    }

    @EventTarget
    private void onTick(EventTick event) {
        if (event.getPhase() != EventTick.Phase.PRE) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.player.input == null) {
            minecraft.options.keySprint.setDown(false);
            return;
        }

        minecraft.options.keySprint.setDown(
                minecraft.player.input.getMoveVector().lengthSquared() > 0.0f
        );
    }
}
