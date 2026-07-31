package cc.nimbus.features.module.api.setting.impl;

import cc.nimbus.features.module.api.setting.Setting;
import cc.nimbus.features.module.api.setting.impl.modes.Modes;
import java.util.*;
import java.util.function.Supplier;

public class MultiSelectSetting extends Setting<Set<String>> {
    private final Modes modes;
    public MultiSelectSetting(String name, Modes modes) { this(name, modes, Set.of()); }
    public MultiSelectSetting(String name, Modes modes, Supplier<Boolean> visibility) { this(name, modes, Set.of(), visibility); }
    public MultiSelectSetting(String name, Modes modes, Set<String> selected) { this(name, modes, selected, () -> true); }
    public MultiSelectSetting(String name, Modes modes, Set<String> selected, Supplier<Boolean> visibility) { super(name, new LinkedHashSet<>(), visibility); this.modes = modes; initializeValue(selected); }
    public MultiSelectSetting(String name, String[] modes) { this(name, new Modes(modes)); }
    public MultiSelectSetting(String name, String[] modes, Supplier<Boolean> visibility) { this(name, new Modes(modes), visibility); }
    public MultiSelectSetting(String name, String[] modes, Set<String> selected) { this(name, new Modes(modes), selected); }
    public MultiSelectSetting(String name, String[] modes, Set<String> selected, Supplier<Boolean> visibility) { this(name, new Modes(modes), selected, visibility); }
    @Override protected Set<String> normalize(Set<String> value) { Set<String> valid = new LinkedHashSet<>(Arrays.asList(modes.getModes())); valid.retainAll(value == null ? Set.of() : value); return valid; }
    @Override public Set<String> getValue() { return Collections.unmodifiableSet(super.getValue()); }
    public String[] getModes() { return modes.getModes(); }
    public boolean isSelected(String value) { return getValue().contains(value); }
    public void select(String value) { Set<String> next = new LinkedHashSet<>(getValue()); next.add(value); setValue(next); }
    public void deselect(String value) { Set<String> next = new LinkedHashSet<>(getValue()); next.remove(value); setValue(next); }
    public void toggle(String value) { if (isSelected(value)) deselect(value); else select(value); }
}
