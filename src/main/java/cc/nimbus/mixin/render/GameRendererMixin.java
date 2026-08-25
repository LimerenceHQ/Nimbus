package cc.nimbus.mixin.render;

import cc.nimbus.features.module.modules.render.FullBright;
import cc.nimbus.features.module.modules.render.NoHurtCamera;
import cc.nimbus.accessor.GuiHudRenderAccess;
import cc.nimbus.utils.mixin.MixinModuleLookup;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void nimbus$beginHudFrame(float partialTick, long nanoTime, boolean renderLevel, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui instanceof GuiHudRenderAccess gui) gui.nimbus$resetHudExtraction();
    }

    @Inject(method = "getNightVisionScale", at = @At("HEAD"), cancellable = true)
    private static void nimbus$fullBrightNightVision(LivingEntity entity, float partialTick,
                                                     CallbackInfoReturnable<Float> cir) {
        FullBright module = MixinModuleLookup.enabled(FullBright.class);
        if (module != null && module.mode.is("Night Vision")) cir.setReturnValue(1.0f);
    }

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void nimbus$noHurtCamera(PoseStack poseStack, float partialTick, CallbackInfo ci) {
        if (MixinModuleLookup.enabled(NoHurtCamera.class) != null) ci.cancel();
    }
}
