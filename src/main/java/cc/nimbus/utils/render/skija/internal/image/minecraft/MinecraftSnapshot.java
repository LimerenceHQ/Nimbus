package cc.nimbus.utils.render.skija.internal.image.minecraft;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.humbleui.skija.BackendTexture;
import io.github.humbleui.skija.ColorAlphaType;
import io.github.humbleui.skija.ColorType;
import io.github.humbleui.skija.DirectContext;
import io.github.humbleui.skija.GLTextureInfo;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.SurfaceOrigin;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import cc.nimbus.utils.render.skija.internal.backend.gl.States;
import cc.nimbus.utils.render.skija.internal.backend.SkijaBackend;
import cc.nimbus.utils.render.skija.internal.backend.SkijaSupport;

public final class MinecraftSnapshot {
    private static Image image;
    private static DirectContext imageContext;
    private static int stagingTexture;
    private static int width;
    private static int height;
    private static long lastCaptureNanos;
    private static boolean valid;

    private MinecraftSnapshot() {
    }

    public static synchronized void capture() {
        capture(Integer.MAX_VALUE);
    }

    public static synchronized void capture(int targetFps) {
        RenderSystem.assertOnRenderThread();
        SkijaSupport.validateBackend();
        if (!SkijaSupport.isSupported()) return;
        long now = System.nanoTime();
        long interval = 1_000_000_000L / Math.max(1, targetFps);
        if (valid && image != null && !image.isClosed() && now - lastCaptureNanos < interval) return;
        States.push();
        try {
            if (captureGuarded()) {
                valid = true;
                lastCaptureNanos = now;
            }
        } finally {
            States.pop();
        }
    }

    private static boolean captureGuarded() {
        DirectContext context = SkijaBackend.INSTANCE.getContext();
        if (context == null || context.isClosed()) {
            return false;
        }

        RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
        int sourceTexture = target.getColorTextureId();
        if (sourceTexture <= 0) {
            return false;
        }

        int targetWidth = target.width;
        int targetHeight = target.height;
        if (targetWidth <= 0 || targetHeight <= 0) {
            return false;
        }

        if (image == null || image.isClosed() || imageContext != context
                || width != targetWidth || height != targetHeight) {
            clear();
            width = targetWidth;
            height = targetHeight;
            imageContext = context;
            stagingTexture = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, stagingTexture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
        }
        copyTexture(sourceTexture, stagingTexture, width, height);
        if (image == null || image.isClosed()) {
            try (BackendTexture texture = BackendTexture.makeGL(
                    width, height, false,
                    new GLTextureInfo(GL11.GL_TEXTURE_2D, stagingTexture, GL11.GL_RGBA8))) {
                image = Image.borrowTextureFrom(
                        context, texture, SurfaceOrigin.BOTTOM_LEFT, ColorType.RGB_888X,
                        ColorAlphaType.OPAQUE, null, null
                );
            }
        }
        context.resetGLAll();
        return true;
    }

    private static void copyTexture(int source, int destination, int width, int height) {
        int read = GL30.glGenFramebuffers();
        int draw = GL30.glGenFramebuffers();
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
            GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, source, 0);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
            GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, destination, 0);
            GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height,
                    GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        } finally {
            GL30.glDeleteFramebuffers(read);
            GL30.glDeleteFramebuffers(draw);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, 0);
        }
    }

    public static synchronized Image getScreenshot() {
        DirectContext context = SkijaBackend.INSTANCE.getContext();
        if (!valid || context == null || context.isClosed() || imageContext != context
                || image == null || image.isClosed()) {
            return null;
        }
        return image;
    }

    public static synchronized void invalidate() {
        valid = false;
        lastCaptureNanos = 0L;
    }

    public static synchronized void clear() {
        if (image != null && !image.isClosed()) image.close();
        image = null;
        imageContext = null;
        if (stagingTexture != 0) {
            GL11.glDeleteTextures(stagingTexture);
            stagingTexture = 0;
        }
        width = 0;
        height = 0;
        invalidate();
    }
}
