package cc.nimbus.utils.render.skija.text;

import cc.nimbus.utils.render.skija.text.factory.TextFactory;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;

public final class SkijaText {
    private final Canvas canvas;

    public SkijaText(Canvas canvas) {
        this.canvas = canvas;
    }

    public float width(String family, float size, String text) {
        return TextFactory.getTextWidth(family, size, text);
    }

    public float height(String family, float size) {
        return TextFactory.getTextHeight(family, size);
    }

    public void draw(String family, float size, String text, float x, float y, int color) {
        try (Paint paint = paint(color)) {
            TextFactory.drawText(canvas, family, size, text, x, y, paint);
        }
    }

    public void drawWithShadow(String family, float size, String text, float x, float y, int color) {
        try (Paint paint = paint(color)) {
            TextFactory.drawTextWithShadow(canvas, family, size, text, x, y, paint);
        }
    }

    public void center(String family, float size, String text, float centerX, float y, int color) {
        try (Paint paint = paint(color)) {
            TextFactory.drawCenteredText(canvas, family, size, text, centerX, y, paint);
        }
    }

    public void centerWithShadow(String family, float size, String text, float centerX, float y, int color) {
        try (Paint paint = paint(color)) {
            TextFactory.drawCenteredTextWithShadow(canvas, family, size, text, centerX, y, paint);
        }
    }

    private static Paint paint(int color) {
        return new Paint().setAntiAlias(true).setColor(color);
    }
}
