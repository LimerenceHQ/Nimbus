package cc.nimbus.features.module.modules.hud;

import cc.nimbus.core.Core;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.BooleanSetting;

@ModuleInfo(
        name = "Notification",
        displayName = "Notification",
        description = "Displays client and game notifications.",
        category = ModuleCategory.HUD,
        enableOnStartUp = true
)
public final class Notification extends BasicModule {
    public final BooleanSetting client = new BooleanSetting("Client", true);
    public final BooleanSetting game = new BooleanSetting("Game", true);

    public Notification() {
        setHidden(true);
    }

    @Override
    protected void onDisable() {
        if (Core.INSTANCE.isInitialized()) Core.INSTANCE.getNotificationManager().clear();
    }
}
