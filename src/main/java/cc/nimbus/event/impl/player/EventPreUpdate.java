package cc.nimbus.event.impl.player;

import cc.nimbus.event.Cancelable;
import cc.nimbus.event.EventType;

public class EventPreUpdate extends Cancelable {
    private final EventType type;

    public EventPreUpdate() {
        this(EventType.PRE);
    }

    public EventPreUpdate(EventType type) {
        this.type = type;
    }

    public EventType getType() {
        return type;
    }

    public boolean isPre() {
        return type == EventType.PRE;
    }

    public boolean isPost() {
        return type == EventType.POST;
    }
}
