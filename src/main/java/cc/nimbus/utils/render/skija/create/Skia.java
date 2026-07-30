package cc.nimbus.utils.render.skija.create;

import io.github.humbleui.skija.*;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import cc.nimbus.utils.render.resource.Cleaner;
import cc.nimbus.utils.render.gl.States;
import cc.nimbus.utils.render.refresh.Refresh;
import cc.nimbus.utils.render.skija.image.minecraft.MinecraftSnapshot;
import cc.nimbus.utils.render.skija.post.PPHandler;
import cc.nimbus.utils.render.skija.surface.offsurface.CanvasOffSurface;

import java.nio.DoubleBuffer;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

public class Skia implements AutoCloseable {
    public volatile static Skia INSTANCE = new Skia();
    private volatile boolean init;
    private DirectContext context;
    private BackendRenderTarget renderTarget;
    private Surface surface;
    private Paint paint;
    private int framebufferId;
    private int width, height;
    private float mouseX = -1, mouseY = -1;
    private final DoubleBuffer mouseBufferX = BufferUtils.createDoubleBuffer(1);
    private final DoubleBuffer mouseBufferY = BufferUtils.createDoubleBuffer(1);
    private final CanvasOffSurface canvasOffSurface = new CanvasOffSurface(165);
    private final Deque<SkiaCallback> callbacks = new ConcurrentLinkedDeque<>();
    public final Cleaner cleaner = new Cleaner();

    public Skia() {
    }

    public synchronized void update(int framebufferId, int width, int height) {
        if (non() || this.framebufferId != framebufferId || this.width != width || this.height != height) {
            create(framebufferId, width, height);
        }
    }

    public synchronized void create(int framebufferId, int width, int height) {
        closeAll();
        if (width <= 0 || height <= 0) return;
        this.framebufferId = framebufferId;
        this.width = width; this.height = height;
        context = DirectContext.makeGL();
        context.resetGLAll();
        renderTarget = BackendRenderTarget.makeGL(width, height, 0, 8, framebufferId, GL11.GL_RGBA8);
        surface = Surface.wrapBackendRenderTarget(context, renderTarget, SurfaceOrigin.BOTTOM_LEFT, SurfaceColorFormat.RGBA_8888, ColorSpace.getSRGB(), new SurfaceProps(true, PixelGeometry.RGB_H));
        if (surface == null) throw new IllegalStateException("Skija failed to wrap the Nimbus framebuffer");
        paint = new Paint();
        init = true;
    }

    public void mouse(float mouseX, float mouseY) { this.mouseX = mouseX; this.mouseY = mouseY; }
    public void mouse(long window) {
        if (non()) return;
        mouseBufferX.clear(); mouseBufferY.clear(); GLFW.glfwGetCursorPos(window, mouseBufferX, mouseBufferY);
        boolean focused = GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE;
        mouseX = focused ? (float) mouseBufferX.get(0) : -1; mouseY = focused ? (float) mouseBufferY.get(0) : -1;
    }

    public synchronized void call(SkiaCallback callback) {
        if (non() || callback == null) return;
        States.push();
        try {
            context.resetGLAll();
            callback.call(getCanvas(), width, height, mouseX, mouseY, resetPaint());
            flushAndSubmit();
        } finally {
            States.pop();
        }
    }
    public void addToSurface(SkiaCallback callback) { if (callback != null) callbacks.add(callback); }
    public synchronized void callSurface(Refresh refresh) {
        if (non() || callbacks.isEmpty()) return;
        States.push();
        try {
            context.resetGLAll();
            canvasOffSurface.setTargetFps(refresh == null ? 165 : refresh.fps());
            int gameFps = Math.max(1, Minecraft.getInstance().getFps());
            if (canvasOffSurface.shouldUpdate(System.currentTimeMillis(), gameFps)) {
                canvasOffSurface.updateSurface(context, null, null, width, height, offscreen -> {
                    int save = offscreen.save();
                    try {
                        PPHandler.drawAll(offscreen);
                    } finally {
                        offscreen.restoreToCount(save);
                    }
                    for (SkiaCallback callback : callbacks) {
                        callback.call(offscreen, width, height, mouseX, mouseY, resetPaint());
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
    public synchronized void flushAndSubmit() { if (non()) return; cleaner.clear(); surface.flushAndSubmit(); context.flush(); }
    private Paint resetPaint() { if (paint == null || paint.isClosed()) paint = new Paint(); else paint.reset(); return paint; }
    public synchronized @Nullable Image snapshot() { return non() ? null : surface.makeImageSnapshot(); }

    public synchronized void closeAll() {
        callbacks.clear();
        cleaner.clear();
        MinecraftSnapshot.clear();
        canvasOffSurface.close();
        PPHandler.close();
        closeManaged(paint); paint = null;
        closeManaged(surface); surface = null;
        renderTarget = closeManaged(renderTarget);
        context = closeManaged(context);
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
