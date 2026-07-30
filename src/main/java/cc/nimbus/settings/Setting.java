package cc.nimbus.settings;

import cc.nimbus.config.ChangeTracker;

import java.util.Objects;
import java.util.function.Supplier;

public class Setting<V> {
    private final String name;
    private V value;
    private final Supplier<Boolean> visibility;

    public Setting(String name, V value, Supplier<Boolean> visibility) {
        this.name = Objects.requireNonNull(name, "name");
        this.value = value;
        this.visibility = visibility == null ? () -> true : visibility;
    }

    public boolean isVisitable() {
        return isHidden();
    }

    public boolean isHidden() {
        return !visibility.get();
    }

    public void invalidateVisibilityCache() {}

    public V getValue() {
        return value;
    }

    public void change(V value) {
        this.value = normalize(value);
    }

    public void setValue(V value) {
        if (!allowChanged()) return;
        V normalized = normalize(value);
        if (Objects.equals(this.value, normalized)) return;
        this.value = normalized;
        onChanged();
        ChangeTracker.changed();
    }

    protected V normalize(V value) {
        return value;
    }

    public void onChanged() {}

    public boolean allowChanged() {
        return true;
    }

    public String getName() {
        return name;
    }
}
