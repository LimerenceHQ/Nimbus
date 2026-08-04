package cc.nimbus.features.module.api.setting;

import cc.nimbus.utils.i18n.I18nKeys;

import java.util.Objects;
import java.util.function.Supplier;

public class Setting<V> {
    private final String name;
    private V value;
    private final Supplier<Boolean> visibility;
    private String translationKey;
    private Runnable changeListener = () -> {};

    public Setting(String name, V value, Supplier<Boolean> visibility) {
        this.name = Objects.requireNonNull(name, "name");
        this.value = value;
        this.visibility = visibility == null ? () -> true : visibility;
    }

    public boolean isHidden() {
        return !visibility.get();
    }

    public V getValue() {
        return value;
    }

    protected final void initializeValue(V value) {
        this.value = normalize(value);
    }

    public void setValue(V value) {
        if (!allowChanged()) return;
        V normalized = normalize(value);
        if (Objects.equals(this.value, normalized)) return;
        this.value = normalized;
        onChanged();
        notifyChanged();
    }

    protected V normalize(V value) {
        return value;
    }

    public void onChanged() {}

    protected final void notifyChanged() {
        changeListener.run();
    }

    public final void setChangeListener(Runnable listener) {
        changeListener = listener == null ? () -> {} : listener;
    }

    public boolean allowChanged() {
        return true;
    }

    public String getName() {
        return name;
    }

    public final void bindTranslationScope(String scope) {
        String next = Objects.requireNonNull(scope, "scope") + ".setting." + I18nKeys.part(name);
        if (translationKey != null && !translationKey.equals(next)) {
            throw new IllegalStateException("Setting '" + name + "' is already bound to " + translationKey);
        }
        translationKey = next;
    }

    public final String getTranslationKey() {
        return translationKey;
    }

    public final String getTranslationKey(String child) {
        return translationKey == null ? null : translationKey + "." + I18nKeys.part(child);
    }

    public final String getOptionTranslationKey(String option) {
        return translationKey == null ? null : translationKey + ".option." + I18nKeys.part(option);
    }
}
