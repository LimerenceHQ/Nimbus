package cc.nimbus.utils.render.skija.internal.backend;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.humbleui.skija.Canvas;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import cc.nimbus.utils.render.skija.internal.backend.gl.States;
import cc.nimbus.utils.render.skija.internal.backend.SkijaBackend;

import java.util.function.Consumer;

public final class SkijaRenderTarget implements AutoCloseable {
    private final ResourceLocation identifier;
    private Texture registeredTexture;
    private int framebuffer;
    private int depthStencilRenderbuffer;
    private int width, height;

    public SkijaRenderTarget(String name) { identifier = new ResourceLocation("nimbus", "skija/" + name); }
    public ResourceLocation identifier() { return identifier; }

    public void ensure(int width, int height) {
        RenderSystem.assertOnRenderThread();
        SkijaSupport.validateBackend();
        if (!SkijaSupport.isSupported()) throw new IllegalStateException(SkijaSupport.getUnsupportedReason());
        if (registeredTexture != null && this.width == width && this.height == height) {
            SkijaBackend.INSTANCE.update(framebuffer, this.width, this.height);
            return;
        }
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        States.push();
        int previousDrawFramebuffer = GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousReadFramebuffer = GL30.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousRenderbuffer = GL30.glGetInteger(GL30.GL_RENDERBUFFER_BINDING);
        try {
            destroy();
            this.width = Math.max(1, width); this.height = Math.max(1, height);
            int textureId = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, this.width, this.height,
                    0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
            registeredTexture = new Texture(textureId);
            Minecraft.getInstance().getTextureManager().register(identifier, registeredTexture);
            framebuffer = GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL30.GL_TEXTURE_2D, textureId, 0);
            depthStencilRenderbuffer = GL30.glGenRenderbuffers();
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depthStencilRenderbuffer);
            GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH24_STENCIL8, this.width, this.height);
            GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT,
                    GL30.GL_RENDERBUFFER, depthStencilRenderbuffer);
            int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
            if (status != GL30.GL_FRAMEBUFFER_COMPLETE) { destroy(); throw new IllegalStateException("Incomplete Nimbus Skija framebuffer: 0x" + Integer.toHexString(status)); }
            SkijaBackend.INSTANCE.update(framebuffer, this.width, this.height);
        } finally {
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, previousRenderbuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            States.pop();
            GlStateManager._activeTexture(previousActiveTexture);
            GlStateManager._bindTexture(previousTexture);
        }
    }

    public void clear() {
        RenderSystem.assertOnRenderThread();
        if (registeredTexture == null) return;
        States.push();
        int previousDrawFramebuffer = GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousReadFramebuffer = GL30.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        try {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
            GL30.glDisable(GL30.GL_SCISSOR_TEST);
            GL30.glColorMask(true, true, true, true);
            GL30.glClearColor(0f, 0f, 0f, 0f);
            GL30.glClear(GL30.GL_COLOR_BUFFER_BIT);
        } finally {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            States.pop();
        }
    }

    public void render(Consumer<Canvas> renderer) {
        RenderSystem.assertOnRenderThread();
        if (registeredTexture == null) return;
        States.push();
        int previousDrawFramebuffer = GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousReadFramebuffer = GL30.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        try {
            SkijaBackend.INSTANCE.update(framebuffer, width, height);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
            GL30.glViewport(0, 0, width, height);
            GL30.glClearStencil(0);
            GL30.glClearDepth(1.0);
            GL30.glClear(GL30.GL_DEPTH_BUFFER_BIT | GL30.GL_STENCIL_BUFFER_BIT);
            SkijaBackend.INSTANCE.getContext().resetGLAll();
            Canvas canvas = SkijaBackend.INSTANCE.getCanvas();
            canvas.clear(0);
            renderer.accept(canvas);
            SkijaBackend.INSTANCE.flushAndSubmit();
        } finally {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            States.pop();
        }
    }
    private void destroy() {
        if (framebuffer != 0) SkijaBackend.INSTANCE.detach(framebuffer);
        if (depthStencilRenderbuffer != 0) { GL30.glDeleteRenderbuffers(depthStencilRenderbuffer); depthStencilRenderbuffer = 0; }
        if (framebuffer != 0) { GL30.glDeleteFramebuffers(framebuffer); framebuffer = 0; }
        if (registeredTexture != null) { Minecraft.getInstance().getTextureManager().release(identifier); registeredTexture = null; }
        width = height = 0;
    }
    @Override public void close() { RenderSystem.assertOnRenderThread(); destroy(); }
}
