package cc.nimbus.utils.render.skija.paint;

import io.github.humbleui.skija.*;
import io.github.humbleui.types.Point;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class PaintEffects {
    private static final Map<BlurKey, MaskFilter> BLUR_CACHE = new HashMap<>();
    private static final Map<LinearKey, Shader> LINEAR_CACHE = new HashMap<>();

    public static MaskFilter makeBlur(FilterBlurMode blurMode, float value) {
        if (value <= 0) return null;

        BlurKey key = new BlurKey(blurMode, value);

        return BLUR_CACHE.computeIfAbsent(key, g ->
                MaskFilter.makeBlur(g.blurMode, g.value)
        );
    }

    public static Shader makeLinearGradient(Point start, Point end, int[] colors) {
        LinearKey key = new LinearKey(
                start.getX(), start.getY(),
                end.getX(), end.getY(),
                colors
        );

        return LINEAR_CACHE.computeIfAbsent(key, k ->
                Shader.makeLinearGradient(
                        new Point(k.sx, k.sy),
                        new Point(k.ex, k.ey),
                        k.colors
                )
        );
    }

    public static Shader makeLinearGradient(Point start, Point end, int[] colors, float[] positions, GradientStyle style) {
        LinearKey key = new LinearKey(
                start.getX(), start.getY(),
                end.getX(), end.getY(),
                colors, positions, style
        );

        return LINEAR_CACHE.computeIfAbsent(key, k ->
                Shader.makeLinearGradient(
                        new Point(k.sx, k.sy),
                        new Point(k.ex, k.ey),
                        k.colors,
                        k.positions,
                        k.style
                )
        );
    }

    public static synchronized void clear() {
        BLUR_CACHE.values().forEach(PaintEffects::close);
        LINEAR_CACHE.values().forEach(PaintEffects::close);
        BLUR_CACHE.clear();
        LINEAR_CACHE.clear();
    }

    private static void close(Object value) {
        if (value instanceof io.github.humbleui.skija.impl.Managed managed && !managed.isClosed()) {
            managed.close();
        }
    }

    private static final class BlurKey {
        final FilterBlurMode blurMode;
        final float value;
        private final int hash;

        BlurKey(FilterBlurMode blurMode, float value) {
            this.blurMode = blurMode;
            this.value = value;
            this.hash = computeHash();
        }

        private int computeHash() {
            int h = Float.hashCode(value);
            h = 31 * h + blurMode.hashCode();
            return h;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof BlurKey k)) return false;
            return value == k.value && blurMode == k.blurMode;
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    private static final class LinearKey {
        final float sx, sy;
        final float ex, ey;
        final int[] colors;
        final float[] positions;
        final GradientStyle style;
        private final int hash;

        LinearKey(float sx, float sy, float ex, float ey, int[] colors) {
            this(sx, sy, ex, ey, colors, null, null);
        }

        LinearKey(float sx, float sy, float ex, float ey, int[] colors, float[] positions, GradientStyle style) {
            this.sx = sx;
            this.sy = sy;
            this.ex = ex;
            this.ey = ey;
            this.colors = colors == null ? new int[0] : colors.clone();
            this.positions = positions == null ? null : positions.clone();
            this.style = style;
            this.hash = computeHash();
        }

        private int computeHash() {
            int h = Float.hashCode(sx);
            h = 31 * h + Float.hashCode(sy);
            h = 31 * h + Float.hashCode(ex);
            h = 31 * h + Float.hashCode(ey);
            h = 31 * h + Arrays.hashCode(colors);
            h = 31 * h + Arrays.hashCode(positions);
            h = 31 * h + Objects.hashCode(style);
            return h;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof LinearKey k)) return false;
            return sx == k.sx && sy == k.sy &&
                    ex == k.ex && ey == k.ey &&
                    Arrays.equals(colors, k.colors) &&
                    Arrays.equals(positions, k.positions) &&
                    Objects.equals(style, k.style);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
