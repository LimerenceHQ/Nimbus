package cc.nimbus.utils.render.skija.paint;

import cc.nimbus.utils.render.skija.create.Skia;
import io.github.humbleui.skija.*;
import io.github.humbleui.types.Point;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public final class PaintEffects {
    private static final Map<BlurKey, MaskFilter> BLUR_CACHE = new HashMap<>();
    private static final Map<LinearKey, Shader> LINEAR_CACHE = new HashMap<>();

    static {
        Skia.INSTANCE.cleaner.addPersistentTask(BLUR_CACHE::clear);
        Skia.INSTANCE.cleaner.addPersistentTask(LINEAR_CACHE::clear);
    }

    public static MaskFilter makeBlur(FilterBlurMode blurMode, float value) {
        if (value <= 0) return null;

        BlurKey key = new BlurKey(blurMode, value);

        return BLUR_CACHE.computeIfAbsent(key, g ->
                Skia.INSTANCE.cleaner.addResource(
                        MaskFilter.makeBlur(g.blurMode, g.value)
                )
        );
    }

    public static Shader makeLinearGradient(Point start, Point end, int[] colors) {
        LinearKey key = new LinearKey(
                start.getX(), start.getY(),
                end.getX(), end.getY(),
                colors
        );

        return LINEAR_CACHE.computeIfAbsent(key, k ->
                Skia.INSTANCE.cleaner.addResource(
                        Shader.makeLinearGradient(
                                new Point(k.sx, k.sy),
                                new Point(k.ex, k.ey),
                                k.colors
                        )
                )
        );
    }

    public static Shader makeLinearGradient(Point start, Point end, int[] colors, float[] positions, GradientStyle style) {
        LinearKey key = new LinearKey(
                start.getX(), start.getY(),
                end.getX(), end.getY(),
                colors
        );

        return LINEAR_CACHE.computeIfAbsent(key, k ->
                Skia.INSTANCE.cleaner.addResource(
                        Shader.makeLinearGradient(
                                new Point(k.sx, k.sy),
                                new Point(k.ex, k.ey),
                                k.colors,
                                positions,
                                style
                        )
                )
        );
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
        private final int hash;

        LinearKey(float sx, float sy, float ex, float ey, int[] colors) {
            this.sx = sx;
            this.sy = sy;
            this.ex = ex;
            this.ey = ey;
            this.colors = colors;
            this.hash = computeHash();
        }

        private int computeHash() {
            int h = Float.hashCode(sx);
            h = 31 * h + Float.hashCode(sy);
            h = 31 * h + Float.hashCode(ex);
            h = 31 * h + Float.hashCode(ey);
            h = 31 * h + Arrays.hashCode(colors);
            return h;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof LinearKey k)) return false;
            return sx == k.sx && sy == k.sy &&
                    ex == k.ex && ey == k.ey &&
                    Arrays.equals(colors, k.colors);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
