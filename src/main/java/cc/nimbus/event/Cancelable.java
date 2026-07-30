package cc.nimbus.event;

public abstract class Cancelable {
    private boolean cancel;
    public void cancelEvent() { cancel = true; }
    public void setCancel(boolean cancel) { this.cancel = cancel; }
    public boolean isCancel() { return cancel; }
    public void reset() { cancel = false; }
}
