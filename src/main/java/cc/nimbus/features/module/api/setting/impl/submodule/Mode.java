package cc.nimbus.features.module.api.setting.impl.submodule;

import cc.nimbus.event.EventBus;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.setting.Setting;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

public abstract class Mode<P> {
    private final String name;
    private final List<Setting<?>> settings = new ArrayList<>();
    private final List<ModeValue> nestedModeValues = new ArrayList<>();
    private P parent;
    private boolean active;
    private boolean reflected;

    protected Mode(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    public final String getName() {
        return name;
    }

    public final P getParent() {
        return parent;
    }

    public final void setParent(P parent) {
        if (this.parent != null && this.parent != parent) {
            throw new IllegalStateException("Mode '" + name + "' already has a parent");
        }
        this.parent = Objects.requireNonNull(parent, "parent");
    }

    public final BasicModule getModule() {
        Object current = parent;
        while (current instanceof Mode<?> mode) current = mode.getParent();
        return current instanceof BasicModule module ? module : null;
    }

    public final boolean isActive() {
        return active;
    }

    public final boolean isModuleEnabled() {
        Object current = parent;
        while (current instanceof Mode<?> mode) {
            if (!mode.isActive()) return false;
            current = mode.getParent();
        }
        return current instanceof BasicModule module && module.isEnabled();
    }

    public final List<Setting<?>> getSettings() {
        reflectProperties();
        return Collections.unmodifiableList(settings);
    }

    final List<ModeValue> getNestedModeValues() {
        reflectProperties();
        return Collections.unmodifiableList(nestedModeValues);
    }

    public final void reflectProperties() {
        if (reflected) return;
        reflected = true;
        LinkedHashSet<Setting<?>> discovered = new LinkedHashSet<>();
        List<Class<?>> hierarchy = new ArrayList<>();
        for (Class<?> type = getClass(); type != null && Mode.class.isAssignableFrom(type);
             type = type.getSuperclass()) {
            hierarchy.add(type);
        }
        Collections.reverse(hierarchy);
        for (Class<?> type : hierarchy) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())
                        || !Setting.class.isAssignableFrom(field.getType())) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(this);
                    if (value instanceof Setting<?> setting && discovered.add(setting)) {
                        settings.add(setting);
                        if (setting instanceof ModeValue nested) nestedModeValues.add(nested);
                    }
                } catch (IllegalAccessException error) {
                    throw new IllegalStateException("Cannot read mode field " + field, error);
                }
            }
        }
    }

    public final void register() {
        if (active) return;
        active = true;
        if (needsEventRegistration()) EventBus.register(this);
        for (ModeValue nested : getNestedModeValues()) nested.onParentEnabled();
        onEnable();
    }

    public final void unregister() {
        if (!active) return;
        for (ModeValue nested : getNestedModeValues()) nested.onParentDisabled();
        if (needsEventRegistration()) EventBus.unregister(this);
        try {
            onDisable();
        } finally {
            active = false;
        }
    }

    protected boolean needsEventRegistration() {
        return true;
    }

    protected void onEnable() {
    }

    protected void onDisable() {
    }
}
