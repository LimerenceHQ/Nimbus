package cc.nimbus.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import cc.nimbus.event.EventBus;
import cc.nimbus.event.impl.render.EventRender2D;
import cc.nimbus.core.Core;
import cc.nimbus.utils.render.skija.api.SkijaLayer;
import cc.nimbus.utils.render.skija.api.SkijaRenderer;
import cc.nimbus.ui.screen.AbstractSkijaScreen;

import java.util.*;

@Mixin(GuiRenderer.class)
public class GuiRendererMixin {
    @Shadow @Final private FeatureRenderDispatcher featureRenderDispatcher;
    @Shadow @Final private Map<Class<? extends PictureInPictureRenderState>, PictureInPictureRenderer<?>> pictureInPictureRenderers;
    @Unique private GuiRenderState nimbus$state;
    @Unique private GuiRenderer nimbus$renderer;
    @Unique private SkijaLayer nimbus$hudLayer;
    @Unique private AbstractSkijaScreen nimbus$drawnScreen;
    @Unique private ClientLevel nimbus$scene;
    @Unique private long nimbus$nextHudUpdateNanos;
    @Unique private int nimbus$hudRefreshRate;
    @Unique private int nimbus$hudWidth;
    @Unique private int nimbus$hudHeight;
    @Unique private boolean nimbus$hudHadContent;
    @Unique private static final ThreadLocal<Boolean> NIMBUS_RENDERING_AUXILIARY = ThreadLocal.withInitial(() -> false);

    @Inject(method = "draw", at = @At("HEAD"))
    private void nimbus$draw(CallbackInfo ci) {
        if (NIMBUS_RENDERING_AUXILIARY.get()) return;
        nimbus$drawnScreen = null;
        Minecraft minecraft = Minecraft.getInstance();
        SkijaRenderer skija = Core.INSTANCE.getRenderManager().skija();
        skija.validateSupport();
        if (!skija.isSupported()) return;

        if (nimbus$scene != minecraft.level) {
            nimbus$scene = minecraft.level;
            skija.invalidateScene();
        }

        if (minecraft.gui.screen() instanceof AbstractSkijaScreen screen) {
            skija.configurePostProcess(Core.INSTANCE.getConfigManager().clientSettings().postProcessOptions());
            var options = skija.postProcessOptions();
            int captureRate = screen.requestedSceneCaptureRate(options);
            if (captureRate > 0) {
                skija.captureScene(captureRate);
            }
            screen.renderExtractedFrame();
            nimbus$drawnScreen = screen;
        }

        if (nimbus$renderer == null) {
            nimbus$state = new GuiRenderState();
            nimbus$renderer = new GuiRenderer(nimbus$state, featureRenderDispatcher, new ArrayList<>(pictureInPictureRenderers.values()));
        }
        int mouseX = (int) minecraft.mouseHandler.getScaledXPos(minecraft.getWindow());
        int mouseY = (int) minecraft.mouseHandler.getScaledYPos(minecraft.getWindow());
        GuiGraphicsExtractor graphics = new GuiGraphicsExtractor(minecraft, nimbus$state, mouseX, mouseY);
        boolean renderHud = minecraft.level != null && minecraft.player != null
                && (minecraft.gui.screen() == null || minecraft.gui.screen() instanceof ChatScreen);
        if (renderHud) {
            if (nimbus$hudLayer == null) nimbus$hudLayer = skija.layer("hud");
            var mainRenderTarget = minecraft.gameRenderer.mainRenderTarget();
            int framebufferWidth = mainRenderTarget.width;
            int framebufferHeight = mainRenderTarget.height;
            nimbus$hudLayer.ensure(framebufferWidth, framebufferHeight);
            boolean resized = framebufferWidth != nimbus$hudWidth || framebufferHeight != nimbus$hudHeight;
            nimbus$hudWidth = framebufferWidth;
            nimbus$hudHeight = framebufferHeight;
            var clientSettings = Core.INSTANCE.getConfigManager().clientSettings();
            skija.configurePostProcess(clientSettings.postProcessOptions());
            var options = skija.postProcessOptions();
            EventBus.call(new EventRender2D(graphics));
            boolean hasPostProcess = skija.postProcess().hasRegions();
            boolean hasContent = hasPostProcess || skija.hud().hasPendingContent();
            boolean forceUpdate = resized || hasContent != nimbus$hudHadContent;
            boolean updateHud = hasContent || nimbus$hudHadContent;
            int hudRefreshRate = clientSettings.hudRefreshRate();
            if (updateHud && nimbus$hudUpdateDue(hudRefreshRate, forceUpdate)) {
                if (options.blurEnabled() && hasPostProcess) {
                    skija.captureScene(Math.min(options.blurRefreshRate(), hudRefreshRate));
                }
                nimbus$hudLayer.render(frame -> skija.hud().render());
                nimbus$hudHadContent = hasContent;
            } else {
                skija.hud().discardFrame();
            }
            if (nimbus$hudHadContent) {
                nimbus$hudLayer.extract(graphics, framebufferWidth, framebufferHeight);
            }
        } else {
            nimbus$resetHudCadence();
        }

        NIMBUS_RENDERING_AUXILIARY.set(true);
        try {
            nimbus$renderer.render();
            nimbus$renderer.endFrame();
        } finally {
            NIMBUS_RENDERING_AUXILIARY.set(false);
        }
    }

    @Inject(method = "draw", at = @At("RETURN"))
    private void nimbus$framePresented(CallbackInfo ci) {
        if (NIMBUS_RENDERING_AUXILIARY.get()) return;
        AbstractSkijaScreen screen = nimbus$drawnScreen;
        nimbus$drawnScreen = null;
        if (screen != null) screen.extractedFramePresented();
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void nimbus$close(CallbackInfo ci) {
        if (nimbus$hudLayer != null) nimbus$hudLayer.releaseTarget();
        nimbus$hudLayer = null;
        nimbus$hudWidth = nimbus$hudHeight = 0;
        nimbus$hudHadContent = false;
        nimbus$resetHudCadence();
        if (nimbus$renderer != null) nimbus$renderer.close();
        nimbus$renderer = null; nimbus$state = null;
    }

    @Unique
    private boolean nimbus$hudUpdateDue(int refreshRate, boolean force) {
        refreshRate = Math.max(1, refreshRate);
        long now = System.nanoTime();
        long interval = Math.max(1L, 1_000_000_000L / refreshRate);
        if (refreshRate != nimbus$hudRefreshRate) {
            nimbus$hudRefreshRate = refreshRate;
            nimbus$nextHudUpdateNanos = 0L;
            force = true;
        }
        if (force || nimbus$nextHudUpdateNanos == 0L) {
            nimbus$nextHudUpdateNanos = now + interval;
            return true;
        }
        if (now < nimbus$nextHudUpdateNanos) return false;

        long elapsedIntervals = (now - nimbus$nextHudUpdateNanos) / interval + 1L;
        nimbus$nextHudUpdateNanos += elapsedIntervals * interval;
        return true;
    }

    @Unique
    private void nimbus$resetHudCadence() {
        nimbus$nextHudUpdateNanos = 0L;
        nimbus$hudRefreshRate = 0;
    }
}
