package cc.nimbus.event.impl.player;

import cc.nimbus.event.Cancelable;

public class EventRotationApplied extends Cancelable {
    private float yaw;
    private float pitch;

    public EventRotationApplied(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public float getYaw() {
        return yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }
}
