package cc.nimbus.utils.render.skija.internal.text;

import io.github.humbleui.skija.*;
import org.jetbrains.annotations.ApiStatus;

import java.util.LinkedHashMap;
import java.util.Map;

public final class TextFactory {
    private record FontTextKey(String familyName, float size, String text) {}

    private static final int MAX_MEASUREMENTS = 4096;
    private static final Map<FontTextKey, Float> TEXT_WIDTH_CACHE = boundedCache();
    private static Paint shadowPaint;
    private static boolean closed;

    private TextFactory() {
    }

    public static synchronized float getTextWidth(String familyName, float size, String text) {
        ensureOpen();
        text = normalize(text);
        FontTextKey key = new FontTextKey(familyName, size, text);
        return TEXT_WIDTH_CACHE.computeIfAbsent(key, k -> {
            Font font = FontFamily.getCachedFont(familyName, size);
            return font.measureTextWidth(k.text());
        });
    }

    public static synchronized float getTextHeight(String familyName, float size) {
        ensureOpen();
        return FontFamily.getCachedFont(familyName, size).getMetrics().getHeight();
    }

    @ApiStatus.Internal
    private static void drawGlyphsSplice(Canvas canvas, String familyName, float size, String text, float x, float y, Paint paint) {
        ensureOpen();
        text = normalize(text);
        Font font = FontFamily.getCachedFont(familyName, size);
        canvas.drawString(text, x, TextLayout.baselineFromTop(font, y), font, paint);
    }

    public static void drawText(Canvas canvas, String family, float size, String text, float x, float y, Paint paint) {
        drawGlyphsSplice(canvas, family, size, text, x, y, paint);
    }

    public static void drawCenteredText(Canvas canvas, String family, float size, String text, float x, float y, Paint paint) {

        float center = getTextWidth(family, size, text) / 2;
        drawGlyphsSplice(canvas, family, size, text, x - center, y, paint);
    }

    public static void drawTextWithShadow(Canvas canvas, String family, float size, String text, float x, float y, Paint paint) {
        Paint shadow = shadowPaint();
        int shadowHex = TextMth.calculateShadowColor(paint.getColor());
        if (shadow.getColor() != shadowHex) {
            shadow.setColor(shadowHex);
        }

        drawGlyphsSplice(canvas, family, size, text, x + 1, y + 1, shadow);
        drawGlyphsSplice(canvas, family, size, text, x, y, paint);
    }

    public static void drawCenteredTextWithShadow(Canvas canvas, String family, float size, String text, float x, float y, Paint paint) {
        text = normalize(text);
        Paint shadow = shadowPaint();
        int shadowHex = TextMth.calculateShadowColor(paint.getColor());
        if (shadow.getColor() != shadowHex) {
            shadow.setColor(shadowHex);
        }

        float center = getTextWidth(family, size, text) / 2;
        drawGlyphsSplice(canvas, family, size, text, x - center + 1, y + 1, shadow);
        drawGlyphsSplice(canvas, family, size, text, x - center, y, paint);
    }

    private static Map<FontTextKey, Float> boundedCache() {
        return new LinkedHashMap<>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<FontTextKey, Float> eldest) {
                return size() > MAX_MEASUREMENTS;
            }
        };
    }

    public static synchronized void close() {
        if (closed) return;
        closed = true;
        TEXT_WIDTH_CACHE.clear();
        if (shadowPaint != null && !shadowPaint.isClosed()) shadowPaint.close();
        shadowPaint = null;
    }

    private static synchronized Paint shadowPaint() {
        ensureOpen();
        if (shadowPaint == null || shadowPaint.isClosed()) {
            shadowPaint = new Paint().setAntiAlias(true);
        }
        return shadowPaint;
    }

    private static void ensureOpen() {
        if (closed) throw new IllegalStateException("Text subsystem is closed");
    }

    private static String normalize(String text) {
        return text == null ? "" : text;
    }
}
