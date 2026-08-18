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

    public boolean isPre() {
        return phase == Phase.PRE;
    }

    public boolean isPost() {
        return phase == Phase.POST;
    }

    public Phase getPhase() {
        return phase;
    }
}
