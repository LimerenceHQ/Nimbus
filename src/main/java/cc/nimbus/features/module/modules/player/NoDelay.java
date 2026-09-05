package cc.nimbus.features.module.modules.player;

import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.game.EventTick;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.BooleanSetting;
import cc.nimbus.mixin.accessor.client.MinecraftAccessor;
import cc.nimbus.mixin.accessor.player.LivingEntityAccessor;

@ModuleInfo(
        name = "NoDelay",
        description = "Removes the client-side jump and attack delays.",
        category = ModuleCategory.Player)
public final class NoDelay extends BasicModule {

    public final BooleanSetting jumpDelay = new BooleanSetting("Jump Delay", true);
    public final BooleanSetting attackDelay = new BooleanSetting("Attack Delay", true);

    @EventTarget
    private void onTick(EventTick event) {
        if (event.getPhase() != EventTick.Phase.PRE || mc.player == null) return;

        if (jumpDelay.getValue()) {
            ((LivingEntityAccessor) mc.player).setNoJumpDelay(0);
        }

        if (attackDelay.getValue()) {
            ((MinecraftAccessor) mc).nimbus$setMissTime(0);
        }
    }
}
