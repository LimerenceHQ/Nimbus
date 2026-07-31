package cc.nimbus.ui.clickgui.neutron.widget;

import cc.nimbus.utils.render.skija.api.SkijaFrame;
import io.github.humbleui.skija.Paint;

public final class UiText {
    private UiText() {
    }

    public static void drawLeftY(SkijaFrame frame, String family, float size, String text, float x, float y, float h, Paint paint) {
        frame.text().leftY(family, size, text, x, y, h, paint);
    }

    public static void drawXY(SkijaFrame frame, String family, float size, String text, float x, float y, float w, float h, Paint paint) {
        frame.text().box(family, size, text, x, y, w, h, paint);
    }

    public static float width(SkijaFrame frame, String family, float size, String text) {
        return frame.text().width(family, size, text);
    }

    public static void draw(SkijaFrame frame, String family, float size, String text, float x, float y, Paint paint) {
        frame.text().draw(family, size, text, x, y, paint);
    }
}
