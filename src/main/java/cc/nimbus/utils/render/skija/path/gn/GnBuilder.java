package cc.nimbus.utils.render.skija.path.gn;

import cc.nimbus.utils.render.skija.resource.CachePool;
import cc.nimbus.utils.render.skija.math.PathMath;
import io.github.humbleui.skija.*;
import io.github.humbleui.types.Point;
import org.jetbrains.annotations.ApiStatus;

@SuppressWarnings("unused")
public class GnBuilder {
    private static final CachePool<Gn, Image> CACHE_POOL_IMAGE = new CachePool<>(1024) {
        @Override
        protected boolean cleanable(Image value) {
            if (value != null && !value.isClosed()) {
                value.close();
            }
            return true;
        }
    };

    private static final CachePool<Gn, Path> CACHE_POOL_PATH = new CachePool<>(1024) {
        @Override
        protected boolean cleanable(Path value) {
            if (value != null && !value.isClosed()) {
                value.close();
            }
            return true;
        }
    };

    private static Path empty = new Path();

    private static Path empty(Gn gn) {
        if (empty == null || empty.isClosed()) {
            empty = new Path();
            return empty;
        }

        if (empty.isEmpty()) {
            return empty;
        }

        empty.reset();
        return empty;
    }

    private static Image buildImage(Gn gn) {
        int offset = 1; // don't know

        int imgW = (int) Math.ceil(gn.width) + offset;
        int imgH = (int) Math.ceil(gn.height) + offset;

        ImageInfo info = new ImageInfo(imgW, imgH, ColorType.ALPHA_8, ColorAlphaType.PREMUL);

        try (Surface surface = Surface.makeRaster(info)) {
            Canvas canvas = surface.getCanvas();
            canvas.clear(0x00000000);

            canvas.translate(offset, offset);

            try (Paint paint = new Paint()) {
                paint.setAntiAlias(true);
                paint.setColor(0xFFFFFFFF);

                canvas.drawPath(getPath(gn), paint);
            }

            return surface.makeImageSnapshot();
        }
    }

    private static Path buildPath(Gn gn) {
        if (Gn.isNullOrEmpty(gn)) {
            return empty(gn);
        }

        float w = gn.width;
        float h = gn.height;

        float maxRadius = Math.min(w, h) * 0.5f;

        float[] r = new float[4];
        for (int i = 0; i < 4; i++) {
            r[i] = gn.radius[i] * maxRadius;
        }

        float scale = Math.min(Math.min(w / (r[0] + r[1]), w / (r[2] + r[3])), Math.min(h / (r[0] + r[3]), h / (r[1] + r[2])));
        if (scale < 1f) {
            for (int i = 0; i < 4; i++) {
                r[i] *= scale;
            }
        }

        final float g3 = 0.16F;
        float[] ko = new float[4];
        for (int i = 0; i < 4; i++) {
            ko[i] = 0.5522847f + Math.min(gn.softness[i] * g3, g3);
        }

        float[] k = new float[4];
        for (int i = 0; i < 4; i++) {
            k[i] = r[i] * ko[i];
        }

        float l = 0, t = 0;

        Path p = new Path();
        p.moveTo(l + r[0], t);
        PathMath.cubicCorner(p, new Point(w - r[1], t), new Point(w, t + r[1]), new Point(1, 0), new Point(0, 1), k[1]);
        PathMath.cubicCorner(p, new Point(w, h - r[2]), new Point(w - r[2], h), new Point(0, 1), new Point(-1, 0), k[2]);
        PathMath.cubicCorner(p, new Point(l + r[3], h), new Point(l, h - r[3]), new Point(-1, 0), new Point(0, -1), k[3]);
        PathMath.cubicCorner(p, new Point(l, t + r[0]), new Point(l + r[0], t), new Point(0, -1), new Point(1, 0), k[0]);
        p.closePath();

        return p;
    }

    @ApiStatus.Internal
    private static Path getPath(Gn gn) {
        if (Gn.isNullOrEmpty(gn)) {
            return empty(gn);
        }
        return CACHE_POOL_PATH.build(gn, () -> buildPath(gn));
    }

    public static Image getImage(Gn gn) {
        return CACHE_POOL_IMAGE.build(gn, () -> buildImage(gn));
    }

    public static Path getPath(Gn gn, Matrix33 local) {
        if (Gn.isNullOrEmpty(gn)) {
            return empty(gn);
        }
        Path offset = new Path();
        offset.addPath(getPath(gn), local);
        return offset;
    }

    public static Path getPath(Gn gn, float x, float y) {
        if (Gn.isNullOrEmpty(gn)) {
            return empty(gn);
        }
        return getPath(gn, Matrix33.makeTranslate(x, y));
    }

    public static void drawGn(Canvas canvas, Gn gn, float x, float y, Paint paint) {
        if (Gn.isNullOrEmpty(gn)) {
            return;
        }
        int offset = 1; // dont know
        canvas.drawImage(getImage(gn), x - offset, y - offset, paint);
    }

    public static void clear() {
        CACHE_POOL_IMAGE.clear();
        CACHE_POOL_PATH.clear();
        if (empty != null && !empty.isClosed()) empty.close();
        empty = null;
    }
}
