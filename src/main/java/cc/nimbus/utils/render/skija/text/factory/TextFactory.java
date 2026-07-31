package cc.nimbus.utils.render.skija.text.factory;

import cc.nimbus.utils.render.skija.paint.SkColor;
import cc.nimbus.utils.render.skija.text.FontFamily;
import cc.nimbus.utils.render.skija.text.utils.TextMth;
import io.github.humbleui.skija.*;
import org.jetbrains.annotations.ApiStatus;

import java.util.LinkedHashMap;
import java.util.Map;

@SuppressWarnings("unused")
public class TextFactory {
    private static final @SuppressWarnings("all") String MEASURE_TEXT = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    private record FontTextKey(String familyName, float size, String text) {}

    private static final int MAX_MEASUREMENTS = 4096;
    private static final Map<FontTextKey, Float> TEXT_WIDTH_CACHE = boundedCache();
    private static final Map<FontTextKey, Float> TEXT_HEIGHT_CACHE = boundedCache();

    private static final Paint shadowPaint = new Paint().setAntiAlias(true);

    public static synchronized float getTextWidth(String familyName, float size, String text) {
        FontTextKey key = new FontTextKey(familyName, size, text);
        return TEXT_WIDTH_CACHE.computeIfAbsent(key, k -> {
            Font font = FontFamily.getCachedFont(familyName, size);
            return font.measureTextWidth(text);
        });
    }

    public static synchronized float getTextHeight(String familyName, float size, String text) {
        FontTextKey key = new FontTextKey(familyName, size, text);
        return TEXT_HEIGHT_CACHE.computeIfAbsent(key, k -> {
            Font font = FontFamily.getCachedFont(familyName, size);
            return font.measureText(text).getHeight();
        });
    }

    public static float getTextHeight(String familyName, float size) {
        return getTextHeight(familyName, size, MEASURE_TEXT);
    }

    @ApiStatus.Internal
    private static void drawGlyphsSplice(Canvas canvas, String familyName, float size, String text, float x, float y, Paint paint) {
        Font font = FontFamily.getCachedFont(familyName, size);
        float rY = y + getTextHeight(familyName, size) / 1.5F;
        canvas.drawString(text, x, rY, font, paint);
    }

    @ApiStatus.Internal
    private static void drawGlyphsSplice(Canvas canvas, String familyName, float size, String text, float x, float y, SkColor color) {
        drawGlyphsSplice(canvas, familyName, size, text, x, y, color.getPaint());
    }

    public static void drawText(Canvas canvas, String family, float size, String text, float x, float y, SkColor color) {
        drawText(canvas, family, size, text, x, y, color.getPaint());
    }

    public static void drawText(Canvas canvas, String family, float size, String text, float x, float y, Paint paint) {
        drawGlyphsSplice(canvas, family, size, text, x, y, paint);
    }

    public static void drawCenteredText(Canvas canvas, String family, float size, String text, float x, float y, SkColor color) {
        drawCenteredText(canvas, family, size, text, x, y, color.getPaint());
    }

    public static void drawCenteredText(Canvas canvas, String family, float size, String text, float x, float y, Paint paint) {

        float center = getTextWidth(family, size, text) / 2;
        drawGlyphsSplice(canvas, family, size, text, x - center, y, paint);
    }

    public static void drawTextWithShadow(Canvas canvas, String family, float size, String text, float x, float y, SkColor color) {
        drawTextWithShadow(canvas, family, size, text, x, y, color.getPaint());
    }

    public static void drawTextWithShadow(Canvas canvas, String family, float size, String text, float x, float y, Paint paint) {
        int shadowHex = TextMth.calculateShadowColor(paint.getColor());
        if (shadowPaint.getColor() != shadowHex) {
            shadowPaint.setColor(shadowHex);
        }

        drawGlyphsSplice(canvas, family, size, text, x + 1, y + 1, shadowPaint);
        drawGlyphsSplice(canvas, family, size, text, x, y, paint);
    }

    public static void drawCenteredTextWithShadow(Canvas canvas, String family, float size, String text, float x, float y, SkColor color) {
        drawCenteredTextWithShadow(canvas, family, size, text, x, y, color.getPaint());
    }

    public static void drawCenteredTextWithShadow(Canvas canvas, String family, float size, String text, float x, float y, Paint paint) {
        int shadowHex = TextMth.calculateShadowColor(paint.getColor());
        if (shadowPaint.getColor() != shadowHex) {
            shadowPaint.setColor(shadowHex);
        }

        float center = getTextWidth(family, size, text) / 2;
        drawGlyphsSplice(canvas, family, size, text, x - center + 1, y + 1, shadowPaint);
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
}
