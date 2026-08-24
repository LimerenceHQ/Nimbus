package cc.nimbus.event.impl.player;

import cc.nimbus.event.Cancelable;
import cc.nimbus.event.EventType;

public class EventMotion extends Cancelable {
    private final EventType eventState;
    private float yaw;
    private float pitch;
    private double x;
    private double y;
    private double z;
    private boolean onGround;

    public EventMotion(EventType eventState, float yaw, float pitch, double x, double y, double z, boolean onGround) {
        this.eventState = eventState;
        this.yaw = yaw;
        this.pitch = pitch;
        this.x = x;
        this.y = y;
        this.z = z;
        this.onGround = onGround;
    }

    public double getPosX() {
        return x;
    }

    public void setPosX(double posX) {
        this.x = posX;
    }

    public double getPosY() {
        return y;
    }

    public void setPosY(double posY) {
        this.y = posY;
    }

    public double getPosZ() {
        return z;
    }

    public void setPosZ(double posZ) {
        this.z = posZ;
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

    public void setRotation(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public boolean isGround() {
        return onGround;
    }

    public void setGround(boolean ground) {
        this.onGround = ground;
    }

    public EventType getType() {
        return eventState;
    }

    public boolean isPre() {
        return eventState == EventType.PRE;
    }

    public boolean isPost() {
        return eventState == EventType.POST;
    }
}
