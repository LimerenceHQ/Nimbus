package cc.nimbus.utils.render.skija.api;

import cc.nimbus.utils.render.skija.internal.backend.SkijaBackend;

import java.util.Objects;

public final class SkijaHud {
    private final SkijaRenderer renderer;

    SkijaHud(SkijaRenderer renderer) {
        this.renderer = renderer;
    }

    public void submit(SkijaFrameRenderer callback) {
        Objects.requireNonNull(callback, "callback");
        SkijaBackend.INSTANCE.addToSurface((canvas, width, height, paint) ->
                callback.render(new SkijaFrame(canvas, width, height, renderer)));
    }

    public void render(int targetFps) {
        renderer.renderHud(targetFps);
    }
}
