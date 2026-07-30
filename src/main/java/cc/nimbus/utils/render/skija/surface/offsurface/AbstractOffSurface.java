package cc.nimbus.utils.render.skija.surface.offsurface;

import io.github.humbleui.skija.*;
import io.github.humbleui.types.Rect;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract sealed class AbstractOffSurface<V> implements AutoCloseable permits CanvasOffSurface, GaussianSurface, KawaseSurface, ShadowSurface, TextureSurface {
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
                bounds = path.getBounds().inflate(inflate(target));
            } else {
                bounds = path.getBounds();
            }
        } else {
            bounds = Rect.makeXYWH(0, 0, screenWidth, screenHeight);
        }

        this.snapshotBounds = bounds;
        width = Math.max(1, (int) bounds.getWidth());
        height = Math.max(1, (int) bounds.getHeight());

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

    @Override
    public void close() {
        if (snapshot != null && !snapshot.isClosed()) snapshot.close();
        if (surface != null && !surface.isClosed()) surface.close();
        snapshot = null;
        surface = null;
        snapshotBounds = null;
    }
}
