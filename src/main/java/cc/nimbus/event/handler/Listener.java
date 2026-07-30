package cc.nimbus.event.handler;

import cc.nimbus.event.Cancelable;

@FunctionalInterface
public interface Listener<T extends Cancelable> {
    void handle(T event);
}
