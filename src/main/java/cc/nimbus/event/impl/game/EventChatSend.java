package cc.nimbus.event.impl.game;

import cc.nimbus.event.Cancelable;

public class EventChatSend extends Cancelable {
    private String message;
    public EventChatSend() {}
    public EventChatSend(String message) { this.message = message; }
    public EventChatSend set(String message) { reset(); this.message = message; return this; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
