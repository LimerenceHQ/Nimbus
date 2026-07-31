package cc.nimbus.utils.render.skija.api;

import cc.nimbus.utils.render.skija.internal.text.TextFactory;

public final class SkijaTextMetrics {
    SkijaTextMetrics() {
    }

    public float width(String family, float size, String text) {
        return TextFactory.getTextWidth(family, size, text == null ? "" : text);
    }

    public float height(String family, float size) {
        return TextFactory.getTextHeight(family, size);
    }
}
