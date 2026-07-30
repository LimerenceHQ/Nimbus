package cc.nimbus.event.impl.player;
import net.minecraft.world.entity.Entity;
import cc.nimbus.event.Cancelable;
public class EventRayTrace extends Cancelable { public Entity entity; public float yaw,pitch; public EventRayTrace(Entity entity,float yaw,float pitch){this.entity=entity;this.yaw=yaw;this.pitch=pitch;} public Entity getEntity(){return entity;} public void setEntity(Entity value){entity=value;} public float getYaw(){return yaw;} public void setYaw(float value){yaw=value;} public float getPitch(){return pitch;} public void setPitch(float value){pitch=value;} }
