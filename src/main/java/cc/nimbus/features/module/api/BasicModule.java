package cc.nimbus.features.module.api;

import cc.nimbus.event.EventBus;
import cc.nimbus.features.hud.Draggable;
import cc.nimbus.features.module.api.setting.Setting;
import cc.nimbus.features.module.api.setting.impl.submodule.Mode;
import cc.nimbus.features.module.api.setting.impl.submodule.ModeValue;
import cc.nimbus.features.module.api.setting.impl.submodule.ComponentValue;
import cc.nimbus.core.Core;
import cc.nimbus.ui.notification.NotificationType;
import cc.nimbus.utils.client.IMinecraft;
import cc.nimbus.utils.i18n.I18nKeys;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

public abstract class BasicModule extends ToggleElement implements IMinecraft {
    private final ModuleInfo info;
    private final String name;
    private final String displayName;
    private final String description;
    private final ModuleCategory category;
    private final List<Setting<?>> additionalSettings = new ArrayList<>();
    private volatile Members members;
    private String suffix = "";
    private int key;
    private boolean hidden;
    private Runnable changeListener = () -> {};
    private Runnable keyChangeListener = () -> {};
    private final Map<Class<?>, Mode<?>> modeClassMap = new HashMap<>();

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
    public synchronized void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;

