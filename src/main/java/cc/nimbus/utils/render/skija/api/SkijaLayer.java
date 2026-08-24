package cc.nimbus.utils.render.skija.api;

import cc.nimbus.utils.render.skija.internal.backend.SkijaRenderTarget;
import cc.nimbus.utils.render.skija.internal.backend.SkijaBackend;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public final class SkijaLayer {
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
            // Skija's framebuffer has a bottom-left origin; GuiGraphics is top-left.
            // Skija stores colors premultiplied. Flush this blit while using the
            // matching ONE/ONE_MINUS_SRC_ALPHA blend equation; the vanilla GUI
            // state uses SRC_ALPHA and produces dark halos around translucent HUD
            // pixels and blur regions.
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            graphics.blit(identifier(), 0, 0, graphics.guiWidth(), graphics.guiHeight(),
                    0F, height, width, -height, width, height);
            graphics.flush();
            RenderSystem.defaultBlendFunc();
        } finally {
            pose.popPose();
        }
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
