package cc.nimbus.utils.render.skija.api;

import cc.nimbus.utils.render.skija.internal.backend.SkijaRenderTarget;
import cc.nimbus.utils.render.skija.internal.backend.SkijaBackend;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.Objects;

public final class SkijaLayer {
    private final SkijaRenderer renderer;
    private final SkijaRenderTarget target;

    SkijaLayer(SkijaRenderer renderer, String name) {
        this.renderer = renderer;
        this.target = new SkijaRenderTarget(name);
    }

    public Identifier identifier() {
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

    public void extract(GuiGraphicsExtractor graphics, int width, int height) {
        Objects.requireNonNull(graphics, "graphics");
        ensure(width, height);
        //SCALE 取整
        int guiScale = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
        float scaleX = width / (float) guiScale / graphics.guiWidth();
        float scaleY = height / (float) guiScale / graphics.guiHeight();
        var pose = graphics.pose();
        pose.pushMatrix();
        try {
            pose.scale(scaleX, scaleY);
            graphics.blit(
                    RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
                    identifier(),
                    0, 0, 0F, height,
                    graphics.guiWidth(), graphics.guiHeight(),
                    width, -height, width, height
            );
        } finally {
            pose.popMatrix();
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
