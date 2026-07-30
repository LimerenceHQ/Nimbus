package cc.nimbus.module;

public abstract class ToggleElement {
    protected boolean enabled;
    public void toggle() { setEnabled(!enabled); }
    public void setEnabled(boolean enabled) { if (this.enabled == enabled) return; this.enabled = enabled; if (enabled) onEnable(); else onDisable(); }
    public boolean isEnabled() { return enabled; }
    protected abstract void onEnable();
    protected abstract void onDisable();
}