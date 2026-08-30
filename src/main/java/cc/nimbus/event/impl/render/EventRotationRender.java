package cc.nimbus.event.impl.render;


import cc.nimbus.event.Cancelable;
import net.minecraft.world.entity.Entity;

public class EventRotationRender extends Cancelable {
   public static Entity currentEntity;
   private float yaw;
   private float lastYaw;
   private float pitch;
   private float lastPitch;


   public EventRotationRender(float yaw, float lastYaw, float pitch, float lastPitch) {
      this.yaw = yaw;
      this.lastYaw = lastYaw;
      this.pitch = pitch;
      this.lastPitch = lastPitch;
   }

    public float getLastYaw() {
        return lastYaw;
    }

    public float getYaw() {
        return yaw;
    }

   public float getLastPitch() {
      return lastPitch;
   }

    public float getPitch() {
      return pitch;
    }

   public void setLastYaw(float yaw) {
      this.lastYaw = yaw;
   }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public void setPitch(float pitch) {
       this.pitch = pitch;
    }

    public void setLastPitch(float pitch) {
        this.lastPitch = pitch;
    }
}
