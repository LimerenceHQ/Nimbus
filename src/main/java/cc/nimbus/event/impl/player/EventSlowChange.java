package cc.nimbus.event.impl.player;
import cc.nimbus.event.Cancelable;
public class EventSlowChange extends Cancelable { private float forward,sideways; private boolean sprint; public EventSlowChange(float forward,float sideways,boolean sprint){this.forward=forward;this.sideways=sideways;this.sprint=sprint;} public float getForward(){return forward;} public void setForward(float v){forward=v;} public float getSideways(){return sideways;} public void setSideways(float v){sideways=v;} public boolean isSprint(){return sprint;} public void setSprint(boolean v){sprint=v;} }
