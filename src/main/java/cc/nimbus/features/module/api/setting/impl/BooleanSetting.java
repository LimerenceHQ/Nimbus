package cc.nimbus.features.module.api.setting.impl;

import cc.nimbus.features.module.api.setting.Setting;
import java.util.function.Supplier;

public class BooleanSetting extends Setting<Boolean> {
    public BooleanSetting(String name, Boolean value) { this(name, value, () -> true); }
    public BooleanSetting(String name, Boolean value, Supplier<Boolean> visibility) { super(name, value, visibility); }
}
