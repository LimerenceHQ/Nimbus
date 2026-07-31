package cc.nimbus.utils.render.skija;

import cc.nimbus.utils.render.skija.internal.SkijaBackend;

import java.util.Objects;

/** Submission facade for game HUD content. */
public final class SkijaHud {
    private final SkijaRenderer renderer;

    SkijaHud(SkijaRenderer renderer) {
        this.renderer = renderer;
    }

    public void submit(SkijaFrameRenderer callback) {
        Objects.requireNonNull(callback, "callback");
        SkijaBackend.INSTANCE.addToSurface((canvas, width, height, paint) ->
                callback.render(new SkijaFrame(canvas, width, height)));
    }

    public void render(int targetFps) {
        renderer.renderHud(targetFps);
    }
}
