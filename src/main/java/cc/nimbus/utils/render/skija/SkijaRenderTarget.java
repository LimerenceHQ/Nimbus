package cc.nimbus.utils.render.skija;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.*;
import io.github.humbleui.skija.Canvas;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL30;
import cc.nimbus.utils.render.NativeSupport;
import cc.nimbus.utils.render.gl.States;
import cc.nimbus.utils.render.skija.create.Skia;

import java.util.OptionalDouble;
import java.util.function.Consumer;

public final class SkijaRenderTarget implements AutoCloseable {
    private final Identifier identifier;
    private Texture registeredTexture;
    private int framebuffer;
    private int depthStencilRenderbuffer;
    private int width, height;

    public SkijaRenderTarget(String name) { identifier = Identifier.fromNamespaceAndPath("nimbus", "skija/" + name); }
    public Identifier identifier() { return identifier; }

    public void ensure(int width, int height) {
        RenderSystem.assertOnRenderThread();
        NativeSupport.validateBackend();
        if (!NativeSupport.isSupported()) throw new IllegalStateException(NativeSupport.getUnsupportedReason());
        if (registeredTexture != null && this.width == width && this.height == height) {
            Skia.INSTANCE.update(framebuffer, this.width, this.height);
            return;
        }
        States.push();
        int previousDrawFramebuffer = GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousReadFramebuffer = GL30.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousRenderbuffer = GL30.glGetInteger(GL30.GL_RENDERBUFFER_BINDING);
        try {
            destroy();
            this.width = Math.max(1, width); this.height = Math.max(1, height);
            var device = RenderSystem.getDevice();
            GpuTexture texture = device.createTexture("Nimbus Skija Color", GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC, GpuFormat.RGBA8_UNORM, this.width, this.height, 1, 1);
            GpuTextureView view = device.createTextureView(texture);
            GpuSampler sampler = device.createSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.NEAREST, FilterMode.NEAREST, 1, OptionalDouble.empty());
            registeredTexture = new Texture(texture, view, sampler);
            Minecraft.getInstance().getTextureManager().register(identifier, registeredTexture);
            if (!(texture instanceof GlTexture glTexture)) { destroy(); throw new IllegalStateException("Nimbus Skija only supports Minecraft's OpenGL device"); }
            framebuffer = GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL30.GL_TEXTURE_2D, glTexture.glId(), 0);
            depthStencilRenderbuffer = GL30.glGenRenderbuffers();
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depthStencilRenderbuffer);
            GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH24_STENCIL8, this.width, this.height);
            GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT,
                    GL30.GL_RENDERBUFFER, depthStencilRenderbuffer);
            int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
            if (status != GL30.GL_FRAMEBUFFER_COMPLETE) { destroy(); throw new IllegalStateException("Incomplete Nimbus Skija framebuffer: 0x" + Integer.toHexString(status)); }
            Skia.INSTANCE.update(framebuffer, this.width, this.height);
        } finally {
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, previousRenderbuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            States.pop();
        }
    }

    public void clear() {
        if (registeredTexture != null) {
            RenderSystem.getDevice().createCommandEncoder().clearColorTexture(registeredTexture.getTexture(), new Vector4f(0, 0, 0, 0));
        }
    }

    public void render(Consumer<Canvas> renderer) {
        if (registeredTexture == null) return;
        States.push();
        int previousDrawFramebuffer = GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousReadFramebuffer = GL30.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        try {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
            GL30.glViewport(0, 0, width, height);
            GL30.glClearStencil(0);
            GL30.glClearDepth(1.0);
            GL30.glClear(GL30.GL_DEPTH_BUFFER_BIT | GL30.GL_STENCIL_BUFFER_BIT);
            Skia.INSTANCE.getContext().resetGLAll();
            Canvas canvas = Skia.INSTANCE.getCanvas();
            canvas.clear(0);
            renderer.accept(canvas);
            Skia.INSTANCE.flushAndSubmit();
        } finally {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            States.pop();
        }
    }
    private void destroy() {
        Skia.INSTANCE.closeAll();
        if (depthStencilRenderbuffer != 0) { GL30.glDeleteRenderbuffers(depthStencilRenderbuffer); depthStencilRenderbuffer = 0; }
        if (framebuffer != 0) { GL30.glDeleteFramebuffers(framebuffer); framebuffer = 0; }
        if (registeredTexture != null) { Minecraft.getInstance().getTextureManager().release(identifier); registeredTexture = null; }
        width = height = 0;
    }
    @Override public void close() { RenderSystem.assertOnRenderThread(); destroy(); }
}
