package cc.nimbus.utils.render.skija.internal.text;

import cc.nimbus.utils.render.font.FontSpec;
import io.github.humbleui.skija.*;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TextFactory {
    private record FontTextKey(FontSpec font, float size, String text) {}
    private record TextRun(FontSpec font, String text, float x, TextBlob blob) {}
    private record TextLayoutPlan(List<TextRun> runs, float width, float height, float baseline,
                                  float visualLeft, float visualRight, float visualCenterY) {}

    private static final int MAX_MEASUREMENTS = 4096;
    private static final Map<FontTextKey, TextLayoutPlan> LAYOUT_CACHE = boundedCache();
    private static Paint shadowPaint;
    private static boolean closed;

    private TextFactory() {
    }

    public static synchronized float getTextWidth(FontSpec font, float size, String text) {
        ensureOpen();
        text = normalize(text);
        return layout(font, size, text).width();
    }

    public static synchronized float getTextHeight(FontSpec font, float size) {
        ensureOpen();
        return layout(font, size, "").height();
    }

    public static synchronized float getTextHeight(FontSpec font, float size, String text) {
        ensureOpen();
        return layout(font, size, normalize(text)).height();
    }

    public static synchronized float getTextVisualLeft(FontSpec font, float size, String text) {
        ensureOpen();
        return layout(font, size, normalize(text)).visualLeft();
    }

    public static synchronized float getTextVisualRight(FontSpec font, float size, String text) {
        ensureOpen();
        return layout(font, size, normalize(text)).visualRight();
    }

    public static synchronized float getTextVisualCenterY(FontSpec font, float size, String text) {
        ensureOpen();
        return layout(font, size, normalize(text)).visualCenterY();
    }

    public static synchronized float getTextBaseline(FontSpec font, float size, String text) {
        ensureOpen();
        return layout(font, size, normalize(text)).baseline();
    }

    @ApiStatus.Internal
    private static void drawGlyphsSplice(Canvas canvas, FontSpec font, float size, String text, float x, float y, Paint paint) {
        ensureOpen();
        text = normalize(text);
        TextLayoutPlan layout = layout(font, size, text);
        drawLayoutAtBaseline(canvas, size, layout, x, y + layout.baseline(), paint);
    }

    private static void drawLayoutAtBaseline(Canvas canvas, float size, TextLayoutPlan layout,
                                             float x, float baseline, Paint paint) {
        for (TextRun run : layout.runs()) {
            if (run.blob() != null && !run.blob().isClosed()) {
                canvas.drawTextBlob(run.blob(), x + run.x(), baseline, paint);
            } else {
                canvas.drawString(run.text(), x + run.x(), baseline,
                        FontFamily.getCachedFont(run.font(), size), paint);
            }
        }
    }

    public static void drawText(Canvas canvas, FontSpec font, float size, String text, float x, float y, Paint paint) {
        drawGlyphsSplice(canvas, font, size, text, x, y, paint);
    }

    public static void drawTextAtBaseline(Canvas canvas, FontSpec font, float size, String text,
                                          float x, float baseline, Paint paint) {
        ensureOpen();
        TextLayoutPlan layout = layout(font, size, normalize(text));
        drawLayoutAtBaseline(canvas, size, layout, x, baseline, paint);
    }

    public static void drawCenteredText(Canvas canvas, FontSpec font, float size, String text, float x, float y, Paint paint) {

        float center = getTextWidth(font, size, text) / 2;
        drawGlyphsSplice(canvas, font, size, text, x - center, y, paint);
    }

    public static void drawTextWithShadow(Canvas canvas, FontSpec font, float size, String text, float x, float y, Paint paint) {
        Paint shadow = shadowPaint();
        int shadowHex = TextMth.calculateShadowColor(paint.getColor());
        if (shadow.getColor() != shadowHex) {
            shadow.setColor(shadowHex);
        }

        drawGlyphsSplice(canvas, font, size, text, x + 1, y + 1, shadow);
        drawGlyphsSplice(canvas, font, size, text, x, y, paint);
    }

    public static void drawCenteredTextWithShadow(Canvas canvas, FontSpec font, float size, String text, float x, float y, Paint paint) {
        text = normalize(text);
        Paint shadow = shadowPaint();
        int shadowHex = TextMth.calculateShadowColor(paint.getColor());
        if (shadow.getColor() != shadowHex) {
            shadow.setColor(shadowHex);
        }

        float center = getTextWidth(font, size, text) / 2;
        drawGlyphsSplice(canvas, font, size, text, x - center + 1, y + 1, shadow);
        drawGlyphsSplice(canvas, font, size, text, x - center, y, paint);
    }

    private static TextLayoutPlan layout(FontSpec font, float size, String text) {
        FontTextKey key = new FontTextKey(font, size, normalize(text));
        return LAYOUT_CACHE.computeIfAbsent(key, TextFactory::buildLayout);
    }

    private static TextLayoutPlan buildLayout(FontTextKey key) {
        Font primary = FontFamily.getCachedFont(key.font(), key.size());
        FontSpec fallbackFont = FontFamily.fallback(key.font());
        Font fallback = fallbackFont == null ? null : FontFamily.getCachedFont(fallbackFont, key.size());
        List<TextRun> runs = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        FontSpec currentFont = key.font();
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
            FontSpec selected = selectFont(primary, fallback, key.font(), fallbackFont, codePoint,
                    currentFont);
            if (!selected.equals(currentFont) && !current.isEmpty()) {
                Font font = FontFamily.getCachedFont(currentFont, key.size());
                String value = current.toString();
                runs.add(createRun(currentFont, value, width, font));
                width += font.measureTextWidth(value);
                current.setLength(0);
            }
            currentFont = selected;
            current.append(key.text(), offset, offset + chars);
            offset += chars;
        }
        if (!current.isEmpty()) {
            Font font = FontFamily.getCachedFont(currentFont, key.size());
            String value = current.toString();
            runs.add(createRun(currentFont, value, width, font));
            width += font.measureTextWidth(value);
        }
        for (TextRun run : runs) {
            Font font = FontFamily.getCachedFont(run.font(), key.size());
            var bounds = font.measureText(run.text());
            visualLeft = Math.min(visualLeft, run.x() + bounds.getLeft());
            visualRight = Math.max(visualRight, run.x() + bounds.getRight());
            visualTop = Math.min(visualTop, bounds.getTop());
            visualBottom = Math.max(visualBottom, bounds.getBottom());
            if (run.font().equals(key.font())) continue;
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

    private static FontSpec selectFont(Font primary, Font fallback, FontSpec primaryFont,
                                       FontSpec fallbackFont, int codePoint, FontSpec currentFont) {
        if (primary.getTypeface().getUTF32Glyph(codePoint) != 0) return primaryFont;
        if (fallback != null && fallback.getTypeface().getUTF32Glyph(codePoint) != 0) return fallbackFont;
        return currentFont;
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

    private static TextRun createRun(FontSpec fontSpec, String text, float x, Font font) {
        short[] glyphs = font.getStringGlyphs(text);
        TextBlob blob = glyphs.length == 0
                ? null : TextBlob.makeFromPosH(glyphs, font.getXPositions(glyphs), 0f, font);
        return new TextRun(fontSpec, text, x, blob);
    }

    private static void closeLayout(TextLayoutPlan layout) {
        for (TextRun run : layout.runs()) {
            if (run.blob() != null && !run.blob().isClosed()) run.blob().close();
        }
    }
}
