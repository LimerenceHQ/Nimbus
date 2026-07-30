package cc.nimbus.utils.render.skija.surface.offsurface;

import io.github.humbleui.skija.*;
import io.github.humbleui.types.Rect;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class ShadowSurface extends AbstractOffSurface<ShadowSurface.Target> {
    private final Paint shadow = new Paint();
    private final Paint paint = new Paint();

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

    @Override
    protected void onCanvas(DirectContext context, Surface surface, Canvas canvas, @Nullable Path path, @Nullable List<Rect> srcs, @Nullable Target target) {
        if (path == null || target == null || target.radius <= 0) {
            return;
        }

        try (ImageFilter blur = ImageFilter.makeBlur(
                target.radius,
                target.radius,
                FilterTileMode.DECAL
        )) {
            shadow.setImageFilter(blur);
        }

        int count = canvas.saveLayer(null, shadow);
        try {
            paint.setAlpha(target.alpha);
            canvas.drawPath(path, paint);
        } finally {
            canvas.restoreToCount(count);
        }
    }

    public record Target(float radius, int color, int alpha) {
    }
}
