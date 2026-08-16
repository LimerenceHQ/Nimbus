package cc.nimbus.event.impl.input;

import cc.nimbus.event.Cancelable;

public class EventKeyPress extends Cancelable {
    public static int downKey = -1;

    private int key;
    private int action;
    private int modifiers;

    public EventKeyPress() {
    }

    public EventKeyPress(int key) {
        this(key, 1, 0);
    }

    public EventKeyPress(int key, int action, int modifiers) {
        this.key = key;
        this.action = action;
        this.modifiers = modifiers;
    }

    public EventKeyPress set(int key) {
        return set(key, 1, 0);
    }

    public EventKeyPress set(int key, int action, int modifiers) {
        reset();
        this.key = key;
        this.action = action;
        this.modifiers = modifiers;
        return this;
    }

    public int getKey() {
        return key;
    }

    public void setKey(int key) {
        this.key = key;
    }

    public int getAction() {
        return action;
    }

    public int getModifiers() {
        return modifiers;
    }
}
