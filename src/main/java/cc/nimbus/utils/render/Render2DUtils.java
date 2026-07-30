package cc.nimbus.utils.render;

import cc.nimbus.utils.render.skija.paint.SkColor;
import cc.nimbus.utils.render.skija.create.Skia;
import cc.nimbus.utils.render.skija.paint.PaintEffects;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.systems.RenderSystem;

import io.github.humbleui.skija.*;
import io.github.humbleui.skija.Shader;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;

import io.github.humbleui.skija.ColorType;
import io.github.humbleui.skija.SurfaceOrigin;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import org.lwjgl.opengl.GL11;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Skia-based 2D render helpers adapted from test Kotlin utilities.
 */
@SuppressWarnings({"deprecation", "unused"})
public final class Render2DUtils {
    private Render2DUtils() {}

    private static final com.mojang.blaze3d.vertex.ByteBufferBuilder LEGACY_BUFFER =
            new com.mojang.blaze3d.vertex.ByteBufferBuilder(256 * 1024);

    private static final Map<Identifier, Image> TEXTURE_CACHE = new ConcurrentHashMap<>();
    private static final Map<Identifier, Image> GL_IMAGE_CACHE = new ConcurrentHashMap<>();
    private static final Map<Identifier, Integer> GL_IMAGE_ID_CACHE = new ConcurrentHashMap<>();
    private static final Map<Identifier, Long> IMAGE_APPEAR_MS = new ConcurrentHashMap<>();
    private static final ConcurrentLinkedQueue<Image> PENDING_CLOSE = new ConcurrentLinkedQueue<>();
    private static Image capturedScene;
    private static volatile long FRAME_NOW_MS;

    public static void beginFrame() {
        FRAME_NOW_MS = System.currentTimeMillis();
    }

    public static long frameNowMs() {
        long now = FRAME_NOW_MS;
        if (now != 0L) return now;
        return System.currentTimeMillis();
    }

    public static float appearAlpha(Identifier loc, long durationMs) {
        if (loc == null) return 1f;
        if (durationMs <= 0L) return 1f;
        Long start = IMAGE_APPEAR_MS.get(loc);
        if (start == null) return 1f;
        long now = frameNowMs();
        long elapsed = now - start;
        if (elapsed <= 0L) return 0f;
        if (elapsed >= durationMs) {
            IMAGE_APPEAR_MS.remove(loc, start);
            return 1f;
        }
        return elapsed / (float) durationMs;
    }

    private static void markAppear(Identifier loc) {
        if (loc == null) return;
        IMAGE_APPEAR_MS.putIfAbsent(loc, frameNowMs());
        if (IMAGE_APPEAR_MS.size() > 4096) IMAGE_APPEAR_MS.clear();
    }

    /**
     * Optional scene capture for blur. Current pipeline supports canvas.drawAllBlur,
     * so this can be a no-op unless a specific blur-on-image is required.
     */
    public static void updateGameImage() {
        // No-op for now: main pipeline uses Canvas.drawAllBlur without a captured texture.
        // If needed later, we can adopt GL framebuffer into Skia Image.
    }

    public static void beginScissor(double x, double y, double endX, double endY) {
        double width = Math.max(0.0, endX - x);
        double height = Math.max(0.0, endY - y);
        double scale = Minecraft.getInstance().getWindow().getGuiScale();
        int sx = (int) (x * scale);
        int sy = (int) ((Minecraft.getInstance().getWindow().getGuiScaledHeight() - (y + height)) * scale);
        int sw = (int) (width * scale);
        int sh = (int) (height * scale);
        RenderSystem.enableScissorForRenderTypeDraws(sx, sy, sw, sh);
    }

    public static void endScissor() {
        RenderSystem.disableScissorForRenderTypeDraws();
    }

    public static void setupRender() {
        RenderSystem.assertOnRenderThread();
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    public static void endRender() {
        RenderSystem.assertOnRenderThread();
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_BLEND);
    }

    /**
     * 棰勫厛璁剧疆鐭╁舰鐨勫洓涓《鐐癸紙POSITION鏍煎紡锛夛紝杩斿洖鍙户缁～鍏呯殑BufferBuilder銆?     */
    public static com.mojang.blaze3d.vertex.BufferBuilder preShaderDraw(com.mojang.blaze3d.vertex.PoseStack matrices, float x, float y, float width, float height) {
        org.joml.Matrix4f matrix = matrices.last().pose();
        com.mojang.blaze3d.vertex.BufferBuilder buffer = new com.mojang.blaze3d.vertex.BufferBuilder(
                LEGACY_BUFFER, PrimitiveTopology.QUADS, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION);
        setRectanglePoints(buffer, matrix, x, y, x + width, y + height);
        return buffer;
    }

