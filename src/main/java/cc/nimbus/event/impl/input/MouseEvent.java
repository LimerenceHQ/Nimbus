package cc.nimbus.event.impl.input;
import cc.nimbus.event.Cancelable;
public class MouseEvent extends Cancelable {
    private int button, action, mods;
    public MouseEvent() {}
    public MouseEvent(int button, int action, int mods) { set(button, action, mods); }
    public MouseEvent set(int button, int action, int mods) { reset(); this.button=button; this.action=action; this.mods=mods; return this; }
    public int getButton() { return button; }
    public int getAction() { return action; }
    public int getMods() { return mods; }
}
