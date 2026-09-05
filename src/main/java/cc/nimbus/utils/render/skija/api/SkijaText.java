package cc.nimbus.utils.render.skija.api;

import cc.nimbus.utils.render.font.FontSpec;
import cc.nimbus.utils.render.skija.internal.text.FontFamily;
import cc.nimbus.utils.render.skija.internal.text.TextFactory;
import cc.nimbus.utils.render.skija.internal.text.TextLayout;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;

public final class SkijaText {
    private final Canvas canvas;

    SkijaText(Canvas canvas) {
        this.canvas = canvas;
    }

    public float width(FontSpec font, float size, String text) {
        return TextFactory.getTextWidth(font, size, normalize(text));
    }

    public float height(FontSpec font, float size) {
        return TextFactory.getTextHeight(font, size);
    }

    public float height(FontSpec font, float size, String text) {
        return TextFactory.getTextHeight(font, size, normalize(text));
    }

    public float visualLeft(FontSpec font, float size, String text) {
        return TextFactory.getTextVisualLeft(font, size, normalize(text));
    }

    public float visualRight(FontSpec font, float size, String text) {
        return TextFactory.getTextVisualRight(font, size, normalize(text));
    }

    public float visualCenterY(FontSpec font, float size, String text) {
        return TextFactory.getTextVisualCenterY(font, size, normalize(text));
    }

    public float baselineFromTop(FontSpec font, float size, String text, float top) {
        return top + TextFactory.getTextBaseline(font, size, normalize(text));
    }

    public void draw(FontSpec font, float size, String text, float x, float y, int color) {
        try (Paint paint = paint(color)) {
            TextFactory.drawText(canvas, font, size, normalize(text), x, y, paint);
        }
    }

    public void draw(FontSpec font, float size, String text, float x, float y, Paint paint) {
        TextFactory.drawText(canvas, font, size, normalize(text), x, y, paint);
    }

    public void drawAtBaseline(FontSpec font, float size, String text,
                               float x, float baseline, Paint paint) {
        TextFactory.drawTextAtBaseline(canvas, font, size, normalize(text), x, baseline, paint);
    }

    public void drawWithShadow(FontSpec font, float size, String text, float x, float y, int color) {
        try (Paint paint = paint(color)) {
            TextFactory.drawTextWithShadow(canvas, font, size, normalize(text), x, y, paint);
        }
    }

    public void drawWithShadow(FontSpec font, float size, String text, float x, float y, Paint paint) {
        TextFactory.drawTextWithShadow(canvas, font, size, normalize(text), x, y, paint);
    }

    public void center(FontSpec font, float size, String text, float centerX, float y, int color) {
        try (Paint paint = paint(color)) {
            TextFactory.drawCenteredText(canvas, font, size, normalize(text), centerX, y, paint);
        }
    }

    public void center(FontSpec font, float size, String text, float centerX, float y, Paint paint) {
        TextFactory.drawCenteredText(canvas, font, size, normalize(text), centerX, y, paint);
    }

    public void leftY(FontSpec fontSpec, float size, String text, float x, float y, float height, Paint paint) {
        text = normalize(text);
        var font = FontFamily.getCachedFont(fontSpec, size);
        canvas.drawString(text, x, TextLayout.baselineInBox(font, y, height), font, paint);
    }

    public void box(FontSpec fontSpec, float size, String text, float x, float y, float width, float height, Paint paint) {
        text = normalize(text);
        var font = FontFamily.getCachedFont(fontSpec, size);
        canvas.drawString(text,
                x + (width - font.measureTextWidth(text)) / 2f,
                TextLayout.baselineInBox(font, y, height),
                font, paint);
    }

    public void centerWithShadow(FontSpec font, float size, String text, float centerX, float y, int color) {
        try (Paint paint = paint(color)) {
            TextFactory.drawCenteredTextWithShadow(canvas, font, size, normalize(text), centerX, y, paint);
        }
    }

    private static Paint paint(int color) {
        return new Paint().setAntiAlias(true).setColor(color);
    }

    private static String normalize(String text) {
        return text == null ? "" : text;
    }

}
