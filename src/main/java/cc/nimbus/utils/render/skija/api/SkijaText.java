package cc.nimbus.utils.render.skija.api;

import cc.nimbus.utils.render.skija.internal.text.TextFactory;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.types.Rect;

public final class SkijaText {
    private final Canvas canvas;

    SkijaText(Canvas canvas) {
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

    public void draw(String family, float size, String text, float x, float y, Paint paint) {
        TextFactory.drawText(canvas, family, size, text, x, y, paint);
    }

    public void drawWithShadow(String family, float size, String text, float x, float y, int color) {
        try (Paint paint = paint(color)) {
            TextFactory.drawTextWithShadow(canvas, family, size, text, x, y, paint);
        }
    }

    public void drawWithShadow(String family, float size, String text, float x, float y, Paint paint) {
        TextFactory.drawTextWithShadow(canvas, family, size, text, x, y, paint);
    }

    public void center(String family, float size, String text, float centerX, float y, int color) {
        try (Paint paint = paint(color)) {
            TextFactory.drawCenteredText(canvas, family, size, text, centerX, y, paint);
        }
    }

    public void center(String family, float size, String text, float centerX, float y, Paint paint) {
        TextFactory.drawCenteredText(canvas, family, size, text, centerX, y, paint);
    }

    public void leftY(String family, float size, String text, float x, float y, float height, Paint paint) {
        if (text == null) text = "";
        var font = cc.nimbus.utils.render.skija.internal.text.FontFamily.getCachedFont(family, size);
        Rect bounds = font.measureText(text);
        canvas.drawString(text, x - bounds.getLeft(),
                y + height / 2f - (bounds.getTop() + bounds.getBottom()) / 2f, font, paint);
    }

    public void box(String family, float size, String text, float x, float y, float width, float height, Paint paint) {
        if (text == null) text = "";
        var font = cc.nimbus.utils.render.skija.internal.text.FontFamily.getCachedFont(family, size);
        Rect bounds = font.measureText(text);
        canvas.drawString(text,
                x + width / 2f - (bounds.getLeft() + bounds.getRight()) / 2f,
                y + height / 2f - (bounds.getTop() + bounds.getBottom()) / 2f,
                font, paint);
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
