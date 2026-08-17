package cc.nimbus.utils.render.skija.internal.text;

import io.github.humbleui.skija.*;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TextFactory {
    private record FontTextKey(String familyName, float size, String text) {}
    private record TextRun(String family, String text, float x, TextBlob blob) {}
    private record TextLayoutPlan(List<TextRun> runs, float width, float height, float baseline,
                                  float visualLeft, float visualRight, float visualCenterY) {}

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

    public static synchronized float getTextVisualLeft(String familyName, float size, String text) {
        ensureOpen();
        return layout(familyName, size, normalize(text)).visualLeft();
    }

    public static synchronized float getTextVisualRight(String familyName, float size, String text) {
        ensureOpen();
        return layout(familyName, size, normalize(text)).visualRight();
    }

    public static synchronized float getTextVisualCenterY(String familyName, float size, String text) {
        ensureOpen();
        return layout(familyName, size, normalize(text)).visualCenterY();
    }

    public static synchronized float getTextBaseline(String familyName, float size, String text) {
        ensureOpen();
        return layout(familyName, size, normalize(text)).baseline();
    }

    @ApiStatus.Internal
    private static void drawGlyphsSplice(Canvas canvas, String familyName, float size, String text, float x, float y, Paint paint) {
        ensureOpen();
        text = normalize(text);
        TextLayoutPlan layout = layout(familyName, size, text);
        drawLayoutAtBaseline(canvas, size, layout, x, y + layout.baseline(), paint);
    }

    private static void drawLayoutAtBaseline(Canvas canvas, float size, TextLayoutPlan layout,
                                             float x, float baseline, Paint paint) {
        for (TextRun run : layout.runs()) {
            if (run.blob() != null && !run.blob().isClosed()) {
                canvas.drawTextBlob(run.blob(), x + run.x(), baseline, paint);
            } else {
                canvas.drawString(run.text(), x + run.x(), baseline,
                        FontFamily.getCachedFont(run.family(), size), paint);
            }
        }
    }

    public static void drawText(Canvas canvas, String family, float size, String text, float x, float y, Paint paint) {
        drawGlyphsSplice(canvas, family, size, text, x, y, paint);
    }

    public static void drawTextAtBaseline(Canvas canvas, String family, float size, String text,
                                          float x, float baseline, Paint paint) {
        ensureOpen();
        TextLayoutPlan layout = layout(family, size, normalize(text));
        drawLayoutAtBaseline(canvas, size, layout, x, baseline, paint);
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
        float visualLeft = Float.POSITIVE_INFINITY;
        float visualRight = Float.NEGATIVE_INFINITY;
        float visualTop = Float.POSITIVE_INFINITY;
        float visualBottom = Float.NEGATIVE_INFINITY;
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
                runs.add(createRun(currentFamily, value, width, font));
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
            runs.add(createRun(currentFamily, value, width, font));
            width += font.measureTextWidth(value);
        }
        for (TextRun run : runs) {
            Font font = FontFamily.getCachedFont(run.family(), key.size());
            var bounds = font.measureText(run.text());
            visualLeft = Math.min(visualLeft, run.x() + bounds.getLeft());
            visualRight = Math.max(visualRight, run.x() + bounds.getRight());
            visualTop = Math.min(visualTop, bounds.getTop());
            visualBottom = Math.max(visualBottom, bounds.getBottom());
            if (run.family().equals(key.familyName())) continue;
            FontMetrics metrics = font.getMetrics();
            ascent = Math.max(ascent, -metrics.getAscent());
            descent = Math.max(descent, metrics.getDescent());
        }
        float visualCenterY = Float.isFinite(visualTop) && Float.isFinite(visualBottom)
                ? ascent + (visualTop + visualBottom) * .5f
                : (ascent + descent) * .5f;
        return new TextLayoutPlan(List.copyOf(runs), width, ascent + descent, ascent,
                Float.isFinite(visualLeft) ? visualLeft : 0f,
                Float.isFinite(visualRight) ? visualRight : width, visualCenterY);
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
                boolean remove = size() > MAX_MEASUREMENTS;
                if (remove) closeLayout(eldest.getValue());
                return remove;
            }
        };
    }

    public static synchronized void close() {
        if (closed) return;
        closed = true;
        for (TextLayoutPlan layout : LAYOUT_CACHE.values()) closeLayout(layout);
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

    private static TextRun createRun(String family, String text, float x, Font font) {
        short[] glyphs = font.getStringGlyphs(text);
        TextBlob blob = glyphs.length == 0
                ? null : TextBlob.makeFromPosH(glyphs, font.getXPositions(glyphs), 0f, font);
        return new TextRun(family, text, x, blob);
    }

    private static void closeLayout(TextLayoutPlan layout) {
        for (TextRun run : layout.runs()) {
            if (run.blob() != null && !run.blob().isClosed()) run.blob().close();
        }
    }
}
