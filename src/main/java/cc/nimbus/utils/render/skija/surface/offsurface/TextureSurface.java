package cc.nimbus.utils.render.skija.surface.offsurface;

import cc.nimbus.utils.render.skija.internal.SkijaBackend;
import cc.nimbus.utils.render.skija.image.SvgFactory;
import io.github.humbleui.skija.*;
import io.github.humbleui.types.Rect;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@SuppressWarnings("SpellCheckingInspection")
public final class TextureSurface extends AbstractOffSurface<TextureSurface.Target> {
    private Target lastTarget;

    public TextureSurface(int targetFps) {
        super(targetFps);
    }

    public boolean shouldUpdate(long nowMs, float currentFps, Path path, Target target) {
        Rect cachedBounds = getSnapshotBounds();
        Rect bounds = path == null ? null : path.getBounds();
        return cachedBounds == null || bounds == null || !sameBounds(cachedBounds, bounds)
                || !target.equals(lastTarget) || super.shouldUpdate(nowMs, currentFps);
    }

    @Override
    protected ImageInfo imageInfo(int width, int height, Target target) {
        return ImageInfo.makeN32Premul(width, height);
    }

    @Override
    protected void onCanvas(DirectContext context, Surface surface, Canvas canvas, @Nullable Path path, @Nullable List<Rect> srcs, @Nullable Target target) {
        if (target == null) {
            return;
        }

        float size = 600;
        lastTarget = target;
        Rect bounds = path == null
                ? Rect.makeWH(SkijaBackend.INSTANCE.getCurrentWidth(), SkijaBackend.INSTANCE.getCurrentHeight())
                : path.getBounds();
        float startX = (float) Math.floor(bounds.getLeft() / size) * size;
        float startY = (float) Math.floor(bounds.getTop() / size) * size;
        try (Paint paint = new Paint()) {
            paint.setColor(target.color);
            paint.setAlpha(target.alpha);
            paint.setAntiAlias(true);

            for (float y = startY; y < bounds.getBottom(); y += size) {
                for (float x = startX; x < bounds.getRight(); x += size) {
                    SvgFactory.drawSvgFromImage(canvas, "assets/nimbus/image/topography.svg", x, y, size, paint);
                }
            }
        }
    }

    public record Target(int color, int alpha) {
    }

    private static boolean sameBounds(Rect first, Rect second) {
        return Float.compare(first.getLeft(), second.getLeft()) == 0
                && Float.compare(first.getTop(), second.getTop()) == 0
                && Float.compare(first.getRight(), second.getRight()) == 0
                && Float.compare(first.getBottom(), second.getBottom()) == 0;
    }

    @Override
    public void close() {
        super.close();
        lastTarget = null;
    }
}
