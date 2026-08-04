package cc.nimbus.utils.render.skija.internal.text;

import io.github.humbleui.skija.*;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TextFactory {
    private record FontTextKey(String familyName, float size, String text) {}
    private record TextRun(String family, String text, float x) {}
    private record TextLayoutPlan(List<TextRun> runs, float width, float height, float baseline) {}

    private static final int MAX_MEASUREMENTS = 4096;
    private static final Map<FontTextKey, TextLayoutPlan> LAYOUT_CACHE = boundedCache();
    private static Paint shadowPaint;
    private static boolean closed;

    private TextFactory() {
    }

    public static synchronized float getTextWidth(String familyName, float size, String text) {
        ensureOpen();
        text = normalize(text);
        return layout(familyName, size, text).width();
    }

    public static synchronized float getTextHeight(String familyName, float size) {
        ensureOpen();
        return layout(familyName, size, "").height();
    }

    public static synchronized float getTextHeight(String familyName, float size, String text) {
        ensureOpen();
        return layout(familyName, size, normalize(text)).height();
    }

    @ApiStatus.Internal
    private static void drawGlyphsSplice(Canvas canvas, String familyName, float size, String text, float x, float y, Paint paint) {
        ensureOpen();
        text = normalize(text);
        TextLayoutPlan layout = layout(familyName, size, text);
        float baseline = y + layout.baseline();
        for (TextRun run : layout.runs()) {
            canvas.drawString(run.text(), x + run.x(), baseline,
                    FontFamily.getCachedFont(run.family(), size), paint);
        }
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

    private static TextLayoutPlan layout(String family, float size, String text) {
        FontTextKey key = new FontTextKey(family, size, normalize(text));
        return LAYOUT_CACHE.computeIfAbsent(key, TextFactory::buildLayout);
    }

    private static TextLayoutPlan buildLayout(FontTextKey key) {
        Font primary = FontFamily.getCachedFont(key.familyName(), key.size());
        String fallbackFamily = FontFamily.fallbackFamily(key.familyName());
        Font fallback = fallbackFamily == null ? null : FontFamily.getCachedFont(fallbackFamily, key.size());
        List<TextRun> runs = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String currentFamily = key.familyName();
        float width = 0f;
        float ascent = -primary.getMetrics().getAscent();
        float descent = primary.getMetrics().getDescent();

        for (int offset = 0; offset < key.text().length();) {
            int codePoint = key.text().codePointAt(offset);
            int chars = Character.charCount(codePoint);
            String selected = selectFamily(primary, fallback, key.familyName(), fallbackFamily, codePoint,
                    currentFamily);
            if (!selected.equals(currentFamily) && !current.isEmpty()) {
                Font font = FontFamily.getCachedFont(currentFamily, key.size());
                String value = current.toString();
                runs.add(new TextRun(currentFamily, value, width));
                width += font.measureTextWidth(value);
                current.setLength(0);
            }
            currentFamily = selected;
            current.append(key.text(), offset, offset + chars);
            offset += chars;
        }
        if (!current.isEmpty()) {
            Font font = FontFamily.getCachedFont(currentFamily, key.size());
            String value = current.toString();
            runs.add(new TextRun(currentFamily, value, width));
            width += font.measureTextWidth(value);
        }
        for (TextRun run : runs) {
            if (run.family().equals(key.familyName())) continue;
            FontMetrics metrics = FontFamily.getCachedFont(run.family(), key.size()).getMetrics();
            ascent = Math.max(ascent, -metrics.getAscent());
            descent = Math.max(descent, metrics.getDescent());
        }
        return new TextLayoutPlan(List.copyOf(runs), width, ascent + descent, ascent);
    }

    private static String selectFamily(Font primary, Font fallback, String primaryFamily,
                                       String fallbackFamily, int codePoint, String currentFamily) {
        if (primary.getTypeface().getUTF32Glyph(codePoint) != 0) return primaryFamily;
        if (fallback != null && fallback.getTypeface().getUTF32Glyph(codePoint) != 0) return fallbackFamily;
        return currentFamily;
    }

    private static Map<FontTextKey, TextLayoutPlan> boundedCache() {
        return new LinkedHashMap<>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<FontTextKey, TextLayoutPlan> eldest) {
                return size() > MAX_MEASUREMENTS;
            }
        };
    }

    public static synchronized void close() {
        if (closed) return;
        closed = true;
        LAYOUT_CACHE.clear();
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
