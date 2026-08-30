package cc.nimbus.utils.render.skija.api;

import cc.nimbus.utils.render.skija.internal.backend.SkijaRenderTarget;
import cc.nimbus.utils.render.skija.internal.backend.SkijaBackend;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.TriState;

import java.util.Objects;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SkijaLayer {
    private static final Map<ResourceLocation, RenderType> PREMULTIPLIED_TEXTURES = new ConcurrentHashMap<>();
    private static final RenderStateShard.TransparencyStateShard PREMULTIPLIED_ALPHA =
            new RenderStateShard.TransparencyStateShard("nimbus_premultiplied_alpha", () -> {
                com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                com.mojang.blaze3d.systems.RenderSystem.blendFunc(
                        GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            }, () -> {
                com.mojang.blaze3d.systems.RenderSystem.disableBlend();
                com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
            });
    private final SkijaRenderer renderer;
    private final SkijaRenderTarget target;

    SkijaLayer(SkijaRenderer renderer, String name) {
        this.renderer = renderer;
        this.target = new SkijaRenderTarget(name);
    }

    public ResourceLocation identifier() {
        return target.identifier();
    }

    public void ensure(int width, int height) {
        renderer.requireReady();
        target.ensure(width, height);
    }

    public void clear() {
        renderer.requireReady();
        target.clear();
    }

    public void releaseTarget() {
        target.close();
    }

    public void extract(GuiGraphics graphics, int width, int height) {
        Objects.requireNonNull(graphics, "graphics");
        ensure(width, height);
        //SCALE 取整
        int guiScale = Math.max(1, (int) Minecraft.getInstance().getWindow().getGuiScale());
        float scaleX = width / (float) guiScale / graphics.guiWidth();
        float scaleY = height / (float) guiScale / graphics.guiHeight();
        var pose = graphics.pose();
        pose.pushPose();
        try {
            pose.scale(scaleX, scaleY, 1.0f);
            graphics.blit(SkijaLayer::premultipliedTexture, identifier(),
                    0, 0, 0.0f, height,
                    graphics.guiWidth(), graphics.guiHeight(),
                    width, -height, width, height);
            graphics.flush();
        } finally {
            pose.popPose();
        }
    }

    private static RenderType premultipliedTexture(ResourceLocation id) {
        return PREMULTIPLIED_TEXTURES.computeIfAbsent(id, value -> RenderType.create(
                "nimbus_gui_premultiplied",
                DefaultVertexFormat.POSITION_TEX_COLOR,
                VertexFormat.Mode.QUADS,
                1536,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.POSITION_TEXTURE_COLOR_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(value, TriState.DEFAULT, false))
                        .setTransparencyState(PREMULTIPLIED_ALPHA)
                        .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                        .setOverlayState(RenderStateShard.NO_OVERLAY)
                        .createCompositeState(true)
        ));
    }

    public void render(SkijaFrameRenderer callback) {
        Objects.requireNonNull(callback, "callback");
        renderer.requireReady();
        try {
            target.render(canvas -> {
                SkijaFrame frame = new SkijaFrame(
                        canvas,
                        SkijaBackend.INSTANCE.getCurrentWidth(),
                        SkijaBackend.INSTANCE.getCurrentHeight(),
                        renderer
                );
                try {
                    callback.render(frame);
                } finally {
                    frame.release();
                }
            });
        } finally {
            renderer.finishFrame();
        }
    }

    void shutdown() {
        target.close();
    }
}
