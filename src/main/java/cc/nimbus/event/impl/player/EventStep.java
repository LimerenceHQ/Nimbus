package cc.nimbus.event.impl.player;
import cc.nimbus.event.Cancelable;
import cc.nimbus.event.EventType;
public class EventStep extends Cancelable { private double stepHeight; private final EventType eventType; public EventStep(double height,EventType type){stepHeight=height;eventType=type;} public double getStepHeight(){return stepHeight;} public void setStepHeight(double v){stepHeight=v;} public EventType getEventType(){return eventType;} public boolean isPre(){return eventType==EventType.PRE;} }
