package cc.nimbus.event.impl.player;

import cc.nimbus.event.Cancelable;
import net.minecraft.world.entity.Entity;

public class EventRayTrace extends Cancelable {
    public Entity entity;
    public float yaw;
    public float pitch;

    public EventRayTrace(Entity entity, float yaw, float pitch) {
        this.entity = entity;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public Entity getEntity() {
        return entity;
    }

    public void setEntity(Entity entity) {
        this.entity = entity;
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
