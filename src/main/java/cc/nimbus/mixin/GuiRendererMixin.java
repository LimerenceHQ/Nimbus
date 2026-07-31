package cc.nimbus.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import cc.nimbus.event.EventBus;
import cc.nimbus.event.impl.render.EventRender2D;
import cc.nimbus.core.Core;
import cc.nimbus.features.module.modules.hud.PostProcess;
import cc.nimbus.utils.render.NativeSupport;
import cc.nimbus.utils.render.skija.SkijaRenderTarget;
import cc.nimbus.ui.clickgui.neutron.NeutronScreen;
import cc.nimbus.ui.menu.TitleScreen;
import cc.nimbus.utils.render.refresh.impl.Refresh_VSync;
import cc.nimbus.utils.render.skija.create.Skia;
import cc.nimbus.utils.render.skija.image.minecraft.MinecraftSnapshot;
import cc.nimbus.utils.render.Render2DUtils;

import java.util.*;

@Mixin(GuiRenderer.class)
public class GuiRendererMixin {
    @Shadow @Final private FeatureRenderDispatcher featureRenderDispatcher;
    @Shadow @Final private Map<Class<? extends PictureInPictureRenderState>, PictureInPictureRenderer<?>> pictureInPictureRenderers;
    @Unique private GuiRenderState nimbus$state;
    @Unique private GuiRenderer nimbus$renderer;
    @Unique private SkijaRenderTarget nimbus$hudTarget;
    @Unique private static final ThreadLocal<Boolean> NIMBUS_RENDERING_AUXILIARY = ThreadLocal.withInitial(() -> false);

    @Inject(method = "draw", at = @At("HEAD"))
    private void nimbus$draw(CallbackInfo ci) {
        if (NIMBUS_RENDERING_AUXILIARY.get()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() instanceof NeutronScreen neutronScreen) {
            PostProcess postProcess = Core.INSTANCE.getModuleManager().getModule(PostProcess.class);
            if (postProcess != null && postProcess.isEnabled() && !postProcess.blur_mode.is("Off")) {
                MinecraftSnapshot.capture();
            }
            neutronScreen.renderExtractedFrame();
        }

        if (minecraft.gui.screen() instanceof TitleScreen mainMenu) {
            MinecraftSnapshot.capture();
            mainMenu.renderExtractedFrame();
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
        NativeSupport.validateBackend();
        if (renderHud && NativeSupport.isSupported()) {
            if (nimbus$hudTarget == null) {
                nimbus$hudTarget = new SkijaRenderTarget("hud");
            }
            int framebufferWidth = minecraft.getWindow().getWidth();
            int framebufferHeight = minecraft.getWindow().getHeight();
            nimbus$hudTarget.ensure(framebufferWidth, framebufferHeight);
            PostProcess postProcess = Core.INSTANCE.getModuleManager().getModule(PostProcess.class);
            if (postProcess != null && postProcess.isEnabled() && !postProcess.blur_mode.is("Off")) {
                MinecraftSnapshot.capture();
            }
            nimbus$hudTarget.clear();
            Render2DUtils.beginFrame();
        EventBus.call(new EventRender2D(graphics));
            nimbus$hudTarget.render(canvas -> Skia.INSTANCE.callSurface(new Refresh_VSync()));
            Render2DUtils.drainPendingCloses();
            graphics.blit(RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
                    nimbus$hudTarget.identifier(), 0, 0, 0F, framebufferHeight,
                    graphics.guiWidth(), graphics.guiHeight(),
                    framebufferWidth, -framebufferHeight, framebufferWidth, framebufferHeight);
        }
        NIMBUS_RENDERING_AUXILIARY.set(true);
        try {
            nimbus$renderer.render();
            nimbus$renderer.endFrame();
        } finally {
            NIMBUS_RENDERING_AUXILIARY.set(false);
        }
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void nimbus$close(CallbackInfo ci) {
        if (nimbus$hudTarget != null) nimbus$hudTarget.close();
        nimbus$hudTarget = null;
        if (nimbus$renderer != null) nimbus$renderer.close();
        nimbus$renderer = null; nimbus$state = null;
    }
}
