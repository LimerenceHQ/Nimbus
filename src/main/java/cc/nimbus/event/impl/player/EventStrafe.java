package cc.nimbus.event.impl.player;
import cc.nimbus.event.Cancelable;
public class EventStrafe extends Cancelable { private float yaw; public EventStrafe(float yaw){this.yaw=yaw;} public float getYaw(){return yaw;} public void setYaw(float v){yaw=v;} }