    /**
     * 璁剧疆鐭╁舰鍥涚偣锛圥OSITION鏍煎紡锛夈€?     */
    public static void setRectanglePoints(com.mojang.blaze3d.vertex.BufferBuilder buffer, org.joml.Matrix4f matrix, float x, float y, float x1, float y1) {
        buffer.addVertex(matrix, x, y1, 0.0f);
        buffer.addVertex(matrix, x1, y1, 0.0f);
        buffer.addVertex(matrix, x1, y, 0.0f);
        buffer.addVertex(matrix, x, y, 0.0f);
    }

    /**
     * 缁樺埗绾壊鐭╁舰锛圕olor閲嶈浇锛夈€?     */
    public static void drawRect(com.mojang.blaze3d.vertex.PoseStack matrices, float x, float y, float width, float height, Color c) {
        drawRect(matrices, x, y, width, height, c.getRGB());
    }

    /**
     * 缁樺埗绾壊鐭╁舰锛坕nt ARGB閲嶈浇锛夈€?     */
    public static void drawRect(com.mojang.blaze3d.vertex.PoseStack matrices, float x, float y, float width, float height, int argb) {
        drawRectSkia(x, y, width, height, argb);
    }

    /**
     * 缁樺埗鐭╁舰骞舵弿杈癸紙Color閲嶈浇锛夈€?     */
    public static void drawRectWithOutline(com.mojang.blaze3d.vertex.PoseStack matrices, float x, float y, float width, float height, Color fill, Color stroke) {
        drawRectWithOutline(matrices, x, y, width, height, fill.getRGB(), stroke.getRGB());
    }

    /**
     * 缁樺埗鐭╁舰骞舵弿杈癸紙int ARGB閲嶈浇锛夈€?     */
    public static void drawRectWithOutline(com.mojang.blaze3d.vertex.PoseStack matrices, float x, float y, float width, float height, int fillArgb, int strokeArgb) {
        drawRectWithOutlineSkia(x, y, width, height, fillArgb, strokeArgb, 1f);
    }

    /**
     * 缁樺埗绾圭悊锛圥OSITION_TEX锛夈€?     */
    public static void renderTexture(com.mojang.blaze3d.vertex.PoseStack matrices,
                                     double x0, double y0, double width, double height,
                                     float u, float v,
                                     double regionWidth, double regionHeight,
                                     double textureWidth, double textureHeight) {
        // The 26.2 renderer no longer exposes a globally bound texture. Call
        // drawSkiaImage(Identifier, ...) when rendering through the Skija canvas.
    }

    public static void drawSkiaRoundedRect(float x, float y, float width, float height, float radius, Color color) {
        drawSkiaRoundedRect(x, y, width, height, radius, color.getRGB());
    }

    public static void drawSkiaRoundedRect(float x, float y, float width, float height, float radius, int color) {
        Canvas canvas = Skia.INSTANCE.getCanvas();
        if (canvas == null) return;
        try (Paint fill = new Paint()) {
            fill.setColor(color);
            canvas.drawRRect(RRect.makeXYWH(x, y, width, height, radius), fill);
        }
    }

    public static void drawSkiaRoundedGradientRect(float x, float y, float width, float height, float radius, int[] colors, float[] positions) {
        Canvas canvas = Skia.INSTANCE.getCanvas();
        if (canvas == null) return;
        try (Paint paint = new Paint()) {
            paint.setShader(Shader.makeLinearGradient(
                    x, y, x + width, y,
                    colors,
                    positions
            ));
            canvas.drawRRect(RRect.makeXYWH(x, y, width, height, radius), paint);
        }
    }

    private static Image noiseTexture = null;


