package cc.nimbus.event.impl.game;

import cc.nimbus.event.Cancelable;

public class EventTick extends Cancelable {
    public enum Phase {
        PRE,
        POST
    }

    private final Phase phase;

    public EventTick(Phase phase) {
        this.phase = phase;
    }

    public Phase getPhase() {
        return phase;
    }
}
