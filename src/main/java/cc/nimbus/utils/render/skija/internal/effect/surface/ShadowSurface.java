package cc.nimbus.utils.render.skija.internal.effect.surface;

import io.github.humbleui.skija.*;
import io.github.humbleui.types.Rect;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class ShadowSurface extends AbstractOffSurface<ShadowSurface.Target> {
    private final Paint shadow = new Paint();
    private final Paint paint = new Paint();
    private final Path cachedPath = new Path();
    private ImageFilter blurFilter;
    private float filterRadius = Float.NaN;
    private float cachedRadius = Float.NaN;
    private int cachedAlpha = -1;

    public ShadowSurface(int targetFps) {
        super(targetFps);
        this.paint.setColor(0xFFFFFFFF);
        this.paint.setAntiAlias(true);
    }

    @Override
    protected ImageInfo imageInfo(int width, int height, Target target) {
        return ImageInfo.makeA8(width, height);
    }

    @Override
    protected float inflate(Target target) {
        return target.radius * 6F;
    }

    public boolean shouldUpdate(long nowMs, float currentFps, Path path, Target target) {
        if (getSnapshot() == null) return true;
        boolean changed = Float.compare(cachedRadius, target.radius()) != 0
                || cachedAlpha != target.alpha()
                || !cachedPath.equals(path);
        return changed && super.shouldUpdate(nowMs, currentFps);
    }

    @Override
    protected void onCanvas(DirectContext context, Surface surface, Canvas canvas, @Nullable Path path, @Nullable List<Rect> srcs, @Nullable Target target) {
        if (path == null || target == null || target.radius <= 0) {
            return;
        }

        if (blurFilter == null || blurFilter.isClosed() || Float.compare(filterRadius, target.radius()) != 0) {
            shadow.setImageFilter(null);
            if (blurFilter != null && !blurFilter.isClosed()) blurFilter.close();
            blurFilter = ImageFilter.makeBlur(target.radius(), target.radius(), FilterTileMode.DECAL);
            shadow.setImageFilter(blurFilter);
            filterRadius = target.radius();
        }

        int count = canvas.saveLayer(null, shadow);
        try {
            paint.setAlpha(target.alpha);
            canvas.drawPath(path, paint);
        } finally {
            canvas.restoreToCount(count);
        }
        try (Path previous = new Path(path)) {
            cachedPath.swap(previous);
        }
        cachedRadius = target.radius();
        cachedAlpha = target.alpha();
    }

    public record Target(float radius, int color, int alpha) {
    }

    @Override
    public void close() {
        super.close();
        shadow.setImageFilter(null);
        if (blurFilter != null && !blurFilter.isClosed()) blurFilter.close();
        if (!cachedPath.isClosed()) cachedPath.close();
        if (!shadow.isClosed()) shadow.close();
        if (!paint.isClosed()) paint.close();
        blurFilter = null;
    }
}
