package cc.nimbus.utils.render.skija.internal.text;

import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontMetrics;

public final class TextLayout {
    private TextLayout() {
    }

    public static float baselineFromTop(Font font, float top) {
        return top - font.getMetrics().getAscent();
    }

    public static float baselineInBox(Font font, float y, float height) {
        FontMetrics metrics = font.getMetrics();
        return y + (height - metrics.getHeight()) / 2f - metrics.getAscent();
    }
}
