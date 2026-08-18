package cc.nimbus.utils.render.skija.internal.effect.surface;

import io.github.humbleui.skija.*;
import io.github.humbleui.types.Rect;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract sealed class AbstractOffSurface<V> implements AutoCloseable permits GaussianSurface, KawaseSurface, ShadowSurface, TextureSurface {
    private Surface surface = null;

    private Image snapshot;
    private Rect snapshotBounds;

    private long lastUpdateTimeMs = 0;
    private long targetIntervalMs;

    public AbstractOffSurface(int targetFps) {
        setTargetFps(targetFps);
    }

    public boolean shouldUpdate(long nowMs, float currentFps) {
        long interval = targetIntervalMs;

        if (currentFps > 1 && currentFps < (1000f / targetIntervalMs)) {
            interval = (long) (1000f / currentFps);
        }

        return snapshot == null || nowMs - lastUpdateTimeMs >= interval;
    }

    public boolean shouldUpdateForPath(long nowMs, float currentFps,
                                       @Nullable Path path, @Nullable V target) {
        if (snapshot == null) return true;
        if (path != null && target != null) {
            Rect requestedBounds = path.getBounds().inflate(inflate(target));
            if (!sameBounds(snapshotBounds, requestedBounds)) return true;
        }
        return shouldUpdate(nowMs, currentFps);
    }

    public void updateSurface(DirectContext context, @Nullable Path path, @Nullable List<Rect> srcs, float screenWidth, float screenHeight, V target) {
        if (context == null || target == null) {
            return;
        }

        int width;
        int height;
        Rect bounds;

        if (path != null) {
            float inflated = inflate(target);
            if (inflated > 0) {
                bounds = path.getBounds().inflate(inflated);
            } else {
                bounds = path.getBounds();
            }
        } else {
            bounds = Rect.makeXYWH(0, 0, screenWidth, screenHeight);
        }

        this.snapshotBounds = bounds;
        width = Math.max(1, (int) Math.ceil(bounds.getWidth()));
        height = Math.max(1, (int) Math.ceil(bounds.getHeight()));

        if (surface == null || surface.isClosed() || surface.getWidth() != width || surface.getHeight() != height) {
            if (surface != null && !surface.isClosed()) {
                surface.close();
            }

            surface = Surface.makeRenderTarget(context, false, imageInfo(width, height, target));
        }

        Canvas canvas = surface.getCanvas();
        canvas.clear(0x00000000);

        int count = canvas.save();
        try {
            canvas.translate(-bounds.getLeft(), -bounds.getTop());
            onCanvas(context, surface, canvas, path, srcs, target);
        } finally {
            canvas.restoreToCount(count);
        }

        Image newImage = surface.makeImageSnapshot();

        if (snapshot != null) {
            snapshot.close();
        }

        snapshot = newImage;
        lastUpdateTimeMs = System.currentTimeMillis();
    }

    protected abstract ImageInfo imageInfo(int width, int height, V target);

    protected abstract void onCanvas(DirectContext context, Surface surface, Canvas canvas, @Nullable Path path, @Nullable List<Rect> srcs, @Nullable V target);

    protected float inflate(V target) {
        return 0;
    }

    public void setTargetFps(int fps) {
        if (fps <= 0) fps = 1;
        this.targetIntervalMs = 1000L / fps;
    }

    public Image getSnapshot() {
        return snapshot;
    }

    public Rect getSnapshotBounds() {
        return snapshotBounds;
    }

    public void invalidate() {
        if (snapshot != null && !snapshot.isClosed()) snapshot.close();
        snapshot = null;
        snapshotBounds = null;
        lastUpdateTimeMs = 0;
    }

    private static boolean sameBounds(@Nullable Rect first, @Nullable Rect second) {
        if (first == second) return true;
        if (first == null || second == null) return false;
        return Float.compare(first.getLeft(), second.getLeft()) == 0
                && Float.compare(first.getTop(), second.getTop()) == 0
                && Float.compare(first.getRight(), second.getRight()) == 0
                && Float.compare(first.getBottom(), second.getBottom()) == 0;
    }

    @Override
    public void close() {
        invalidate();
        if (surface != null && !surface.isClosed()) surface.close();
        surface = null;
    }
}
