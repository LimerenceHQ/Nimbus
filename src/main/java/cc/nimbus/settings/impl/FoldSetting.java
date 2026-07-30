package cc.nimbus.settings.impl;

import cc.nimbus.settings.Setting;
import java.util.function.Supplier;

public class FoldSetting extends Setting<Boolean> {
    public FoldSetting(String name, Boolean value) { this(name, value, () -> true); }
    public FoldSetting(String name, Boolean value, Supplier<Boolean> visibility) { super(name, value, visibility); }
}
