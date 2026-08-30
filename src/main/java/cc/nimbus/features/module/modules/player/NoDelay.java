package cc.nimbus.features.module.modules.player;

import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.game.EventTick;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.BooleanSetting;
import cc.nimbus.mixin.accessor.client.MinecraftAccessor;
import cc.nimbus.mixin.accessor.player.LivingEntityAccessor;

@ModuleInfo(name = "NoDelay",category = ModuleCategory.Player)
public class NoDelay extends BasicModule {

    public BooleanSetting jumpDelay = new BooleanSetting("Jump Delay", true);
    public BooleanSetting attackDelay = new BooleanSetting("Attack Delay", true);

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
    }

    @EventTarget
    public void onTick(EventTick event) {
        if (!event.isPre()) return;
        if (mc.player == null) return;

        if (jumpDelay.getValue()) ((LivingEntityAccessor)mc.player).setNoJumpDelay(0);

        if (attackDelay.getValue()) ((MinecraftAccessor)mc).setMissTime(0);
    }
}
