package cc.nimbus.utils.render.skija.api;

import cc.nimbus.utils.render.font.FontSpec;
import cc.nimbus.utils.render.skija.internal.text.TextFactory;

public final class SkijaTextMetrics {
    private final SkijaRenderer renderer;

    SkijaTextMetrics(SkijaRenderer renderer) {
        this.renderer = renderer;
    }

    public float width(FontSpec font, float size, String text) {
        renderer.requireReady();
        return TextFactory.getTextWidth(font, size, text == null ? "" : text);
    }

    public float height(FontSpec font, float size) {
        renderer.requireReady();
        return TextFactory.getTextHeight(font, size);
    }

    public float visualLeft(FontSpec font, float size, String text) {
        renderer.requireReady();
        return TextFactory.getTextVisualLeft(font, size, text == null ? "" : text);
    }

    public float visualRight(FontSpec font, float size, String text) {
        renderer.requireReady();
        return TextFactory.getTextVisualRight(font, size, text == null ? "" : text);
    }

    public float visualCenterY(FontSpec font, float size, String text) {
        renderer.requireReady();
        return TextFactory.getTextVisualCenterY(font, size, text == null ? "" : text);
    }
}
