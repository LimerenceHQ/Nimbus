package cc.nimbus.event.impl.input;

import cc.nimbus.event.Cancelable;

public class EventMouseButton extends Cancelable {
    private final int button, action, modifiers;
    public EventMouseButton(int button, int action, int modifiers) { this.button = button; this.action = action; this.modifiers = modifiers; }
    public int getButton() { return button; }
    public int getAction() { return action; }
    public int getModifiers() { return modifiers; }
}
