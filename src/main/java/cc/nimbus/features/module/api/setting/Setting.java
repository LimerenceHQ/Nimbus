package cc.nimbus.features.module.api.setting;

import java.util.Objects;
import java.util.function.Supplier;

public class Setting<V> {
    private final String name;
    private V value;
    private final Supplier<Boolean> visibility;
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
}
