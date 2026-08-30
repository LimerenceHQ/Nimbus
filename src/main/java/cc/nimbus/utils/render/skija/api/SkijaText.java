package cc.nimbus.utils.render.skija.api;

import cc.nimbus.utils.render.skija.internal.text.FontFamily;
import cc.nimbus.utils.render.skija.internal.text.TextFactory;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;

public final class SkijaText {
    private final Canvas canvas;

    SkijaText(Canvas canvas) {
        this.canvas = canvas;
    }

    public float width(String family, float size, String text) {
        return TextFactory.getTextWidth(family, size, normalize(text));
    }

    public float height(String family, float size) {
        return TextFactory.getTextHeight(family, size);
    }

    public float height(String family, float size, String text) {
        return TextFactory.getTextHeight(family, size, normalize(text));
    }

    public float visualLeft(String family, float size, String text) {
        return TextFactory.getTextVisualLeft(family, size, normalize(text));
    }

    public float visualRight(String family, float size, String text) {
        return TextFactory.getTextVisualRight(family, size, normalize(text));
    }

    public float visualCenterY(String family, float size, String text) {
        return TextFactory.getTextVisualCenterY(family, size, normalize(text));
    }

    public float baselineFromTop(String family, float size, String text, float top) {
        return top + TextFactory.getTextBaseline(family, size, normalize(text));
    }

    public void draw(String family, float size, String text, float x, float y, int color) {
        try (Paint paint = paint(color)) {
            TextFactory.drawText(canvas, family, size, normalize(text), x, y, paint);
        }
    }

    public void draw(String family, float size, String text, float x, float y, Paint paint) {
        TextFactory.drawText(canvas, family, size, normalize(text), x, y, paint);
    }

    public void drawAtBaseline(String family, float size, String text,
                               float x, float baseline, Paint paint) {
        TextFactory.drawTextAtBaseline(canvas, family, size, normalize(text), x, baseline, paint);
    }

    public void drawWithShadow(String family, float size, String text, float x, float y, int color) {
        try (Paint paint = paint(color)) {
            TextFactory.drawTextWithShadow(canvas, family, size, normalize(text), x, y, paint);
        }
    }

    public void drawWithShadow(String family, float size, String text, float x, float y, Paint paint) {
        TextFactory.drawTextWithShadow(canvas, family, size, normalize(text), x, y, paint);
    }

    public void center(String family, float size, String text, float centerX, float y, int color) {
        try (Paint paint = paint(color)) {
            TextFactory.drawCenteredText(canvas, family, size, normalize(text), centerX, y, paint);
        }
    }

    public void center(String family, float size, String text, float centerX, float y, Paint paint) {
        TextFactory.drawCenteredText(canvas, family, size, normalize(text), centerX, y, paint);
    }

    public void leftY(String family, float size, String text, float x, float y, float height, Paint paint) {
        text = normalize(text);
        float baseline = y + TextFactory.getTextBaseline(family, size, text)
                + (height - TextFactory.getTextHeight(family, size, text)) * 0.5f;
        TextFactory.drawTextAtBaseline(canvas, family, size, text, x, baseline, paint);
    }

    public void box(String family, float size, String text, float x, float y, float width, float height, Paint paint) {
        text = normalize(text);
        float textWidth = TextFactory.getTextWidth(family, size, text);
        float baseline = y + TextFactory.getTextBaseline(family, size, text)
                + (height - TextFactory.getTextHeight(family, size, text)) * 0.5f;
        TextFactory.drawTextAtBaseline(canvas, family, size, text,
                x + (width - textWidth) / 2f, baseline, paint);
    }

    public void centerWithShadow(String family, float size, String text, float centerX, float y, int color) {
        try (Paint paint = paint(color)) {
            TextFactory.drawCenteredTextWithShadow(canvas, family, size, normalize(text), centerX, y, paint);
        }
    }

    private static Paint paint(int color) {
        return new Paint().setAntiAlias(true).setColor(color);
    }

    private static String normalize(String text) {
        return text == null ? "" : text;
    }

}