    public static Image getNoiseTexture() {
        if (noiseTexture != null) return noiseTexture;
        
        int width = 64;
        int height = 64;
        BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int alpha = (int) (Math.random() * 100);
                int val = (int) (Math.random() * 255);
                int color = (alpha << 24) | (val << 16) | (val << 8) | val;
                bi.setRGB(x, y, color);
            }
        }
        
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(bi, "png", baos);
            byte[] bytes = baos.toByteArray();
            noiseTexture = Image.makeFromEncoded(bytes);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return noiseTexture;
    }

    public static void drawSkiaRoundedShaderRect(float x, float y, float width, float height, float radius, Shader shader) {
        Canvas canvas = Skia.INSTANCE.getCanvas();
        if (canvas == null) return;
        try (Paint paint = new Paint()) {
            paint.setShader(shader);
            canvas.drawRRect(RRect.makeXYWH(x, y, width, height, radius), paint);
        }
    }

    public static void drawSkiaRoundedGlowRect(float x, float y, float width, float height, float radius, Color color) {
        Canvas canvas = Skia.INSTANCE.getCanvas();
        if (canvas == null) return;
        canvas.drawRRect(RRect.makeXYWH(x, y, width, height, radius),SkColor.makeHex(color.getRGB()).getPaint().setMaskFilter(PaintEffects.makeBlur(FilterBlurMode.SOLID, 10)).setAlpha(255));
    }
    public static void drawSkiaImage(Image image, Rect rect) {
        Canvas canvas = Skia.INSTANCE.getCanvas();
        if (canvas == null || image == null) return;
        canvas.drawImageRect(image, rect);
    }

    public static void drawSkiaImage(Identifier location, float x, float y, float imgWidth, float imgHeight) {
        Image image = getCachedImage(location);
        if (image == null) return;
        Canvas canvas = Skia.INSTANCE.getCanvas();
        if (canvas == null) return;
        canvas.drawImageRect(image, Rect.makeXYWH(x, y, imgWidth, imgHeight));
    }

    /**
     * 浠庣被璺緞瀛楃涓插姞杞藉苟缁樺埗 Skia 鍥惧儚銆?     */
    public static void drawSkiaImage(String path, float x, float y, float imgWidth, float imgHeight) {
        Canvas canvas = Skia.INSTANCE.getCanvas();
        if (canvas == null) return;
        try (var stream = Render2DUtils.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) return;
            Image image = Image.makeFromEncoded(stream.readAllBytes());
            canvas.drawImageRect(image, Rect.makeXYWH(x, y, imgWidth, imgHeight));
        } catch (Exception ignored) {
        }
    }

    public static void drawRectSkia(float x, float y, float width, float height, int argb) {
        Canvas canvas = Skia.INSTANCE.getCanvas();
        if (canvas == null) return;
        try (Paint fill = new Paint()) {
            fill.setColor(argb);
            canvas.drawRect(Rect.makeXYWH(x, y, width, height), fill);
        }
    }

    public static void drawRectWithOutlineSkia(float x, float y, float width, float height, int fillArgb, int strokeArgb, float strokeWidth) {
        Canvas canvas = Skia.INSTANCE.getCanvas();
        if (canvas == null) return;
        Rect r = Rect.makeXYWH(x, y, width, height);
        try (Paint fill = new Paint(); Paint stroke = new Paint()) {
            fill.setColor(fillArgb);
            canvas.drawRect(r, fill);
            stroke.setColor(strokeArgb);
            stroke.setStroke(true);
            stroke.setStrokeWidth(strokeWidth);
            canvas.drawRect(r, stroke);
        }
    }

    public static void renderEntityHead(Canvas canvas, Entity entity, float x, float y, float radius, float size, float alpha) {
        Identifier loc = getEntityTexture(entity);
        Image image = getGlImage(loc);
        if (image == null) image = getCachedImage(loc);
        
        if (canvas == null || image == null) return;
        try (Paint paint = new Paint()) {
            paint.setAntiAlias(false);
            paint.setAlphaf(alpha);
            canvas.save();
            canvas.clipRRect(RRect.makeXYWH(x, y, size, size, radius), true);
            canvas.drawImageRect(
                image,
                Rect.makeXYWH(8f, 8f, 8f, 8f),
                Rect.makeXYWH(x, y, size, size),
                SamplingMode.DEFAULT,
                paint,
                true
            );
            canvas.restore();
        }
    }

    public static Image getGlImage(Identifier loc) {
        if (loc == null) return null;
        Image skCached = TEXTURE_CACHE.get(loc);
        if (skCached != null && !skCached.isClosed()) return skCached;
        if ("nimbus".equals(loc.getNamespace()) && loc.getPath().startsWith("ncm/")) return null;
        try {
            var tm = Minecraft.getInstance().getTextureManager();
            net.minecraft.client.renderer.texture.AbstractTexture texture = tm.getTexture(loc);
            if (texture != null && texture.getTexture() instanceof GlTexture glTexture) {
                int id = glTexture.glId();
                if (id <= 0 || !GL11.glIsTexture(id)) return null;
                
                // Check cache first
                if (GL_IMAGE_CACHE.containsKey(loc)) {
                    Image cached = GL_IMAGE_CACHE.get(loc);
                    Integer cachedId = GL_IMAGE_ID_CACHE.get(loc);
                    if (cached != null && !cached.isClosed() && cachedId != null && cachedId == id && GL11.glIsTexture(id)) {
                        return cached;
                    } else {
                        // Clean up invalid cache
                        GL_IMAGE_CACHE.remove(loc);
                        GL_IMAGE_ID_CACHE.remove(loc);
                    }
                }

                int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
                int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
                int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
                
                if (width <= 0 || height <= 0) return null;

                Image image = Image.adoptGLTextureFrom(
                    Skia.INSTANCE.getContext(),
                    id,
                    GL11.GL_TEXTURE_2D,
                    width, height,
                    GL11.GL_RGBA8,
                    SurfaceOrigin.TOP_LEFT,
                    ColorType.RGBA_8888
                );
                
                GL_IMAGE_CACHE.put(loc, image);
                GL_IMAGE_ID_CACHE.put(loc, id);
                markAppear(loc);
                return image;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static Identifier getEntityTexture(Entity entity) {
        // Prefer actual client player skin when available
        if (entity instanceof AbstractClientPlayer clientPlayer) {
            // 鍦?.20.6涓紝浣跨敤getSkinLocation()鏂规硶
            return clientPlayer.getSkin().body().texturePath();
        }

        // Entity renderers now resolve textures from extracted render state.
        return Identifier.fromNamespaceAndPath("minecraft", "textures/entity/steve.png");
    }

    public static Identifier getPotionTexture(MobEffect effect) {
        if (effect == null) return null;
        Identifier id = BuiltInRegistries.MOB_EFFECT.getKey(effect);
        if (id != null) {
            return Identifier.fromNamespaceAndPath(id.getNamespace(), "textures/mob_effect/" + id.getPath() + ".png");
        }
        return null;
    }

    public static Image getCachedImage(Identifier loc) {
        Image cached = TEXTURE_CACHE.get(loc);
        if (cached != null && !cached.isClosed()) return cached;
        try {
            var opt = Minecraft.getInstance().getResourceManager().getResource(loc);
            if (opt.isPresent()) {
                try (var stream = opt.get().open()) {
                    Image img = Image.makeFromEncoded(stream.readAllBytes());
                    Image old = TEXTURE_CACHE.put(loc, img);
                    scheduleClose(old);
                    markAppear(loc);
                    return img;
                }
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    /**
     * 娉ㄥ唽 BufferedImage 涓烘父鎴忕汗鐞嗐€?     */
    public static void registerBufferedImageTexture(Identifier id, BufferedImage bi) {
        if (id == null || bi == null) return;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(bi, "png", baos);
            byte[] bytes = baos.toByteArray();
            boolean isNcm = "nimbus".equals(id.getNamespace()) && id.getPath() != null && id.getPath().startsWith("ncm/");
            if (!isNcm) {
                registerTexture(id, bytes);
            }
            Image skImage = Image.makeFromEncoded(bytes);
            Image old = TEXTURE_CACHE.put(id, skImage);
            scheduleClose(old);
            markAppear(id);
        } catch (Exception ignored) {
        }
    }

    /**
     * 灏?PNG 瀛楄妭鏁扮粍娉ㄥ唽涓哄姩鎬佺汗鐞嗐€?     */
    private static void registerTexture(Identifier id, byte[] content) {
        if (content == null || content.length == 0) return;
        try {
            NativeImage nativeImage = NativeImage.read(new ByteArrayInputStream(content));
            if (nativeImage == null) return;
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                try {
                    DynamicTexture tex = new DynamicTexture(() -> "Nimbus dynamic texture " + id, nativeImage);
                    mc.getTextureManager().register(id, tex);
                } catch (Exception e) {
                    try {
                        nativeImage.close();
                    } catch (Exception ignored) {
                    }
                }
            });
        } catch (Exception ignored) {
        }
    }

    public static void drainPendingCloses() {
        for (int i = 0; i < 64; i++) {
            Image img = PENDING_CLOSE.poll();
            if (img == null) return;
            try {
                if (!img.isClosed()) img.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static void scheduleClose(Image img) {
        if (img == null) return;
        if (img.isClosed()) return;
        PENDING_CLOSE.add(img);
    }
}

