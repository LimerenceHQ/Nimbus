package cc.nimbus.mixin;

import cc.nimbus.utils.render.Render3DUtils;
import cc.nimbus.features.module.modules.render.FullBright;
import cc.nimbus.utils.mixin.MixinModuleLookup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @ModifyArg(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/PerspectiveProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"
            )
    )
    private Matrix4f nimbus$captureWorldProjection(Matrix4f projection) {
        Render3DUtils.captureWorldProjectionMatrix(projection);
        return projection;
    }

    @Inject(method = "getNightVisionScale", at = @At("HEAD"), cancellable = true)
    private static void nimbus$fullBrightNightVision(LivingEntity entity, float partialTick,
                                                     CallbackInfoReturnable<Float> cir) {
        FullBright module = MixinModuleLookup.enabled(FullBright.class);
        if (module != null && module.mode.is("Night Vision")) cir.setReturnValue(1.0f);
    }
}
