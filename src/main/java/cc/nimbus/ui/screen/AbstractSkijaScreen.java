package cc.nimbus.ui.screen;

import cc.nimbus.core.Core;
import cc.nimbus.utils.render.skija.api.PostProcessOptions;
import cc.nimbus.utils.render.skija.api.SkijaFrame;
import cc.nimbus.utils.render.skija.api.SkijaLayer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class AbstractSkijaScreen extends Screen {
    private final SkijaLayer layer;
    private int framebufferWidth;
    private int framebufferHeight;
    private float framebufferMouseX;
    private float framebufferMouseY;

    protected AbstractSkijaScreen(Component title, String layerName) {
        super(title);
        layer = Core.INSTANCE.getRenderManager().skija().layer(layerName);
    }

    @Override
    public final void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        RenderTarget target = minecraft.getMainRenderTarget();
        framebufferWidth = target.width;
        framebufferHeight = target.height;
        framebufferMouseX = toFramebufferX(mouseX, graphics.guiWidth(), framebufferWidth);
        framebufferMouseY = toFramebufferY(mouseY, graphics.guiHeight(), framebufferHeight);
        var skija = Core.INSTANCE.getRenderManager().skija();
        skija.configurePostProcess(Core.INSTANCE.getConfigManager().clientSettings().postProcessOptions());
        var options = skija.postProcessOptions();
        int captureRate = requestedSceneCaptureRate(options);
        if (captureRate > 0) skija.captureScene(captureRate);
        layer.ensure(framebufferWidth, framebufferHeight);
        layer.clear();
        layer.render(this::renderSkija);
        layer.extract(graphics, framebufferWidth, framebufferHeight);
        extractedSkijaFramePresented();
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    protected void extractedSkijaFramePresented() {
    }

    protected abstract void renderSkija(SkijaFrame frame);

    public int requestedSceneCaptureRate(PostProcessOptions options) {
        return options.blurEnabled() ? options.blurRefreshRate() : 0;
    }

    protected final int framebufferWidth() {
        return framebufferWidth;
    }

    protected final int framebufferHeight() {
        return framebufferHeight;
    }

    protected final float framebufferMouseX() {
        return framebufferMouseX;
    }

    protected final float framebufferMouseY() {
        return framebufferMouseY;
    }

    protected final float framebufferX(double guiX) {
        return toFramebufferX(guiX, width, minecraft.getMainRenderTarget().width);
    }

    protected final float framebufferY(double guiY) {
        return toFramebufferY(guiY, height, minecraft.getMainRenderTarget().height);
    }

    @Override
    public final void removed() {
        try {
            removedSkijaScreen();
        } finally {
            minecraft.execute(layer::releaseTarget);
            super.removed();
        }
    }

    protected void removedSkijaScreen() {
    }

    private static float toFramebufferX(double x, int guiWidth, int framebufferWidth) {
        return guiWidth > 0 ? (float) (x * framebufferWidth / guiWidth) : (float) x;
    }

    private static float toFramebufferY(double y, int guiHeight, int framebufferHeight) {
        return guiHeight > 0 ? (float) (y * framebufferHeight / guiHeight) : (float) y;
    }
}
