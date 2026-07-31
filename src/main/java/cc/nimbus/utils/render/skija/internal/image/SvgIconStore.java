package cc.nimbus.utils.render.skija.internal.image;

import io.github.humbleui.skija.BlendMode;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ColorFilter;
import io.github.humbleui.skija.Data;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.svg.SVGDOM;
import io.github.humbleui.skija.svg.SVGSVG;
import io.github.humbleui.types.Rect;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Renderer-owned cache for SVG content; contains no UI assets. */
public final class SvgIconStore {
    private static final int MAX_SVGS = 128;
    private static final Map<String, SVGDOM> CACHE = new LinkedHashMap<>(32, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, SVGDOM> eldest) {
            if (size() <= MAX_SVGS) return false;
            close(eldest.getValue());
            return true;
        }
    };

    private SvgIconStore() {
    }

    public static synchronized void draw(Canvas canvas, String svg, float left, float top, float size, Paint tint) {
        if (svg == null || svg.isBlank() || size <= 0f) return;
        SVGDOM dom = CACHE.computeIfAbsent(svg, SvgIconStore::create);
        SVGSVG root = dom.getRoot();
        if (root == null) return;
        Rect viewBox = root.getViewBox();
        float width = viewBox == null ? 24f : viewBox.getWidth();
        float height = viewBox == null ? 24f : viewBox.getHeight();
        int save = canvas.save();
        try {
            canvas.translate(left, top);
            canvas.scale(size / Math.max(width, height), size / Math.max(width, height));
            if (tint == null) {
                dom.render(canvas);
                return;
            }
            try (Paint layer = new Paint()) {
                layer.setColorFilter(ColorFilter.makeBlend(tint.getColor(), BlendMode.SRC_IN));
                layer.setAlpha(tint.getAlpha());
                canvas.saveLayer(Rect.makeXYWH(-4, -4, width + 8, height + 8), layer);
                try {
                    dom.render(canvas);
                } finally {
                    canvas.restore();
                }
            }
        } finally {
            canvas.restoreToCount(save);
        }
    }

    public static synchronized void clear() {
        CACHE.values().forEach(SvgIconStore::close);
        CACHE.clear();
    }

    private static SVGDOM create(String svg) {
        try (Data data = Data.makeFromBytes(svg.getBytes(StandardCharsets.UTF_8))) {
            return new SVGDOM(data);
        }
    }

    private static void close(SVGDOM dom) {
        if (dom != null && !dom.isClosed()) dom.close();
    }
}
