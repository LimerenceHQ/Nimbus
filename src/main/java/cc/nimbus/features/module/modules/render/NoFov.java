package cc.nimbus.features.module.modules.render;

import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.NumberSetting;

@ModuleInfo(name = "NoFov", description = "Removes field-of-view changes caused by player actions and effects.",
        category = ModuleCategory.Render)
public final class NoFov extends BasicModule {
    public final NumberSetting fov = new NumberSetting("Fov", 1.0, 0.1, 1.5, 0.1);
}
