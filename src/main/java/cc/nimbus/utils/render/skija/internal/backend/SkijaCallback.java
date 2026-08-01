package cc.nimbus.utils.render.skija.internal.backend;

import io.github.humbleui.skija.Canvas;

@FunctionalInterface
public interface SkijaCallback {
    void call(Canvas canvas, float width, float height);
}
