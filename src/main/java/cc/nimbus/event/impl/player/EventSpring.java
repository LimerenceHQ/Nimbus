package cc.nimbus.event.impl.player;
import cc.nimbus.event.Cancelable;
public class EventSpring extends Cancelable { private boolean spring; public EventSpring(boolean spring){this.spring=spring;} public boolean isSpring(){return spring;} public void setSpring(boolean v){spring=v;} }
