package cc.nimbus.features.module.modules.render;

import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.ModeSetting;

@ModuleInfo(name = "FullBright", description = "Keeps the world clearly lit without changing game effects.", category = ModuleCategory.Render)
public final class FullBright extends BasicModule {
    public final ModeSetting mode = new ModeSetting("Mode", new String[]{"Gamma", "Night Vision"}, "Gamma");
}
