package cc.nimbus.event.impl.player;

import cc.nimbus.event.Cancelable;
import cc.nimbus.event.EventType;

public class EventMotion extends Cancelable {
    private double posX;
    private double posY;
    private double posZ;
    private float yaw;
    private float pitch;
    private float cameraYaw;
    private float cameraPitch;
    private boolean ground;
    private EventType type;

    public EventMotion() {
    }

    public EventMotion(double x, double y, double z, float yaw, float pitch,
                       float cameraYaw, float cameraPitch, boolean ground, EventType type) {
        set(x, y, z, yaw, pitch, cameraYaw, cameraPitch, ground, type);
    }

    public EventMotion set(double x, double y, double z, float yaw, float pitch,
                           float cameraYaw, float cameraPitch, boolean ground, EventType type) {
        reset();
        posX = x;
        posY = y;
        posZ = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.cameraYaw = cameraYaw;
        this.cameraPitch = cameraPitch;
        this.ground = ground;
        this.type = type;
        return this;
    }

    public double getPosX() {
        return posX;
    }

    public void setPosX(double posX) {
        this.posX = posX;
    }

    public double getPosY() {
        return posY;
    }

    public void setPosY(double posY) {
        this.posY = posY;
    }

    public double getPosZ() {
        return posZ;
    }

    public void setPosZ(double posZ) {
        this.posZ = posZ;
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

    public float getCameraYaw() {
        return cameraYaw;
    }

    public float getCameraPitch() {
        return cameraPitch;
    }

    public void setRotation(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public boolean isGround() {
        return ground;
    }

    public void setGround(boolean ground) {
        this.ground = ground;
    }

    public EventType getType() {
        return type;
    }

    public boolean isPre() {
        return type == EventType.PRE;
    }

    public boolean isPost() {
        return type == EventType.POST;
    }
}
