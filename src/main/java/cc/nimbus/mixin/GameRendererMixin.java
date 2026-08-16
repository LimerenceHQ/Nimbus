package cc.nimbus.mixin;

import cc.nimbus.event.EventBus;
import cc.nimbus.event.impl.player.EventRayTrace;
import cc.nimbus.utils.render.Render3DUtils;
import cc.nimbus.features.module.modules.render.FullBright;
import cc.nimbus.utils.mixin.MixinModuleLookup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Unique private Entity nimbus$pickEntity;
    @Unique private float nimbus$pickYaw;
    @Unique private float nimbus$pickPitch;
    @Unique private float nimbus$pickOldYaw;
    @Unique private float nimbus$pickOldPitch;

    @Inject(method = "pick", at = @At("HEAD"), cancellable = true)
    private void nimbus$rotationPick(float partialTick, CallbackInfo ci) {
        Entity entity = Minecraft.getInstance().getCameraEntity();
        if (entity == null) return;
        EventRayTrace event = EventBus.call(new EventRayTrace(entity, entity.getYRot(), entity.getXRot()));
        if (event.isCancel()) {
            ci.cancel();
            return;
        }
        nimbus$pickEntity = entity;
        nimbus$pickYaw = entity.getYRot();
        nimbus$pickPitch = entity.getXRot();
        nimbus$pickOldYaw = entity.yRotO;
        nimbus$pickOldPitch = entity.xRotO;
        entity.setYRot(event.getYaw());
        entity.setXRot(event.getPitch());
        entity.yRotO = event.getYaw();
        entity.xRotO = event.getPitch();
    }

    @Inject(method = "pick", at = @At("RETURN"))
    private void nimbus$restorePick(float partialTick, CallbackInfo ci) {
        if (nimbus$pickEntity == null) return;
        nimbus$pickEntity.setYRot(nimbus$pickYaw);
        nimbus$pickEntity.setXRot(nimbus$pickPitch);
        nimbus$pickEntity.yRotO = nimbus$pickOldYaw;
        nimbus$pickEntity.xRotO = nimbus$pickOldPitch;
        nimbus$pickEntity = null;
    }

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
