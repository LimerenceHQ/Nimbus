package cc.nimbus.module;

import cc.nimbus.config.ChangeTracker;
import cc.nimbus.event.handler.EventManager;
import cc.nimbus.settings.Setting;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

public abstract class BasicModule extends ToggleElement {
    final ModuleInfo info;
    private String suffix = "";
    protected String name;
    protected String displayName;
    protected String description;
    protected ModuleCategory category;
    protected int key;
    private boolean hidden;
    private volatile List<Setting<?>> settings;
    public boolean changeKey;
    public boolean showSettings;

    public BasicModule() {
        ModuleInfo annotation = getClass().getAnnotation(ModuleInfo.class);
        if (annotation == null) throw new IllegalStateException("Module " + getClass().getSimpleName() + " is missing @ModuleInfo");
        this.info = annotation;
        initFromInfo(annotation);
    }

    protected BasicModule(ModuleInfo info) {
        this.info = Objects.requireNonNull(info, "info");
        initFromInfo(info);
    }

    private void initFromInfo(ModuleInfo info) {
        name = info.name();
        displayName = info.displayName().isEmpty() ? name : info.displayName();
        description = info.description();
        category = info.category();
        key = info.keyBind();
    }

    @Override public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        if (enabled) { EventManager.register(this); onEnable(); }
        else { EventManager.unregister(this); onDisable(); }
        ChangeTracker.changed();
    }

    @Override protected void onEnable() {}
    @Override protected void onDisable() {}
    public String getName() { return name; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description.isEmpty() ? "This module has no description." : description; }
    public ModuleCategory getCategory() { return category; }
    public void setSuffix(Object suffix) { this.suffix = String.valueOf(suffix); }
    public String getSuffix() { return suffix; }
    public int getKey() { return key; }
    public void setKey(int key) { if (this.key == key) return; this.key = key; ModuleManager.getInstance().rebuildKeyMap(); ChangeTracker.changed(); }
    public boolean isHidden() { return hidden; }
    public void setHidden(boolean hidden) { if (this.hidden == hidden) return; this.hidden = hidden; ChangeTracker.changed(); }
    public boolean shouldAutoEnable() { return info.enableOnStartUp(); }
    public String[] getAliases() { return info.alias().clone(); }

    public List<Setting<?>> getSettings() {
        List<Setting<?>> result = settings;
        if (result != null) return result;
        LinkedHashSet<Setting<?>> found = new LinkedHashSet<>();
        List<Class<?>> hierarchy = new ArrayList<>();
        for (Class<?> type = getClass(); type != null && BasicModule.class.isAssignableFrom(type); type = type.getSuperclass()) hierarchy.add(type);
        Collections.reverse(hierarchy);
        for (Class<?> type : hierarchy) for (Field field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.isAnnotationPresent(Exclude.class) || !Setting.class.isAssignableFrom(field.getType())) continue;
            try { field.setAccessible(true); Object value = field.get(this); if (value instanceof Setting<?> setting) found.add(setting); }
            catch (IllegalAccessException e) { throw new IllegalStateException("Cannot read setting " + field, e); }
        }
        settings = new ArrayList<>(found);
        return settings;
    }

    public void addSetting(Setting<?> setting) {
        List<Setting<?>> values = new ArrayList<>(getSettings());
        if (!values.contains(setting)) values.add(setting);
        settings = values;
    }
}
