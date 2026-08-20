package cc.nimbus.features.module.modules.combat;


import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.submodule.ModeValue;
import cc.nimbus.features.module.modules.combat.velocitys.Legit;
import cc.nimbus.features.module.modules.combat.velocitys.Vanilla;

@ModuleInfo(name = "Velocity", description = "Prevent you from taking excessive knockback",category = ModuleCategory.Combat)
public class Velocity extends BasicModule {
    public ModeValue anticheat = new ModeValue("AntiCheat","Vanilla",new Vanilla(),new Legit());

}
