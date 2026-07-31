package cc.nimbus.utils.render.skija.internal.geometry.gn;

import cc.nimbus.utils.render.skija.internal.geometry.PathMath;
import cc.nimbus.utils.render.skija.internal.resource.CachePool;
import io.github.humbleui.skija.Matrix33;
import io.github.humbleui.skija.Path;
import io.github.humbleui.types.Point;

public final class SmoothRectPathCache {
    private static final CachePool<SmoothRectSpec, Path> PATHS = new CachePool<>(1024) {
        @Override
        protected void closeValue(Path path) {
            if (path != null && !path.isClosed()) path.close();
        }
    };

    private SmoothRectPathCache() {
    }

    public static Path translated(SmoothRectSpec spec, float x, float y) {
        Path translated = new Path();
        if (!SmoothRectSpec.isEmpty(spec)) {
            translated.addPath(basePath(spec), Matrix33.makeTranslate(x, y));
        }
        return translated;
    }

    public static void clear() {
        PATHS.clear();
    }

    private static Path basePath(SmoothRectSpec spec) {
        return PATHS.build(spec, () -> buildPath(spec));
    }

    private static Path buildPath(SmoothRectSpec spec) {
        float width = spec.width;
        float height = spec.height;
        float maxRadius = Math.min(width, height) * 0.5f;
        float[] radius = new float[4];
        for (int index = 0; index < radius.length; index++) {
            radius[index] = spec.radius[index] * maxRadius;
        }

        float scale = Math.min(
                Math.min(width / (radius[0] + radius[1]), width / (radius[2] + radius[3])),
                Math.min(height / (radius[0] + radius[3]), height / (radius[1] + radius[2]))
        );
        if (scale < 1f) {
            for (int index = 0; index < radius.length; index++) radius[index] *= scale;
        }

        float[] control = new float[4];
        for (int index = 0; index < control.length; index++) {
            float coefficient = 0.5522847f + Math.min(spec.softness[index] * 0.16f, 0.16f);
            control[index] = radius[index] * coefficient;
        }

        Path path = new Path();
        path.moveTo(radius[0], 0f);
        PathMath.cubicCorner(path, new Point(width - radius[1], 0f), new Point(width, radius[1]), new Point(1, 0), new Point(0, 1), control[1]);
        PathMath.cubicCorner(path, new Point(width, height - radius[2]), new Point(width - radius[2], height), new Point(0, 1), new Point(-1, 0), control[2]);
        PathMath.cubicCorner(path, new Point(radius[3], height), new Point(0f, height - radius[3]), new Point(-1, 0), new Point(0, -1), control[3]);
        PathMath.cubicCorner(path, new Point(0f, radius[0]), new Point(radius[0], 0f), new Point(0, -1), new Point(1, 0), control[0]);
        path.closePath();
        return path;
    }
}
