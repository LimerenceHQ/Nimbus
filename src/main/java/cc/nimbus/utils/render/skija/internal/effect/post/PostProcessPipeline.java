package cc.nimbus.utils.render.skija.internal.effect.post;

import cc.nimbus.utils.render.skija.api.PostProcessOptions;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ClipMode;
import io.github.humbleui.skija.DirectContext;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Path;
import io.github.humbleui.types.Rect;
import io.github.humbleui.types.RRect;
import net.minecraft.client.Minecraft;
import cc.nimbus.utils.render.skija.internal.backend.SkijaBackend;
import cc.nimbus.utils.render.skija.internal.geometry.PathMerger;
import cc.nimbus.utils.render.skija.internal.effect.surface.GaussianSurface;
import cc.nimbus.utils.render.skija.internal.effect.surface.KawaseSurface;
import cc.nimbus.utils.render.skija.internal.effect.surface.ShadowSurface;
import cc.nimbus.utils.render.skija.internal.effect.surface.TextureSurface;

import java.util.List;

public final class PostProcessPipeline {
    private static final GaussianSurface GAUSSIAN_SURFACE = new GaussianSurface(60);
    private static final KawaseSurface KAWASE_SURFACE = new KawaseSurface(60);
    private static final ShadowSurface SHADOW_PROCESSOR = new ShadowSurface(30);
    private static final TextureSurface TEXTURE_SURFACE = new TextureSurface(1);
    private static final PathMerger pathMerger = new PathMerger();

    private PostProcessPipeline() {
    }

    public static void add(Rect rectangle) {
        pathMerger.add(rectangle);
    }

    public static void add(RRect rectangle) {
        pathMerger.add(rectangle);
    }

    public static void add(Path path) {
        pathMerger.add(path);
    }

    public static void drawAll(Canvas canvas, PostProcessOptions options) {
        DirectContext context = SkijaBackend.INSTANCE.getContext();
        if (pathMerger.isEmpty()) {
            return;
        }
        if (context == null || options == null || !options.enabled()) {
            pathMerger.clear();
            return;
        }

        pathMerger.build(path -> {
            List<Rect> bounds = options.splitRegions() ? pathMerger.getBounds() : null;
            apply(canvas, path, bounds, options);
        });
    }

    private static void apply(Canvas canvas, Path path, List<Rect> bounds, PostProcessOptions options) {
        if (options.shadow()) {
            ShadowSurface.Target target = new ShadowSurface.Target(
                    options.shadowRadius(),
                    0xFF000000,
                    options.shadowOpacity()
            );
            shadow(canvas, path, options.shadowRefreshRate(), target);
        }

        switch (options.blurMode()) {
            case GAUSSIAN -> gaussian(canvas, path, bounds, options.blurRefreshRate(),
                    new GaussianSurface.Target(options.blurRadius()));
            case KAWASE -> kawase(canvas, path, bounds, options.blurRefreshRate(),
                    new KawaseSurface.Target(
                            options.blurPasses(),
                            options.downSample(),
                            options.upSample()));
            default -> {
            }
        }

        if (options.texture()) {
            texture(canvas, path, new TextureSurface.Target(
                    0xFFFFFFFF,
                    options.textureOpacity()));
        }
    }

    public static void shadow(Canvas canvas, Path path, int refreshRate, ShadowSurface.Target target) {
        SHADOW_PROCESSOR.setTargetFps(refreshRate);
        if (SHADOW_PROCESSOR.shouldUpdate(System.currentTimeMillis(), gameFps())) {
            SHADOW_PROCESSOR.updateSurface(context(), path, null, width(), height(), target);
        }

        Image cached = SHADOW_PROCESSOR.getSnapshot();
        Rect bounds = SHADOW_PROCESSOR.getSnapshotBounds();
        if (cached != null && bounds != null) {
            try (Paint paint = new Paint()) {
                paint.setColor(target.color());
                int save = canvas.save();
                canvas.clipPath(path, ClipMode.DIFFERENCE, true);
                canvas.drawImage(cached, bounds.getLeft(), bounds.getTop(), paint);
                canvas.restoreToCount(save);
            }
        }
    }

    public static void gaussian(Canvas canvas, Path path, List<Rect> bounds, int refreshRate, GaussianSurface.Target target) {
        GAUSSIAN_SURFACE.setTargetFps(refreshRate);
        if (GAUSSIAN_SURFACE.shouldUpdate(System.currentTimeMillis(), gameFps())) {
            GAUSSIAN_SURFACE.updateSurface(context(), path, bounds, width(), height(), target);
        }
        drawClipped(canvas, path, GAUSSIAN_SURFACE.getSnapshot(), GAUSSIAN_SURFACE.getSnapshotBounds());
    }

    public static void kawase(Canvas canvas, Path path, List<Rect> bounds, int refreshRate, KawaseSurface.Target target) {
        KAWASE_SURFACE.setTargetFps(refreshRate);
        if (KAWASE_SURFACE.shouldUpdate(System.currentTimeMillis(), gameFps())) {
            KAWASE_SURFACE.updateSurface(context(), path, bounds, width(), height(), target);
        }
        drawClipped(canvas, path, KAWASE_SURFACE.getSnapshot(), KAWASE_SURFACE.getSnapshotBounds());
    }

    public static void texture(Canvas canvas, Path path, TextureSurface.Target target) {
        if (TEXTURE_SURFACE.shouldUpdate(System.currentTimeMillis(), 1, path, target)) {
            TEXTURE_SURFACE.updateSurface(context(), path, null, width(), height(), target);
        }
        drawClipped(canvas, path, TEXTURE_SURFACE.getSnapshot(), TEXTURE_SURFACE.getSnapshotBounds());
    }

    private static void drawClipped(Canvas canvas, Path path, Image image, Rect bounds) {
        if (image == null || bounds == null) {
            return;
        }
        int save = canvas.save();
        canvas.clipPath(path, true);
        canvas.drawImage(image, bounds.getLeft(), bounds.getTop());
        canvas.restoreToCount(save);
    }

    public static void clear() {
        pathMerger.clear();
    }

    public static void close() {
        clear();
        GAUSSIAN_SURFACE.close();
        KAWASE_SURFACE.close();
        SHADOW_PROCESSOR.close();
        TEXTURE_SURFACE.close();
    }

    private static DirectContext context() {
        return SkijaBackend.INSTANCE.getContext();
    }

    private static int width() {
        return SkijaBackend.INSTANCE.getCurrentWidth();
    }

    private static int height() {
        return SkijaBackend.INSTANCE.getCurrentHeight();
    }

    private static int gameFps() {
        return Math.max(1, Minecraft.getInstance().getFps());
    }

}
