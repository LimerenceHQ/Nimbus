package cc.nimbus.event.impl.player;
import cc.nimbus.event.Cancelable;
public class EventRotationApplied extends Cancelable { private float yaw,pitch; public EventRotationApplied(float yaw,float pitch){this.yaw=yaw;this.pitch=pitch;} public float getYaw(){return yaw;} public void setYaw(float v){yaw=v;} public float getPitch(){return pitch;} public void setPitch(float v){pitch=v;} }
