package cc.nimbus.utils.render.skija.internal.image.minecraft;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import io.github.humbleui.skija.BackendTexture;
import io.github.humbleui.skija.ColorAlphaType;
import io.github.humbleui.skija.ColorType;
import io.github.humbleui.skija.DirectContext;
import io.github.humbleui.skija.GLTextureInfo;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.SurfaceOrigin;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import cc.nimbus.utils.render.skija.internal.backend.gl.States;
import cc.nimbus.utils.render.skija.internal.backend.SkijaBackend;
import cc.nimbus.utils.render.skija.internal.backend.SkijaSupport;

public final class MinecraftSnapshot {
    private static Image image;
    private static DirectContext imageContext;
    private static GpuTexture stagingTexture;
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
                    GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST,
                    com.mojang.blaze3d.textures.TextureFormat.RGBA8,
                    width, height, 1, 1
            );
        }

        var encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(target.getColorTexture(), stagingTexture,
                0, 0, 0, 0, 0, width, height);

        if (!(stagingTexture instanceof GlTexture stagingGlTexture) || stagingGlTexture.isClosed()) {
            return false;
        }
        if (image == null || image.isClosed()) {
            try (BackendTexture texture = BackendTexture.makeGL(
                    width, height, false,
                    new GLTextureInfo(GL11.GL_TEXTURE_2D, stagingGlTexture.glId(), GL11.GL_RGBA8))) {
                image = Image.borrowTextureFrom(
                        context, texture, SurfaceOrigin.BOTTOM_LEFT, ColorType.RGB_888X,
                        ColorAlphaType.OPAQUE, null, null
                );
            }
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

    public static synchronized void clear() {
        if (image != null && !image.isClosed()) image.close();
        image = null;
        imageContext = null;
        if (stagingTexture != null) {
            stagingTexture.close();
            stagingTexture = null;
        }
        width = 0;
        height = 0;
        invalidate();
    }
}
