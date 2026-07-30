package cc.nimbus.event.impl.game;
import cc.nimbus.event.Cancelable;
import cc.nimbus.event.EventType;
public class EventRunTicks extends Cancelable {
    private final EventType type;
    public EventRunTicks(EventType type) { this.type = type; }
    public EventType getType() { return type; }
    public boolean isPre() { return type == EventType.PRE; }
    public boolean isPost() { return type == EventType.POST; }
    public boolean isMiddle() { return type == EventType.MIDDLE; }
}
