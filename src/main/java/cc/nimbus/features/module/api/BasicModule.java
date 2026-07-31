package cc.nimbus.features.module.api;

import cc.nimbus.event.EventBus;
import cc.nimbus.features.module.api.setting.Setting;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

public abstract class BasicModule extends ToggleElement {
    private final ModuleInfo info;
    private final String name;
    private final String displayName;
    private final String description;
    private final ModuleCategory category;
    private final List<Setting<?>> additionalSettings = new ArrayList<>();
    private volatile List<Setting<?>> settings;
    private String suffix = "";
    private int key;
    private boolean hidden;
    private Runnable changeListener = () -> {};
    private Runnable keyChangeListener = () -> {};

    public boolean changeKey;

    protected BasicModule() {
        info = getClass().getAnnotation(ModuleInfo.class);
        if (info == null) {
            throw new IllegalStateException("Module " + getClass().getSimpleName() + " is missing @ModuleInfo");
        }
        name = info.name();
        displayName = info.displayName().isEmpty() ? name : info.displayName();
        description = info.description();
        category = info.category();
        key = info.keyBind();
    }

    protected BasicModule(ModuleInfo info) {
        this.info = Objects.requireNonNull(info, "info");
        name = info.name();
        displayName = info.displayName().isEmpty() ? name : info.displayName();
        description = info.description();
        category = info.category();
        key = info.keyBind();
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        if (enabled) {
            EventBus.register(this);
            onEnable();
        } else {
            EventBus.unregister(this);
            onDisable();
        }
        changed();
    }

    @Override
    protected void onEnable() {
    }

    @Override
    protected void onDisable() {
    }

    public final void bindStateListeners(Runnable changeListener, Runnable keyChangeListener) {
        this.changeListener = changeListener == null ? () -> {} : changeListener;
        this.keyChangeListener = keyChangeListener == null ? () -> {} : keyChangeListener;
        getSettings().forEach(setting -> setting.setChangeListener(this::changed));
    }

    public void setKey(int key) {
        if (this.key == key) return;
        this.key = key;
        keyChangeListener.run();
        changed();
    }

    public void setHidden(boolean hidden) {
        if (this.hidden == hidden) return;
        this.hidden = hidden;
        changed();
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description.isEmpty() ? "This module has no description." : description;
    }

    public ModuleCategory getCategory() {
        return category;
    }

    public void setSuffix(Object suffix) {
        this.suffix = String.valueOf(suffix);
    }

    public String getSuffix() {
        return suffix;
    }

    public int getKey() {
        return key;
    }

    public boolean isHidden() {
        return hidden;
    }

    public boolean shouldAutoEnable() {
        return info.enableOnStartUp();
    }

    public String[] getAliases() {
        return info.alias().clone();
    }

    public final List<Setting<?>> getSettings() {
        List<Setting<?>> result = settings;
        if (result != null) return result;

        synchronized (this) {
            if (settings == null) settings = discoverSettings();
            return settings;
        }
    }

    public final synchronized void addSetting(Setting<?> setting) {
        Objects.requireNonNull(setting, "setting");
        if (additionalSettings.contains(setting)) return;
        setting.setChangeListener(this::changed);
        additionalSettings.add(setting);
        settings = null;
    }

    private List<Setting<?>> discoverSettings() {
        LinkedHashSet<Setting<?>> found = new LinkedHashSet<>();
        List<Class<?>> hierarchy = new ArrayList<>();
        for (Class<?> type = getClass(); type != null && BasicModule.class.isAssignableFrom(type);
             type = type.getSuperclass()) {
            hierarchy.add(type);
        }
        Collections.reverse(hierarchy);

        for (Class<?> type : hierarchy) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())
                        || field.isAnnotationPresent(Exclude.class)
                        || !Setting.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    if (field.get(this) instanceof Setting<?> setting && found.add(setting)) {
                        setting.setChangeListener(this::changed);
                    }
                } catch (IllegalAccessException error) {
                    throw new IllegalStateException("Cannot read setting field " + field, error);
                }
            }
        }
        for (Setting<?> setting : additionalSettings) {
            if (found.add(setting)) setting.setChangeListener(this::changed);
        }
        return List.copyOf(found);
    }

    private void changed() {
        changeListener.run();
    }
}
