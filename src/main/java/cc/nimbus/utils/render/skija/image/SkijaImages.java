package cc.nimbus.utils.render.skija.image;

import cc.nimbus.utils.render.skija.internal.SkijaBackend;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.humbleui.skija.ColorType;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.SurfaceOrigin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded, Skija-owned copies of Minecraft and resource textures. */
public final class SkijaImages {
    private static final int MAX_IMAGES = 256;
    private static final Map<Identifier, Image> RESOURCE_IMAGES = new LinkedHashMap<>(32, 0.75f, true);
    private static final Map<Identifier, GlImage> GL_IMAGES = new LinkedHashMap<>(32, 0.75f, true);
    private static final ArrayDeque<Image> PENDING_CLOSE = new ArrayDeque<>();

    private SkijaImages() {
    }

    public static Image getGlImage(Identifier id) {
        if (id == null || SkijaBackend.INSTANCE.getContext() == null) return null;
        RenderSystem.assertOnRenderThread();

        GlImage cached = GL_IMAGES.get(id);
        var texture = Minecraft.getInstance().getTextureManager().getTexture(id);
        if (texture == null || !(texture.getTexture() instanceof GlTexture glTexture)) return null;
        int sourceTexture = glTexture.glId();
        if (sourceTexture <= 0 || !GL11.glIsTexture(sourceTexture)) return null;
        if (cached != null && cached.sourceTexture() == sourceTexture && !cached.image().isClosed()) {
            return cached.image();
        }
        if (cached != null) scheduleClose(cached.image());

        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, sourceTexture);
        int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
        if (width <= 0 || height <= 0) return null;

        int copy = copyTexture(sourceTexture, width, height);
        Image image;
        try {
            image = Image.adoptGLTextureFrom(
                    SkijaBackend.INSTANCE.getContext(), copy, GL11.GL_TEXTURE_2D,
                    width, height, GL11.GL_RGBA8,
                    SurfaceOrigin.TOP_LEFT, ColorType.RGBA_8888
            );
        } catch (RuntimeException error) {
            GL11.glDeleteTextures(copy);
            throw error;
        }
        GL_IMAGES.put(id, new GlImage(sourceTexture, image));
        trim();
        return image;
    }

    public static Image getResourceImage(Identifier id) {
        if (id == null) return null;
        Image cached = RESOURCE_IMAGES.get(id);
        if (cached != null && !cached.isClosed()) return cached;
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(id);
            if (resource.isEmpty()) return null;
            try (var stream = resource.get().open()) {
                Image image = Image.makeDeferredFromEncodedBytes(stream.readAllBytes());
                RESOURCE_IMAGES.put(id, image);
                trim();
                return image;
            }
        } catch (IOException error) {
            return null;
        }
    }

    public static Identifier getEntityTexture(Entity entity) {
        if (entity instanceof AbstractClientPlayer player) {
            return player.getSkin().body().texturePath();
        }
        return Identifier.fromNamespaceAndPath("minecraft", "textures/entity/steve.png");
    }

    public static void drainPendingCloses() {
        Image image;
        while ((image = PENDING_CLOSE.poll()) != null) close(image);
    }

    public static void clear() {
        RESOURCE_IMAGES.values().forEach(SkijaImages::scheduleClose);
        GL_IMAGES.values().forEach(value -> scheduleClose(value.image()));
        RESOURCE_IMAGES.clear();
        GL_IMAGES.clear();
        drainPendingCloses();
    }

    private static void trim() {
        while (RESOURCE_IMAGES.size() + GL_IMAGES.size() > MAX_IMAGES) {
            if (!RESOURCE_IMAGES.isEmpty()) {
                Identifier key = RESOURCE_IMAGES.keySet().iterator().next();
                scheduleClose(RESOURCE_IMAGES.remove(key));
            } else {
                Identifier key = GL_IMAGES.keySet().iterator().next();
                scheduleClose(GL_IMAGES.remove(key).image());
            }
        }
    }

    private static void scheduleClose(Image image) {
        if (image != null && !image.isClosed()) PENDING_CLOSE.add(image);
    }

    private static void close(Image image) {
        if (image != null && !image.isClosed()) image.close();
    }

    private static int copyTexture(int sourceTexture, int width, int height) {
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int readFramebuffer = 0;
        int drawFramebuffer = 0;
        int copy = 0;
        try {
            copy = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, copy);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height,
                    0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);

            readFramebuffer = GL30.glGenFramebuffers();
            drawFramebuffer = GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, sourceTexture, 0);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, copy, 0);
            if (GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE
                    || GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException("Cannot copy Minecraft texture into Skija cache");
            }
            GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height,
                    GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
            return copy;
        } catch (RuntimeException error) {
            if (copy != 0) GL11.glDeleteTextures(copy);
            throw error;
        } finally {
            if (readFramebuffer != 0) GL30.glDeleteFramebuffers(readFramebuffer);
            if (drawFramebuffer != 0) GL30.glDeleteFramebuffers(drawFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
        }
    }

    private record GlImage(int sourceTexture, Image image) {
    }
}
