package cc.nimbus.utils.render.skija.internal.image;

import io.github.humbleui.skija.BlendMode;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ColorFilter;
import io.github.humbleui.skija.Data;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.svg.SVGDOM;
import io.github.humbleui.types.Rect;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

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
        dom.setContainerSize(size, size);
        int save = canvas.save();
        try {
            canvas.translate(left, top);
            if (tint == null) {
                dom.render(canvas);
                return;
            }
            try (ColorFilter colorFilter = ColorFilter.makeBlend(tint.getColor(), BlendMode.SRC_IN);
                 Paint layer = new Paint()) {
                layer.setColorFilter(colorFilter);
                layer.setAlpha(tint.getAlpha());
                canvas.saveLayer(Rect.makeXYWH(-2, -2, size + 4, size + 4), layer);
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
