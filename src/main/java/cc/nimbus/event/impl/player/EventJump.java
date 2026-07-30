package cc.nimbus.event.impl.player;
import cc.nimbus.event.Cancelable;
public class EventJump extends Cancelable { private float yaw; public EventJump(float yaw){this.yaw=yaw;} public float getYaw(){return yaw;} public void setYaw(float yaw){this.yaw=yaw;} }
