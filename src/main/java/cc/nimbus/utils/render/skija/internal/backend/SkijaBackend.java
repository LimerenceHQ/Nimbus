package cc.nimbus.utils.render.skija.internal.backend;

import io.github.humbleui.skija.*;
import org.lwjgl.opengl.GL11;
import cc.nimbus.utils.render.skija.internal.image.minecraft.MinecraftSnapshot;
import cc.nimbus.utils.render.skija.internal.effect.post.PostProcessPipeline;
import cc.nimbus.utils.render.skija.api.PostProcessOptions;
import cc.nimbus.utils.render.skija.internal.effect.shader.Shaders;
import cc.nimbus.utils.render.skija.internal.image.ImageStore;
import cc.nimbus.utils.render.skija.internal.image.SvgTextureCache;
import cc.nimbus.utils.render.skija.internal.resource.ResourceDataCache;
import cc.nimbus.utils.render.skija.internal.geometry.gn.SmoothRectPathCache;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

public final class SkijaBackend implements AutoCloseable {
    public static final SkijaBackend INSTANCE = new SkijaBackend();
    private volatile boolean init;
    private DirectContext context;
    private BackendRenderTarget renderTarget;
    private Surface surface;
    private int framebufferId;
    private int width, height;
    private final Deque<SkijaCallback> callbacks = new ConcurrentLinkedDeque<>();
    private volatile PostProcessOptions postProcessOptions = PostProcessOptions.DISABLED;

    private SkijaBackend() {
    }

    public synchronized void update(int framebufferId, int width, int height) {
        if (non() || this.framebufferId != framebufferId || this.width != width || this.height != height) {
            create(framebufferId, width, height);
        }
    }

    public synchronized void create(int framebufferId, int width, int height) {
        closeSurface();
        if (width <= 0 || height <= 0) return;
        this.framebufferId = framebufferId;
        this.width = width; this.height = height;
        if (context == null || context.isClosed()) {
            context = DirectContext.makeGL();
        }
        context.resetGLAll();
        renderTarget = BackendRenderTarget.makeGL(width, height, 0, 8, framebufferId, GL11.GL_RGBA8);
        surface = Surface.wrapBackendRenderTarget(context, renderTarget, SurfaceOrigin.BOTTOM_LEFT,
                ColorType.RGBA_8888, ColorSpace.getSRGB(), new SurfaceProps());
        if (surface == null) throw new IllegalStateException("Skija failed to wrap the Nimbus framebuffer");
        init = true;
    }

    public void addToSurface(SkijaCallback callback) { if (callback != null) callbacks.add(callback); }
    public boolean hasSurfaceCallbacks() { return !callbacks.isEmpty(); }
    public void discardSurfaceCallbacks() { callbacks.clear(); }
    public synchronized void callSurface() {
        if (non() || callbacks.isEmpty()) {
            callbacks.clear();
            PostProcessPipeline.clear();
            return;
        }
        try {
            context.resetGLAll();
            Canvas canvas = getCanvas();
            int save = canvas.save();
            try {
                PostProcessPipeline.drawAll(canvas, postProcessOptions);
            } finally {
                canvas.restoreToCount(save);
            }
            for (SkijaCallback callback : callbacks) callback.call(canvas, width, height);
        } finally {
            callbacks.clear();
            PostProcessPipeline.clear();
        }
    }
    public synchronized void flushAndSubmit() {
        if (non()) return;
        context.flushAndSubmit(surface);
    }
    public void setPostProcessOptions(PostProcessOptions options) {
        postProcessOptions = options == null ? PostProcessOptions.DISABLED : options;
    }
    public PostProcessOptions getPostProcessOptions() { return postProcessOptions; }

    public synchronized void invalidateScene() {
        callbacks.clear();
        PostProcessPipeline.invalidateScene();
    }

    public synchronized void closeAll() {
        callbacks.clear();
        RuntimeException failure = null;
        failure = cleanup(failure, MinecraftSnapshot::clear);
        failure = cleanup(failure, PostProcessPipeline::close);
        failure = cleanup(failure, Shaders::close);
        failure = cleanup(failure, ImageStore::clear);
        failure = cleanup(failure, SvgTextureCache::clear);
        failure = cleanup(failure, SmoothRectPathCache::clear);
        failure = cleanup(failure, ResourceDataCache::clear);
        postProcessOptions = PostProcessOptions.DISABLED;
        failure = cleanup(failure, this::closeSurface);
        DirectContext contextToClose = context;
        context = null;
        failure = cleanup(failure, () -> closeManaged(contextToClose));
        framebufferId = width = height = 0;
        init = false;
        if (failure != null) throw failure;
    }

    public synchronized void detach(int targetFramebufferId) {
        if (framebufferId == targetFramebufferId) {
            closeSurface();
        }
    }

    private void closeSurface() {
        closeManaged(surface); surface = null;
        renderTarget = closeManaged(renderTarget);
        framebufferId = width = height = 0;
        init = false;
    }
    private static <T extends io.github.humbleui.skija.impl.Managed> T closeManaged(T value) { if (value != null && !value.isClosed()) value.close(); return null; }
    private static RuntimeException cleanup(RuntimeException failure, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException error) {
            if (failure == null) return error;
            failure.addSuppressed(error);
        }
        return failure;
    }
    @Override public void close() { closeAll(); }
    public boolean non() { return !init || context == null || context.isClosed() || renderTarget == null || renderTarget.isClosed() || surface == null || surface.isClosed(); }
    public int getCurrentWidth() { return width; }
    public int getCurrentHeight() { return height; }
    public DirectContext getContext() { return context; }
    public Canvas getCanvas() { return surface == null ? null : surface.getCanvas(); }
}
