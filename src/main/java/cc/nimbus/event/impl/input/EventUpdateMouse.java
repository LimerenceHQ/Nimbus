package cc.nimbus.event.impl.input;
import cc.nimbus.event.Cancelable;
public class EventUpdateMouse extends Cancelable {
    private double yaw, pitch, timeDelta;
    public EventUpdateMouse() {}
    public EventUpdateMouse(double yaw, double pitch, double timeDelta) { set(yaw, pitch, timeDelta); }
    public EventUpdateMouse set(double yaw, double pitch, double timeDelta) { reset(); this.yaw=yaw; this.pitch=pitch; this.timeDelta=timeDelta; return this; }
    public double getYaw() { return yaw; }
    public void setYaw(double yaw) { this.yaw=yaw; }
    public double getPitch() { return pitch; }
    public void setPitch(double pitch) { this.pitch=pitch; }
    public double getTimeDelta() { return timeDelta; }
}
