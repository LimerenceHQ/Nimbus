package cc.nimbus.utils.render.skija.internal.effect.post;

import cc.nimbus.utils.render.skija.api.PostProcessOptions;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ClipMode;
import io.github.humbleui.skija.DirectContext;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.PathBuilder;
import io.github.humbleui.types.Rect;
import io.github.humbleui.types.RRect;
import net.minecraft.client.Minecraft;
import cc.nimbus.utils.render.skija.internal.backend.SkijaBackend;
import cc.nimbus.utils.render.skija.internal.geometry.PathMerger;
import cc.nimbus.utils.render.skija.internal.effect.surface.GaussianSurface;
import cc.nimbus.utils.render.skija.internal.effect.surface.KawaseSurface;
import cc.nimbus.utils.render.skija.internal.effect.surface.ShadowSurface;
import cc.nimbus.utils.render.skija.internal.effect.surface.TextureSurface;

import java.util.ArrayList;
import java.util.List;

public final class PostProcessPipeline {
    private static final GaussianSurface GAUSSIAN_SURFACE = new GaussianSurface(60);
    private static final List<GaussianSurface> GAUSSIAN_REGION_SURFACES = new ArrayList<>();
    private static final KawaseSurface KAWASE_SURFACE = new KawaseSurface(60);
    private static final List<KawaseSurface> KAWASE_REGION_SURFACES = new ArrayList<>();
    private static final ShadowSurface SHADOW_PROCESSOR = new ShadowSurface(30);
    private static final TextureSurface TEXTURE_SURFACE = new TextureSurface(1);
    private static final List<TextureSurface> TEXTURE_REGION_SURFACES = new ArrayList<>();
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

    public static boolean hasRegions() {
        return !pathMerger.isEmpty();
    }

    private static void apply(Canvas canvas, Path path, List<Rect> bounds, PostProcessOptions options) {
        if (options.shadow()) {
            ShadowSurface.Target target = new ShadowSurface.Target(
                    options.shadowRadius(),
                    0xFF000000,
                    options.shadowOpacity()
            );
            if (target.radius() > 0f && target.alpha() > 0) {
                shadow(canvas, path, options.shadowRefreshRate(), target);
            }
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
            texture(canvas, path, bounds, new TextureSurface.Target(
                    0xFFFFFFFF,
                    options.textureOpacity()));
        }
    }

