package cc.nimbus.features.module.modules.render;

import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.BooleanSetting;
import cc.nimbus.features.module.api.setting.impl.NumberSetting;

@ModuleInfo(name = "WorldTweaks", description = "Overrides the displayed client-side world time.", category = ModuleCategory.Render)
public final class WorldTweaks extends BasicModule {
    public final BooleanSetting changeTime = new BooleanSetting("Change time", false);
    public final NumberSetting hour = new NumberSetting("Hour", 21, 0, 23.75, 0.25, changeTime::getValue);

    public long visualTimeTicks() {
        double shiftedHour = (hour.getValue().doubleValue() - 6.0 + 24.0) % 24.0;
        return Math.round(shiftedHour * 1000.0);
    }
}
