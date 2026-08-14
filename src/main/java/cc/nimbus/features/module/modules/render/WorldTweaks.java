package cc.nimbus.features.module.modules.render;

import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.BooleanSetting;
import cc.nimbus.features.module.api.setting.impl.ColorSetting;
import cc.nimbus.features.module.api.setting.impl.NumberSetting;

@ModuleInfo(name = "WorldTweaks", description = "Overrides client-side fog and displayed world time.", category = ModuleCategory.Render)
public final class WorldTweaks extends BasicModule {
    public final BooleanSetting modifyFog = new BooleanSetting("Modify fog", true);
    public final NumberSetting fogStart = new NumberSetting("Fog start", 0, 0, 256, 1, modifyFog::getValue);
    public final NumberSetting fogEnd = new NumberSetting("Fog end", 64, 1, 512, 1, modifyFog::getValue);
    public final ColorSetting fogColor = new ColorSetting("Fog color", 0xFFA900FF, modifyFog::getValue, false);
    public final BooleanSetting changeTime = new BooleanSetting("Change time", false);
    public final NumberSetting hour = new NumberSetting("Hour", 21, 0, 23.75, 0.25, changeTime::getValue);

    public long visualTimeTicks() {
        double shiftedHour = (hour.getValue().doubleValue() - 6.0 + 24.0) % 24.0;
        return Math.round(shiftedHour * 1000.0);
    }
}
