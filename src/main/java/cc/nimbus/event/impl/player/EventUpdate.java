package cc.nimbus.event.impl.player;

import cc.nimbus.event.Cancelable;
import cc.nimbus.event.EventType;

public class EventUpdate extends Cancelable {
    private final EventType eventState;

    public EventUpdate(EventType eventState) {
        this.eventState = eventState;
    }

    public boolean isPre() {
        return eventState == EventType.PRE;
    }

    public boolean isPost() {
        return eventState == EventType.POST;
    }
}