        if (enabled) {
            enable();
        } else {
            disable();
        }
    }

    private void enable() {
        boolean hookStarted = false;
        boolean modesStarted = false;
        try {
            Members current = members();
            EventBus.register(this);
            hookStarted = true;
            current.settings().stream()
                    .filter(ModeValue.class::isInstance)
                    .map(ModeValue.class::cast)
                    .filter(ModeValue::isRoot)
                    .forEach(ModeValue::onModuleEnabled);
            current.settings().stream()
                    .filter(ComponentValue.class::isInstance)
                    .map(ComponentValue.class::cast)
                    .filter(ComponentValue::isRoot)
                    .forEach(ComponentValue::onModuleEnabled);
            modesStarted = true;
            onEnable();
            if (enabled) {
                changed();
                postToggleNotification(true);
            }
        } catch (Throwable failure) {
            boolean needsHookCleanup = hookStarted && enabled;
            enabled = false;
            if (modesStarted) failure = cleanup(failure, this::disableModes);
            failure = cleanup(failure, () -> EventBus.unregister(this));
            if (needsHookCleanup) failure = cleanup(failure, this::onDisable);
            failure = cleanup(failure, this::changed);
            rethrow(failure);
        }
    }

    private void disable() {
        Throwable failure = null;
        failure = cleanup(failure, this::disableModes);
        failure = cleanup(failure, () -> EventBus.unregister(this));
        failure = cleanup(failure, this::onDisable);
        failure = cleanup(failure, () -> postToggleNotification(false));
        failure = cleanup(failure, this::changed);
        if (failure != null) rethrow(failure);
    }

    private void disableModes() {
        members().settings().stream()
                .filter(ModeValue.class::isInstance)
                .map(ModeValue.class::cast)
                .filter(ModeValue::isRoot)
                .forEach(ModeValue::onModuleDisabled);
        members().settings().stream()
                .filter(ComponentValue.class::isInstance)
                .map(ComponentValue.class::cast)
                .filter(ComponentValue::isRoot)
                .forEach(ComponentValue::onModuleDisabled);
    }

    private static Throwable cleanup(Throwable failure, Runnable action) {
        try {
            action.run();
        } catch (Throwable error) {
            if (failure == null) return error;
            failure.addSuppressed(error);
        }
        return failure;
    }

    private static void rethrow(Throwable failure) {
        if (failure instanceof RuntimeException runtime) throw runtime;
        if (failure instanceof Error error) throw error;
        throw new IllegalStateException(failure);
    }

    private void postToggleNotification(boolean enabled) {
        if (!Core.INSTANCE.isInitialized() || mc.level == null || mc.player == null || getName().equalsIgnoreCase("Notification")) return;
        String moduleName = Core.INSTANCE.getI18nManager().get(
                getDisplayNameTranslationKey(), getDisplayName());
        String message = Core.INSTANCE.getI18nManager().get(
                enabled ? "nimbus.module.toggle.enabled" : "nimbus.module.toggle.disabled",
                enabled ? "%s enabled" : "%s disabled", moduleName);
        Core.INSTANCE.getNotificationManager().postGame(
                message,
                2000L,
                enabled ? NotificationType.SUCCESS : NotificationType.ERROR);
    }

    @Override
    protected void onEnable() {
    }

    @Override
    protected void onDisable() {
    }

    public void registerMode(Mode<?> mode) {
        modeClassMap.put(mode.getClass(), mode);
    }

    public final void bindStateListeners(Runnable changeListener, Runnable keyChangeListener) {
        this.changeListener = changeListener == null ? () -> {} : changeListener;
        this.keyChangeListener = keyChangeListener == null ? () -> {} : keyChangeListener;
        Members current = members();
        current.settings().forEach(setting -> setting.setChangeListener(this::changed));
        current.draggables().forEach(draggable -> draggable.setChangeListener(this::changed));
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

    public String getDisplayNameTranslationKey() {
        return translationScope() + ".name";
    }

    public String getDescriptionTranslationKey() {
        return translationScope() + ".description";
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
        return members().settings();
    }

    public final List<Draggable> getDraggables() {
        return members().draggables();
    }

    public final synchronized void addSetting(Setting<?> setting) {
        Objects.requireNonNull(setting, "setting");
        if (additionalSettings.contains(setting)) return;
        setting.setChangeListener(this::changed);
        additionalSettings.add(setting);
        members = null;
    }

    private Members members() {
        Members result = members;
        if (result != null) return result;

        synchronized (this) {
            if (members == null) members = discoverMembers();
            return members;
        }
    }

    private Members discoverMembers() {
        LinkedHashSet<Setting<?>> settings = new LinkedHashSet<>();
        LinkedHashSet<Draggable> draggables = new LinkedHashSet<>();
        Set<String> draggableNames = new LinkedHashSet<>();
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
                        || (!Setting.class.isAssignableFrom(field.getType())
                        && !Draggable.class.isAssignableFrom(field.getType()))) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(this);
                    if (value instanceof Setting<?> setting && settings.add(setting)) {
                        setting.bindTranslationScope(translationScope());
                        setting.setChangeListener(this::changed);
                        if (setting instanceof ModeValue modeValue) {
                            modeValue.initializeSubModules(this);
                        }
                        if (setting instanceof ComponentValue componentValue) {
                            componentValue.initializeSubModules(this);
                        }
                    } else if (value instanceof Draggable draggable && draggables.add(draggable)) {
                        String key = draggable.getName().toLowerCase(Locale.ROOT);
                        if (!draggableNames.add(key)) {
                            throw new IllegalStateException("Duplicate draggable name '" + draggable.getName()
                                    + "' in module " + name);
                        }
                        draggable.setChangeListener(this::changed);
                    }
                } catch (IllegalAccessException error) {
                    throw new IllegalStateException("Cannot read module field " + field, error);
                }
            }
        }
        // ModeValue/ComponentValue expand their child settings through addSetting().
        // Process by index so nested expansion is included in this same Members snapshot.
        for (int index = 0; index < additionalSettings.size(); index++) {
            Setting<?> setting = additionalSettings.get(index);
            if (settings.add(setting)) {
                setting.bindTranslationScope(translationScope());
                setting.setChangeListener(this::changed);
                if (setting instanceof ModeValue modeValue) {
                    modeValue.initializeSubModules(this);
                }
                if (setting instanceof ComponentValue componentValue) {
                    componentValue.initializeSubModules(this);
                }
            }
        }
        return new Members(List.copyOf(settings), List.copyOf(draggables));
    }
    public <T extends Mode<?>> T getSubModule(Class<T> modeClass) {
        return (T) modeClassMap.get(modeClass);
    }

    private String translationScope() {
        return "nimbus.module." + I18nKeys.part(name);
    }

    private void changed() {
        changeListener.run();
    }

    private record Members(List<Setting<?>> settings, List<Draggable> draggables) {}
}
