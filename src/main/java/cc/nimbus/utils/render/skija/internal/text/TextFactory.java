package cc.nimbus.utils.render.skija.internal.text;

import io.github.humbleui.skija.*;
import org.jetbrains.annotations.ApiStatus;

import java.util.LinkedHashMap;
import java.util.Map;

public final class TextFactory {
    private record FontTextKey(String familyName, float size, String text) {}

    private static final int MAX_MEASUREMENTS = 4096;
    private static final Map<FontTextKey, Float> TEXT_WIDTH_CACHE = boundedCache();
    private static final Paint SHADOW_PAINT = new Paint().setAntiAlias(true);

    private TextFactory() {
    }

    public static synchronized float getTextWidth(String familyName, float size, String text) {
        FontTextKey key = new FontTextKey(familyName, size, text);
        return TEXT_WIDTH_CACHE.computeIfAbsent(key, k -> {
            Font font = FontFamily.getCachedFont(familyName, size);
            return font.measureTextWidth(text);
        });
    }

    public static synchronized float getTextHeight(String familyName, float size, String text) {
        return FontFamily.getCachedFont(familyName, size).getMetrics().getHeight();
    }

    public static float getTextHeight(String familyName, float size) {
        return getTextHeight(familyName, size, "");
    }

    @ApiStatus.Internal
    private static void drawGlyphsSplice(Canvas canvas, String familyName, float size, String text, float x, float y, Paint paint) {
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
        int shadowHex = TextMth.calculateShadowColor(paint.getColor());
        if (SHADOW_PAINT.getColor() != shadowHex) {
            SHADOW_PAINT.setColor(shadowHex);
        }

        drawGlyphsSplice(canvas, family, size, text, x + 1, y + 1, SHADOW_PAINT);
        drawGlyphsSplice(canvas, family, size, text, x, y, paint);
    }

    public static void drawCenteredTextWithShadow(Canvas canvas, String family, float size, String text, float x, float y, Paint paint) {
        int shadowHex = TextMth.calculateShadowColor(paint.getColor());
        if (SHADOW_PAINT.getColor() != shadowHex) {
            SHADOW_PAINT.setColor(shadowHex);
        }

        float center = getTextWidth(family, size, text) / 2;
        drawGlyphsSplice(canvas, family, size, text, x - center + 1, y + 1, SHADOW_PAINT);
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
