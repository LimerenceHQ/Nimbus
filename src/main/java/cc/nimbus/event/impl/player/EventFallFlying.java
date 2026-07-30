package cc.nimbus.event.impl.player;
import cc.nimbus.event.Cancelable;
public class EventFallFlying extends Cancelable { private float pitch; public EventFallFlying(float pitch){this.pitch=pitch;} public float getPitch(){return pitch;} public void setPitch(float pitch){this.pitch=pitch;} }
