package cc.nimbus.features.module.modules.render;

import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.BooleanSetting;
import cc.nimbus.features.module.api.setting.impl.NumberSetting;

@ModuleInfo(name = "Item Physics", description = "Gives dropped items grounded and airborne physical rotation.", category = ModuleCategory.Render)
public final class ItemPhysics extends BasicModule {
    public final BooleanSetting removeBob = new BooleanSetting("Remove bob", true);
    public final NumberSetting groundedTilt = new NumberSetting("Grounded tilt", 90, 0, 180, 1);
    public final BooleanSetting airRotation = new BooleanSetting("Air rotation", true);
    public final NumberSetting rotationSpeed = new NumberSetting("Rotation speed", 2.5, 0.1, 10, 0.1,
            airRotation::getValue);
}
