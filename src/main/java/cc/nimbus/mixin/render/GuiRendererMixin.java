package cc.nimbus.mixin.render;

import cc.nimbus.core.Core;
import cc.nimbus.event.EventBus;
import cc.nimbus.event.impl.render.EventRender2D;
import cc.nimbus.features.music.integration.MusicToastState;
import cc.nimbus.utils.render.skija.api.SkijaLayer;
import cc.nimbus.utils.render.skija.api.SkijaRenderer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {
    @Shadow @Final GuiRenderState renderState;
    @Unique private SkijaLayer nimbus$hudLayer;
    @Unique private int nimbus$hudWidth;
    @Unique private int nimbus$hudHeight;
    @Unique private boolean nimbus$hudHadContent;
    @Unique private long nimbus$nextHudUpdateNanos;
    @Unique private int nimbus$hudRefreshRate;
    @Unique private ClientLevel nimbus$scene;

    @Inject(method = "render", at = @At("HEAD"))
    private void nimbus$render(GpuBufferSlice projection, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        SkijaRenderer skija = Core.INSTANCE.getRenderManager().skija();
        skija.validateSupport();
        if (!skija.isSupported() || minecraft.level == null || minecraft.player == null
                || !(minecraft.screen == null || minecraft.screen instanceof ChatScreen)) {
            nimbus$resetHudCadence();
            return;
        }
        if (nimbus$scene != minecraft.level) {
            nimbus$scene = minecraft.level;
            skija.invalidateScene();
        }

        if (nimbus$hudLayer == null) nimbus$hudLayer = skija.layer("hud");
        var target = minecraft.getMainRenderTarget();
        int framebufferWidth = target.width;
        int framebufferHeight = target.height;
        nimbus$hudLayer.ensure(framebufferWidth, framebufferHeight);
        boolean resized = framebufferWidth != nimbus$hudWidth || framebufferHeight != nimbus$hudHeight;
        nimbus$hudWidth = framebufferWidth;
        nimbus$hudHeight = framebufferHeight;

        var settings = Core.INSTANCE.getConfigManager().clientSettings();
        skija.configurePostProcess(settings.postProcessOptions());
        GuiGraphics graphics = new GuiGraphics(minecraft, renderState,
                (int) minecraft.mouseHandler.getScaledXPos(minecraft.getWindow()),
                (int) minecraft.mouseHandler.getScaledYPos(minecraft.getWindow()));
        EventBus.call(new EventRender2D(graphics));
        boolean hasPostProcess = skija.postProcess().hasRegions();
        boolean hasContent = hasPostProcess || skija.hud().hasPendingContent();
        boolean forceUpdate = resized || hasContent != nimbus$hudHadContent;
        int refreshRate = settings.hudRefreshRate();
        if ((hasContent || nimbus$hudHadContent) && nimbus$hudUpdateDue(refreshRate, forceUpdate)) {
            if (settings.postProcessOptions().blurEnabled() && hasPostProcess) {
                skija.captureScene(Math.min(settings.postProcessOptions().blurRefreshRate(), refreshRate));
            }
            nimbus$hudLayer.render(frame -> skija.hud().render());
            nimbus$hudHadContent = hasContent;
        } else {
            skija.hud().discardFrame();
        }
        if (nimbus$hudHadContent) nimbus$hudLayer.extract(graphics, framebufferWidth, framebufferHeight);

        if (MusicToastState.active()) minecraft.getToastManager().render(graphics);
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void nimbus$close(CallbackInfo ci) {
        if (nimbus$hudLayer != null) nimbus$hudLayer.releaseTarget();
        nimbus$hudLayer = null;
        nimbus$hudWidth = nimbus$hudHeight = 0;
        nimbus$hudHadContent = false;
        nimbus$scene = null;
        nimbus$resetHudCadence();
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
        long elapsed = (now - nimbus$nextHudUpdateNanos) / interval + 1L;
        nimbus$nextHudUpdateNanos += elapsed * interval;
        return true;
    }

    @Unique
    private void nimbus$resetHudCadence() {
        nimbus$nextHudUpdateNanos = 0L;
        nimbus$hudRefreshRate = 0;
    }
}
