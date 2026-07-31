package cc.nimbus.features.module.api.setting.impl;

import cc.nimbus.features.module.api.setting.Setting;
import java.util.function.Supplier;

public class StringSetting extends Setting<String> {
    public StringSetting(String name, String value) { this(name, value, () -> true); }
    public StringSetting(String name, String value, Supplier<Boolean> visibility) { super(name, value, visibility); }
}