    public static void shadow(Canvas canvas, Path path, int refreshRate, ShadowSurface.Target target) {
        SHADOW_PROCESSOR.setTargetFps(refreshRate);
        if (SHADOW_PROCESSOR.shouldUpdate(System.currentTimeMillis(), gameFps(), path, target)) {
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
        if (bounds != null && !bounds.isEmpty()) {
            ensureGaussianRegionCount(bounds.size());
            long now = System.currentTimeMillis();
            int fps = gameFps();
            for (int index = 0; index < bounds.size(); index++) {
                GaussianSurface regionSurface = GAUSSIAN_REGION_SURFACES.get(index);
                regionSurface.setTargetFps(refreshRate);
                try (PathBuilder builder = new PathBuilder()) {
                    builder.addRect(bounds.get(index));
                    try (Path region = builder.detach()) {
                        if (regionSurface.shouldUpdate(now, fps)) {
                            regionSurface.updateSurface(context(), region, null, width(), height(), target);
                        }
                    }
                }
                drawClipped(canvas, path, regionSurface.getSnapshot(), regionSurface.getSnapshotBounds());
            }
            return;
        }
        closeGaussianRegions();
        GAUSSIAN_SURFACE.setTargetFps(refreshRate);
        if (GAUSSIAN_SURFACE.shouldUpdate(System.currentTimeMillis(), gameFps())) {
            GAUSSIAN_SURFACE.updateSurface(context(), path, null, width(), height(), target);
        }
        drawClipped(canvas, path, GAUSSIAN_SURFACE.getSnapshot(), GAUSSIAN_SURFACE.getSnapshotBounds());
    }

    public static void kawase(Canvas canvas, Path path, List<Rect> bounds, int refreshRate, KawaseSurface.Target target) {
        if (bounds != null && !bounds.isEmpty()) {
            ensureKawaseRegionCount(bounds.size());
            long now = System.currentTimeMillis();
            int fps = gameFps();
            for (int index = 0; index < bounds.size(); index++) {
                KawaseSurface regionSurface = KAWASE_REGION_SURFACES.get(index);
                regionSurface.setTargetFps(refreshRate);
                Rect boundsForRegion = integerBounds(bounds.get(index));
                try (PathBuilder builder = new PathBuilder()) {
                    builder.addRect(boundsForRegion);
                    try (Path region = builder.detach()) {
                        if (regionSurface.shouldUpdate(now, fps)) {
                            regionSurface.updateSurface(context(), region, null, width(), height(), target);
                        }
                    }
                }
                drawClipped(canvas, path, regionSurface.getSnapshot(), regionSurface.getSnapshotBounds());
            }
            return;
        }
        closeKawaseRegions();
        KAWASE_SURFACE.setTargetFps(refreshRate);
        if (KAWASE_SURFACE.shouldUpdate(System.currentTimeMillis(), gameFps())) {
            KAWASE_SURFACE.updateSurface(context(), path, null, width(), height(), target);
        }
        drawClipped(canvas, path, KAWASE_SURFACE.getSnapshot(), KAWASE_SURFACE.getSnapshotBounds());
    }

    public static void texture(Canvas canvas, Path path, List<Rect> bounds, TextureSurface.Target target) {
        if (bounds != null && !bounds.isEmpty()) {
            ensureTextureRegionCount(bounds.size());
            for (int index = 0; index < bounds.size(); index++) {
                TextureSurface regionSurface = TEXTURE_REGION_SURFACES.get(index);
                try (PathBuilder builder = new PathBuilder()) {
                    builder.addRect(bounds.get(index));
                    try (Path region = builder.detach()) {
                        if (regionSurface.shouldUpdate(region, target)) {
                            regionSurface.updateSurface(context(), region, null, width(), height(), target);
                        }
                    }
                }
                drawClipped(canvas, path, regionSurface.getSnapshot(), regionSurface.getSnapshotBounds());
            }
            return;
        }
        closeTextureRegions();
        if (TEXTURE_SURFACE.shouldUpdate(path, target)) {
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

    public static void invalidateScene() {
        clear();
        GAUSSIAN_SURFACE.invalidate();
        for (GaussianSurface surface : GAUSSIAN_REGION_SURFACES) surface.invalidate();
        KAWASE_SURFACE.invalidate();
        for (KawaseSurface surface : KAWASE_REGION_SURFACES) surface.invalidate();
    }

    public static void close() {
        clear();
        GAUSSIAN_SURFACE.close();
        closeGaussianRegions();
        KAWASE_SURFACE.close();
        closeKawaseRegions();
        SHADOW_PROCESSOR.close();
        TEXTURE_SURFACE.close();
        closeTextureRegions();
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

    private static void ensureGaussianRegionCount(int count) {
        while (GAUSSIAN_REGION_SURFACES.size() < count) {
            GAUSSIAN_REGION_SURFACES.add(new GaussianSurface(60));
        }
        while (GAUSSIAN_REGION_SURFACES.size() > count) {
            GAUSSIAN_REGION_SURFACES.removeLast().close();
        }
    }

    private static void closeGaussianRegions() {
        for (GaussianSurface surface : GAUSSIAN_REGION_SURFACES) surface.close();
        GAUSSIAN_REGION_SURFACES.clear();
    }

    private static void ensureKawaseRegionCount(int count) {
        while (KAWASE_REGION_SURFACES.size() < count) {
            KAWASE_REGION_SURFACES.add(new KawaseSurface(60));
        }
        while (KAWASE_REGION_SURFACES.size() > count) {
            KAWASE_REGION_SURFACES.removeLast().close();
        }
    }

    private static void closeKawaseRegions() {
        for (KawaseSurface surface : KAWASE_REGION_SURFACES) surface.close();
        KAWASE_REGION_SURFACES.clear();
    }

    private static void ensureTextureRegionCount(int count) {
        while (TEXTURE_REGION_SURFACES.size() < count) {
            TEXTURE_REGION_SURFACES.add(new TextureSurface(1));
        }
        while (TEXTURE_REGION_SURFACES.size() > count) {
            TEXTURE_REGION_SURFACES.removeLast().close();
        }
    }

    private static void closeTextureRegions() {
        for (TextureSurface surface : TEXTURE_REGION_SURFACES) surface.close();
        TEXTURE_REGION_SURFACES.clear();
    }

    private static Rect integerBounds(Rect bounds) {
        return Rect.makeLTRB(
                (float) Math.floor(bounds.getLeft()),
                (float) Math.floor(bounds.getTop()),
                (float) Math.ceil(bounds.getRight()),
                (float) Math.ceil(bounds.getBottom())
        );
    }

}
