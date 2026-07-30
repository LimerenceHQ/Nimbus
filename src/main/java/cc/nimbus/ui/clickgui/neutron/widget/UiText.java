package cc.nimbus.ui.clickgui.neutron.widget;

import cc.nimbus.utils.render.skija.text.FontFamily;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.types.Rect;

public final class UiText {
    private UiText() {
    }

    public static void drawLeftY(Canvas canvas, String family, float size, String text, float x, float y, float h, Paint paint) {
        if (text == null) text = "";
        Font font = FontFamily.getCachedFont(family, size);
        Rect b = font.measureText(text);
        float bx = x - b.getLeft();
        float by = y + h / 2f - (b.getTop() + b.getBottom()) / 2f;
        canvas.drawString(text, bx, by, font, paint);
    }

    public static void drawXY(Canvas canvas, String family, float size, String text, float x, float y, float w, float h, Paint paint) {
        if (text == null) text = "";
        Font font = FontFamily.getCachedFont(family, size);
        Rect b = font.measureText(text);
        float bx = x + w / 2f - (b.getLeft() + b.getRight()) / 2f;
        float by = y + h / 2f - (b.getTop() + b.getBottom()) / 2f;
        canvas.drawString(text, bx, by, font, paint);
    }
}
