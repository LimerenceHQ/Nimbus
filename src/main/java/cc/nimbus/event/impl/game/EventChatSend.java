package cc.nimbus.event.impl.game;

import cc.nimbus.event.Cancelable;

public final class EventChatSend extends Cancelable {
    private final String message;

    public EventChatSend(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
