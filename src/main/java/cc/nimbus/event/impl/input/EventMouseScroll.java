package cc.nimbus.event.impl.input;

import cc.nimbus.event.Cancelable;

public class EventMouseScroll extends Cancelable {
    private final double x;
    private final double y;

    public EventMouseScroll(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }
}
