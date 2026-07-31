package cc.nimbus.utils.render.skija;

import cc.nimbus.utils.render.skija.draw.SkijaDraw;
import cc.nimbus.utils.render.skija.text.SkijaText;
import io.github.humbleui.skija.Canvas;

/** Drawing context passed to HUD and screen submissions. */
public final class SkijaFrame {
    private final Canvas canvas;
    private final float width;
    private final float height;
    private final SkijaDraw draw;
    private final SkijaText text;

    SkijaFrame(Canvas canvas, float width, float height) {
        this.canvas = canvas;
        this.width = width;
        this.height = height;
        this.draw = new SkijaDraw(canvas);
        this.text = new SkijaText(canvas);
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
}
