package cc.nimbus.utils.render.skija.internal.image.minecraft;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import io.github.humbleui.skija.ColorType;
import io.github.humbleui.skija.DirectContext;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.SurfaceOrigin;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import cc.nimbus.utils.render.skija.internal.backend.gl.States;
import cc.nimbus.utils.render.skija.internal.backend.SkijaBackend;
import cc.nimbus.utils.render.skija.internal.backend.SkijaSupport;

import java.nio.ByteBuffer;

public final class MinecraftSnapshot {
    private static Image image;
    private static DirectContext imageContext;
    private static GpuTexture stagingTexture;
    private static int textureId;
    private static int readFramebuffer;
    private static int drawFramebuffer;
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

        RenderTarget target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        if (!(target.getColorTexture() instanceof GlTexture sourceTexture) || sourceTexture.isClosed()) {
            return false;
        }

        int targetWidth = target.width;
        int targetHeight = target.height;
        if (targetWidth <= 0 || targetHeight <= 0
                || sourceTexture.getWidth(0) < targetWidth || sourceTexture.getHeight(0) < targetHeight) {
            return false;
        }

        if (image == null || image.isClosed() || imageContext != context
                || width != targetWidth || height != targetHeight) {
            clear();
            width = targetWidth;
            height = targetHeight;
            imageContext = context;

            stagingTexture = RenderSystem.getDevice().createTexture(
                    "Nimbus Skija Scene Snapshot",
                    GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC,
                    GpuFormat.RGBA8_UNORM,
                    width, height, 1, 1
            );

            int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            textureId = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height,
                    0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);

            readFramebuffer = GL30.glGenFramebuffers();
            drawFramebuffer = GL30.glGenFramebuffers();
        }

        var encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(target.getColorTexture(), stagingTexture,
                0, 0, 0, 0, 0, width, height);
        encoder.submit();

        if (!(stagingTexture instanceof GlTexture stagingGlTexture) || stagingGlTexture.isClosed()) {
            return false;
        }
        copyTexture(stagingGlTexture.glId());
        if (image == null || image.isClosed()) {
            image = Image.adoptGLTextureFrom(
                    context,
                    textureId,
                    GL11.GL_TEXTURE_2D,
                    width,
                    height,
                    GL11.GL_RGBA8,
                    SurfaceOrigin.BOTTOM_LEFT,
                    ColorType.RGB_888X
            );
        }
        context.resetGLAll();
        return true;
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

    private static void copyTexture(int sourceTextureId) {
        int previousReadFramebuffer = GL30.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, sourceTextureId, 0);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, textureId, 0);
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
            int readStatus = GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER);
            int drawStatus = GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER);
            if (readStatus != GL30.GL_FRAMEBUFFER_COMPLETE || drawStatus != GL30.GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException("Incomplete Skija snapshot framebuffer: read=0x"
                        + Integer.toHexString(readStatus) + ", draw=0x" + Integer.toHexString(drawStatus));
            }
            GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height,
                    GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
        }
    }

    public static synchronized void clear() {
        if (image != null) {
            if (!image.isClosed()) image.close();
        } else if (textureId != 0) {
            GL11.glDeleteTextures(textureId);
        }
        image = null;
        imageContext = null;
        textureId = 0;
        if (readFramebuffer != 0) {
            GL30.glDeleteFramebuffers(readFramebuffer);
            readFramebuffer = 0;
        }
        if (drawFramebuffer != 0) {
            GL30.glDeleteFramebuffers(drawFramebuffer);
            drawFramebuffer = 0;
        }
        if (stagingTexture != null) {
            stagingTexture.close();
            stagingTexture = null;
        }
        width = 0;
        height = 0;
        invalidate();
    }
}
