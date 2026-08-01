package cc.nimbus.utils.render.skija.api;

import cc.nimbus.utils.render.skija.internal.text.TextFactory;

public final class SkijaTextMetrics {
    private final SkijaRenderer renderer;

    SkijaTextMetrics(SkijaRenderer renderer) {
        this.renderer = renderer;
    }

    public float width(String family, float size, String text) {
        renderer.requireReady();
        return TextFactory.getTextWidth(family, size, text == null ? "" : text);
    }

    public float height(String family, float size) {
        renderer.requireReady();
        return TextFactory.getTextHeight(family, size);
    }
}
