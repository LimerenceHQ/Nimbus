package cc.nimbus.utils.render.skija.post;

import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ClipMode;
import io.github.humbleui.skija.DirectContext;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Path;
import io.github.humbleui.types.Rect;
import net.minecraft.client.Minecraft;
import cc.nimbus.module.ModuleManager;
import cc.nimbus.module.impl.hud.PostProcess;
import cc.nimbus.utils.render.skija.create.Skia;
import cc.nimbus.utils.render.skija.path.PathMerger;
import cc.nimbus.utils.render.skija.surface.RefreshMode;
import cc.nimbus.utils.render.skija.surface.offsurface.GaussianSurface;
import cc.nimbus.utils.render.skija.surface.offsurface.KawaseSurface;
import cc.nimbus.utils.render.skija.surface.offsurface.ShadowSurface;
import cc.nimbus.utils.render.skija.surface.offsurface.TextureSurface;

import java.util.List;

public final class PPHandler {
    private static final GaussianSurface GAUSSIAN_SURFACE = new GaussianSurface(60);
    private static final KawaseSurface KAWASE_SURFACE = new KawaseSurface(60);
    private static final ShadowSurface SHADOW_PROCESSOR = new ShadowSurface(30);
    private static final TextureSurface TEXTURE_SURFACE = new TextureSurface(1);
    private static final PathMerger pathMerger = new PathMerger();

    private PPHandler() {
    }

    public static <T> void add(T element) {
        pathMerger.add(element);
    }

    public static void drawAll(Canvas canvas) {
        DirectContext context = Skia.INSTANCE.getContext();
        if (context == null || pathMerger.isEmpty()) {
            return;
        }

        pathMerger.build(path -> {
            PostProcess postProcess = ModuleManager.getInstance().getModule(PostProcess.class);
            if (postProcess == null || !postProcess.isEnabled()) {
                return;
            }

            List<Rect> bounds = postProcess.spilt.getValue() ? pathMerger.getBounds() : null;
            apply(canvas, path, bounds, postProcess);
        });
    }

    private static void apply(Canvas canvas, Path path, List<Rect> bounds, PostProcess postProcess) {
        if (postProcess.shadow.getValue()) {
            ShadowSurface.Target target = new ShadowSurface.Target(
                    postProcess.shadow_radius.getValue().floatValue(),
                    0xFF000000,
                    (int) (postProcess.shadow_opacity.getValue().floatValue() * 255)
            );
            shadow(canvas, path, Util.getShadowRefreshRate(postProcess), target);
        }

        switch (postProcess.blur_mode.getValue()) {
            case "Gaussian" -> gaussian(canvas, path, bounds, Util.getBlurRefreshRate(postProcess),
                    new GaussianSurface.Target(postProcess.blur_radius.getValue().floatValue()));
            case "Kawase" -> kawase(canvas, path, bounds, Util.getBlurRefreshRate(postProcess),
                    new KawaseSurface.Target(
                            postProcess.blur_passes.getValue().intValue(),
                            postProcess.blur_sample.getValue().floatValue(),
                            postProcess.blur_sample.getValue().floatValue()));
            default -> {
            }
        }

        if (postProcess.texture.getValue()) {
            texture(canvas, path, new TextureSurface.Target(
                    0xFFFFFFFF,
                    (int) (postProcess.texture_opacity.getValue().floatValue() * 255)));
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
        if (TEXTURE_SURFACE.shouldUpdate(System.currentTimeMillis(), 1)) {
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
        return Skia.INSTANCE.getContext();
    }

    private static int width() {
        return Skia.INSTANCE.getCurrentWidth();
    }

    private static int height() {
        return Skia.INSTANCE.getCurrentHeight();
    }

    private static int gameFps() {
        return Math.max(1, Minecraft.getInstance().getFps());
    }

    public static final class Util {
        private Util() {
        }

        public static int getBlurRefreshRate(PostProcess postProcess) {
            if (postProcess == null) {
                return 60;
            }
            return RefreshMode.getByName(postProcess.blurRefresh.getValue())
                    .getRefreshRate(postProcess.blurRefreshLimit.getValue().intValue());
        }

        public static int getShadowRefreshRate(PostProcess postProcess) {
            if (postProcess == null) {
                return 30;
            }
            return RefreshMode.getByName(postProcess.glowRefresh.getValue())
                    .getRefreshRate(postProcess.glowRefreshLimit.getValue().intValue());
        }
    }
}
