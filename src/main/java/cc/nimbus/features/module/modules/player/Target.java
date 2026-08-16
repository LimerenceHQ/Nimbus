package cc.nimbus.features.module.modules.player;

import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.BooleanSetting;

@ModuleInfo(
        name = "Target",
        description = "Selects entity types that combat modules may target.",
        category = ModuleCategory.Player,
        enableOnStartUp = true
)
public final class Target extends BasicModule {
    public final BooleanSetting player = new BooleanSetting("Player", true);
    public final BooleanSetting mob = new BooleanSetting("Mob", false);
    public final BooleanSetting animal = new BooleanSetting("Animal", false);
    public final BooleanSetting golem = new BooleanSetting("Golem", false);
    public final BooleanSetting villager = new BooleanSetting("Villager", false);
    public final BooleanSetting invisible = new BooleanSetting("Invisible", false);
}
