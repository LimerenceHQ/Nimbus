package cc.nimbus.utils.render.skija.api;

import cc.nimbus.utils.render.skija.internal.resource.FrameResources;
import io.github.humbleui.skija.Canvas;

/** Drawing context passed to HUD and screen submissions. */
public final class SkijaFrame {
    private final Canvas canvas;
    private final float width;
    private final float height;
    private final FrameResources resources = new FrameResources();
    private final SkijaDraw draw;
    private final SkijaText text;
    private final SkijaImages images;
    private final SkijaEffects effects;
    private final SkijaPostProcess postProcess;

    SkijaFrame(Canvas canvas, float width, float height, SkijaRenderer renderer) {
        this.canvas = canvas;
        this.width = width;
        this.height = height;
        this.draw = new SkijaDraw(canvas);
        this.text = new SkijaText(canvas);
        this.images = new SkijaImages();
        this.effects = new SkijaEffects(resources);
        this.postProcess = new SkijaPostProcess(canvas, renderer);
    }

    public Canvas canvas() {
        return canvas;
    }

    public float width() {
        return width;
    }

    public float height() {
        return height;
    }

    public SkijaDraw draw() {
        return draw;
    }

    public SkijaText text() {
        return text;
    }

    public SkijaImages images() {
        return images;
    }

    public SkijaEffects effects() {
        return effects;
    }

    public SkijaPostProcess postProcess() {
        return postProcess;
    }

    void release() {
        resources.close();
    }
}
