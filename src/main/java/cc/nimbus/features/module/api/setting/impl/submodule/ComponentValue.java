package cc.nimbus.features.module.api.setting.impl.submodule;

import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.setting.Setting;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/** A multi-select value whose options are real Mode instances. */
public final class ComponentValue extends Setting<Set<String>> {
    private final List<Mode<?>> modes;
    private Object parent;
    private boolean initialized;

    public ComponentValue(String name, Mode<?>... modes) {
        this(name, Set.of(), () -> true, modes);
    }

    public ComponentValue(String name, String[] defaults, Mode<?>... modes) {
        this(name, new LinkedHashSet<>(Arrays.asList(defaults)), () -> true, modes);
    }

    public ComponentValue(String name, Set<String> defaults, Supplier<Boolean> visibility, Mode<?>... modes) {
        super(name, new LinkedHashSet<>(), visibility);
        this.modes = List.copyOf(Arrays.asList(modes));
        initializeValue(defaults);
    }

    @Override
    protected Set<String> normalize(Set<String> value) {
        Set<String> valid = new LinkedHashSet<>();
        Set<String> requested = value == null ? Set.of() : value;
        for (Mode<?> mode : modes) {
            if (requested.stream().anyMatch(name -> name.equalsIgnoreCase(mode.getName()))) valid.add(mode.getName());
        }
        return valid;
    }

    @Override
    public Set<String> getValue() {
        return Collections.unmodifiableSet(super.getValue());
    }

    public List<Mode<?>> getModes() {
        return Collections.unmodifiableList(modes);
    }

    public String[] getModeNames() {
        return modes.stream().map(Mode::getName).toArray(String[]::new);
    }

    public boolean isEnabled(Mode<?> mode) {
        return mode != null && isEnabled(mode.getName());
    }

    public boolean isEnabled(String name) {
        return getValue().stream().anyMatch(value -> value.equalsIgnoreCase(name));
    }

    public void setEnabled(Mode<?> mode, boolean enabled) {
        if (mode == null) return;
        LinkedHashSet<String> next = new LinkedHashSet<>(getValue());
        if (enabled) next.add(mode.getName()); else next.removeIf(name -> name.equalsIgnoreCase(mode.getName()));
        setValue(next);
        if (mode.isModuleEnabled()) {
            if (enabled && !mode.isActive()) mode.register();
            if (!enabled && mode.isActive()) mode.unregister();
        }
    }

    public void toggle(String name) {
        setEnabled(find(name), !isEnabled(name));
    }

    public void toggle(Mode<?> mode) {
        if (mode != null) setEnabled(mode, !isEnabled(mode));
    }

    @SuppressWarnings("unchecked")
    public void initializeSubModules(BasicModule module) {
        initializeSubModules(module, module);
    }

    @SuppressWarnings("unchecked")
    public void initializeSubModules(BasicModule module, Object parent) {
        if (initialized) return;
        initialized = true;
        this.parent = parent;
        for (Mode<?> mode : modes) {
            ((Mode<Object>) mode).setParent(parent);
            mode.reflectProperties();
            module.registerMode(mode);
            for (Setting<?> setting : mode.getSettings()) {
                setting.addVisibilityCondition(() -> isEnabled(mode));
                module.addSetting(setting);
                if (setting instanceof ModeValue nested) nested.initializeSubModules(module, mode);
                if (setting instanceof ComponentValue nested) nested.initializeSubModules(module, mode);
            }
        }
    }

    public void onModuleEnabled() {
        for (Mode<?> mode : modes) if (isEnabled(mode) && !mode.isActive()) mode.register();
    }

    public void onModuleDisabled() {
        for (Mode<?> mode : modes) if (mode.isActive()) mode.unregister();
    }

    private Mode<?> find(String name) {
        return modes.stream().filter(mode -> mode.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
    }
}
