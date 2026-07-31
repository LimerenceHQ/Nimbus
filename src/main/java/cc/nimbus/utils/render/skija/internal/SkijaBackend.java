package cc.nimbus.utils.render.skija.internal;

import io.github.humbleui.skija.*;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import cc.nimbus.utils.render.skija.resource.FrameResources;
import cc.nimbus.utils.render.skija.internal.gl.States;
import cc.nimbus.utils.render.skija.image.minecraft.MinecraftSnapshot;
import cc.nimbus.utils.render.skija.post.PPHandler;
import cc.nimbus.utils.render.skija.post.PostProcessOptions;
import cc.nimbus.utils.render.skija.paint.PaintEffects;
import cc.nimbus.utils.render.skija.image.SkijaImages;
import cc.nimbus.utils.render.skija.image.SvgFactory;
import cc.nimbus.utils.render.skija.data.DataMemory;
import cc.nimbus.utils.render.skija.path.gn.GnBuilder;
import cc.nimbus.utils.render.skija.surface.offsurface.CanvasOffSurface;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

public final class SkijaBackend implements AutoCloseable {
    public static final SkijaBackend INSTANCE = new SkijaBackend();
    private volatile boolean init;
    private DirectContext context;
    private BackendRenderTarget renderTarget;
    private Surface surface;
    private Paint paint;
    private int framebufferId;
    private int width, height;
    private final CanvasOffSurface canvasOffSurface = new CanvasOffSurface(165);
    private final Deque<SkijaCallback> callbacks = new ConcurrentLinkedDeque<>();
    private final FrameResources frameResources = new FrameResources();
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
        surface = Surface.wrapBackendRenderTarget(context, renderTarget, SurfaceOrigin.BOTTOM_LEFT, SurfaceColorFormat.RGBA_8888, ColorSpace.getSRGB(), new SurfaceProps(true, PixelGeometry.RGB_H));
        if (surface == null) throw new IllegalStateException("Skija failed to wrap the Nimbus framebuffer");
        resetPaint();
        init = true;
    }

    public void addToSurface(SkijaCallback callback) { if (callback != null) callbacks.add(callback); }
    public synchronized void callSurface(int targetFps) {
        if (non() || callbacks.isEmpty()) return;
        States.push();
        try {
            context.resetGLAll();
            canvasOffSurface.setTargetFps(Math.max(1, targetFps));
            int gameFps = Math.max(1, Minecraft.getInstance().getFps());
            if (canvasOffSurface.shouldUpdate(System.currentTimeMillis(), gameFps)) {
                canvasOffSurface.updateSurface(context, null, null, width, height, offscreen -> {
                    int save = offscreen.save();
                    try {
                        PPHandler.drawAll(offscreen, postProcessOptions);
                    } finally {
                        offscreen.restoreToCount(save);
                    }
                    for (SkijaCallback callback : callbacks) {
                        callback.call(offscreen, width, height, resetPaint());
                    }
                });
            }
            callbacks.clear();
            Image snapshot = canvasOffSurface.getSnapshot();
            if (snapshot != null) getCanvas().drawImage(snapshot, 0, 0);
            flushAndSubmit();
        } finally {
            callbacks.clear();
            States.pop();
        }
    }
    public synchronized void flushAndSubmit() {
        if (non()) return;
        try {
            surface.flushAndSubmit();
            context.flush();
        } finally {
            frameResources.clear();
        }
    }
    private Paint resetPaint() { if (paint == null || paint.isClosed()) paint = new Paint(); else paint.reset(); return paint; }
    public <T extends AutoCloseable> T ownFrameResource(T resource) { return frameResources.own(resource); }
    public void setPostProcessOptions(PostProcessOptions options) {
        postProcessOptions = options == null ? PostProcessOptions.DISABLED : options;
    }
    public PostProcessOptions getPostProcessOptions() { return postProcessOptions; }

    public synchronized void closeAll() {
        callbacks.clear();
        frameResources.clear();
        MinecraftSnapshot.clear();
        canvasOffSurface.close();
        PPHandler.close();
        PaintEffects.clear();
        SkijaImages.clear();
        SvgFactory.clear();
        GnBuilder.clear();
        DataMemory.clear();
        postProcessOptions = PostProcessOptions.DISABLED;
        closeSurface();
        closeManaged(paint); paint = null;
        context = closeManaged(context);
        framebufferId = width = height = 0;
        init = false;
    }

    /** Detaches a single target without destroying the shared context and caches. */
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
    @Override public void close() { closeAll(); }
    public boolean non() { return !init || context == null || context.isClosed() || renderTarget == null || renderTarget.isClosed() || surface == null || surface.isClosed(); }
    public int getCurrentWidth() { return width; }
    public int getCurrentHeight() { return height; }
    public DirectContext getContext() { return context; }
    public Canvas getCanvas() { return surface == null ? null : surface.getCanvas(); }
}
