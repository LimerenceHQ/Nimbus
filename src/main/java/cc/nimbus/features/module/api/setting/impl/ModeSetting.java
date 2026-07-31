package cc.nimbus.features.module.api.setting.impl;

import cc.nimbus.features.module.api.setting.Setting;
import cc.nimbus.features.module.api.setting.impl.modes.Modes;
import java.util.Arrays;
import java.util.function.Supplier;

public class ModeSetting extends Setting<String> {
    private final Modes modes;
    public ModeSetting(String name, Modes modes, String value) { this(name, modes, value, () -> true); }
    public ModeSetting(String name, Modes modes, String value, Supplier<Boolean> visibility) { super(name, validDefault(modes, value), visibility); this.modes = modes; }
    public ModeSetting(String name, String[] modes, String value) { this(name, new Modes(modes), value); }
    public ModeSetting(String name, String[] modes, String value, Supplier<Boolean> visibility) { this(name, new Modes(modes), value, visibility); }
    public ModeSetting(String name, Modes modes) { this(name, modes, modes.getModes()[0]); }
    public ModeSetting(String name, Modes modes, Supplier<Boolean> visibility) { this(name, modes, modes.getModes()[0], visibility); }
    public ModeSetting(String name, String[] modes) { this(name, new Modes(modes)); }
    public ModeSetting(String name, String[] modes, Supplier<Boolean> visibility) { this(name, new Modes(modes), visibility); }
    private static String validDefault(Modes modes, String value) { return Arrays.asList(modes.getModes()).contains(value) ? value : modes.getModes()[0]; }
    @Override protected String normalize(String value) { return Arrays.asList(modes.getModes()).contains(value) ? value : getValue(); }
    public boolean is(String value) { return getValue().equals(value); }
    public boolean is(String... values) { return Arrays.asList(values).contains(getValue()); }
    public String[] getModes() { return modes.getModes(); }
}
