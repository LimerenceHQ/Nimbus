package cc.nimbus.features.module.api.setting.impl.submodule;

import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.setting.Setting;
import cc.nimbus.features.module.api.setting.impl.modes.Modes;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public class ModeValue extends Setting<String> {
    private final List<Mode<?>> modes;
    private Object parent;
    private boolean initialized;

    public ModeValue(String name, String defaultName, Mode<?>... modes) {
        this(name, defaultName, () -> true, modes);
    }

    public ModeValue(String name, String defaultName, Supplier<Boolean> visibility, Mode<?>... modes) {
        super(name, findDefault(defaultName, modes).getName(), visibility);
        this.modes = validateModes(modes);
    }

    public ModeValue(String name, Mode<?> defaultMode, Mode<?>... modes) {
        this(name, defaultMode, () -> true, modes);
    }

    public ModeValue(String name, Mode<?> defaultMode, Supplier<Boolean> visibility, Mode<?>... modes) {
        super(name, requireDefault(defaultMode, modes).getName(), visibility);
        this.modes = validateModes(modes);
    }

    public ModeValue(String name, Modes modes, String value) {
        this(name, modes, value, () -> true);
    }

    public ModeValue(String name, Modes modes, String value, Supplier<Boolean> visibility) {
        this(name, value, visibility, namedModes(modes.getModes()));
    }

    public ModeValue(String name, String[] modes, String value) {
        this(name, modes, value, () -> true);
    }

    public ModeValue(String name, String[] modes, String value, Supplier<Boolean> visibility) {
        this(name, value, visibility, namedModes(modes));
    }

    public ModeValue(String name, Modes modes) {
        this(name, modes, modes.getModes()[0]);
    }

    public ModeValue(String name, Modes modes, Supplier<Boolean> visibility) {
        this(name, modes, modes.getModes()[0], visibility);
    }

    public ModeValue(String name, String[] modes) {
        this(name, modes, modes[0]);
    }

    public ModeValue(String name, String[] modes, Supplier<Boolean> visibility) {
        this(name, modes, modes[0], visibility);
    }

    @Override
    protected String normalize(String value) {
        return modes.stream().anyMatch(mode -> mode.getName().equalsIgnoreCase(value))
                ? modes.stream().filter(mode -> mode.getName().equalsIgnoreCase(value)).findFirst().orElseThrow().getName()
                : getValue();
    }

    @Override
    public void setValue(String value) {
        Mode<?> old = currentMode();
        String nextName = normalize(value);
        if (Objects.equals(getValue(), nextName)) return;
        boolean active = old != null && old.isActive();
        if (active) old.unregister();
        super.setValue(nextName);
        Mode<?> next = currentMode();
        if (active && next != null) next.register();
    }

    public void update(Mode<?> mode) {
        if (mode != null) setValue(mode.getName());
    }

    public void update(String modeName) {
        if (modeName == null) return;
        modes.stream()
                .filter(mode -> mode.getName().equalsIgnoreCase(modeName))
                .findFirst()
                .ifPresent(mode -> setValue(mode.getName()));
    }

    public String getValueName() {
        return getValue();
    }

    public boolean is(String modeName) {
        return modeName != null && getValueName().equalsIgnoreCase(modeName);
    }

    public boolean is(String... modeNames) {
        return modeNames != null && Arrays.stream(modeNames).anyMatch(this::is);
    }

    public List<Mode<?>> getModes() {
        return Collections.unmodifiableList(modes);
    }

    public String[] getModeNames() {
        return modes.stream().map(Mode::getName).toArray(String[]::new);
    }

    public void initializeSubModules(BasicModule module) {
        initializeSubModules(module, module);
    }

    @SuppressWarnings("unchecked")
    public void initializeSubModules(BasicModule module, Object parent) {
        Objects.requireNonNull(module, "module");
        if (initialized) {
            return;
        }
        initialized = true;
        this.parent = Objects.requireNonNull(parent, "parent");
        for (Mode<?> mode : modes) {
            ((Mode<Object>) mode).setParent(parent);
            mode.reflectProperties();
            module.registerMode(mode);
            for (Setting<?> setting : mode.getSettings()) {
                setting.addVisibilityCondition(() -> is(mode.getName()));
                module.addSetting(setting);
                if (setting instanceof ModeValue nested) nested.initializeSubModules(module, mode);
                if (setting instanceof ComponentValue nested) nested.initializeSubModules(module, mode);
            }
        }
    }

    public boolean isRoot() {
        return parent instanceof BasicModule;
    }

    public Mode<?> currentMode() {
        return modes.stream().filter(mode -> mode.getName().equalsIgnoreCase(getValue())).findFirst().orElse(null);
    }

    public void onModuleEnabled() {
        if (isRoot()) onParentEnabled();
    }

    public void onModuleDisabled() {
        if (isRoot()) onParentDisabled();
    }

    void onParentEnabled() {
        Mode<?> current = currentMode();
        if (current != null && !current.isActive()) current.register();
    }

    void onParentDisabled() {
        Mode<?> current = currentMode();
        if (current != null && current.isActive()) current.unregister();
    }

    private static List<Mode<?>> validateModes(Mode<?>[] modes) {
        if (modes == null || modes.length == 0) throw new IllegalArgumentException("modes cannot be empty");
        List<Mode<?>> values = List.copyOf(Arrays.asList(modes));
        for (Mode<?> mode : values) Objects.requireNonNull(mode, "mode");
        return values;
    }

    private static Mode<?> findDefault(String defaultName, Mode<?>[] modes) {
        if (modes == null || modes.length == 0) throw new IllegalArgumentException("modes cannot be empty");
        return Arrays.stream(modes)
                .filter(Objects::nonNull)
                .filter(mode -> mode.getName().equalsIgnoreCase(defaultName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Mode '" + defaultName + "' not found"));
    }

    private static Mode<?> requireDefault(Mode<?> defaultMode, Mode<?>[] modes) {
        if (modes == null || !Arrays.asList(modes).contains(defaultMode)) {
            throw new IllegalArgumentException("Default mode is not in the modes list");
        }
        return defaultMode;
    }

    private static Mode<?>[] namedModes(String[] names) {
        if (names == null || names.length == 0) throw new IllegalArgumentException("modes cannot be empty");
        return Arrays.stream(names).map(NamedMode::new).toArray(Mode<?>[]::new);
    }

    private static final class NamedMode extends Mode<Object> {
        private NamedMode(String name) {
            super(name);
        }

        @Override
        protected boolean needsEventRegistration() {
            return false;
        }
    }
}
