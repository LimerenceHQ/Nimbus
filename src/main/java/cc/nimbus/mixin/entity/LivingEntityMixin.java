package cc.nimbus.mixin.entity;

import cc.nimbus.core.Core;
import cc.nimbus.event.EventBus;
import cc.nimbus.event.impl.player.EventJump;
import cc.nimbus.event.impl.player.EventTravel;
import cc.nimbus.features.rotation.Rotation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Shadow
    public float yHeadRot;

    @Shadow
    public float yBodyRot;

    @Redirect(method = "jumpFromGround", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;getYRot()F"))
    private float nimbus$jumpYaw(LivingEntity entity) {
        if (entity != Minecraft.getInstance().player) return entity.getYRot();
        return EventBus.call(new EventJump(entity.getYRot())).getYaw();
    }

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void nimbus$travel(Vec3 travelVector, CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (entity == Minecraft.getInstance().player && EventBus.call(new EventTravel()).isCancel()) {
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void postTickOverrideHeadYaw(CallbackInfo ci) {

        LivingEntity entity = (LivingEntity) (Object) this;
        if (!(entity instanceof LocalPlayer)) {
            return;
        }

        Rotation serverRotation = Core.INSTANCE.getRotationManager().getServerRotation();
        if (serverRotation == null) {
            return;
        }

        this.yHeadRot = serverRotation.yaw();
    }

    @Inject(method = "tickHeadTurn", at = @At("HEAD"), cancellable = true)
    private void overrideTurnHead(float bodyRotation, float headRotation, CallbackInfoReturnable<Float> cir) {

        Entity entity = (Entity) (Object) this;
        if (!(entity instanceof LocalPlayer)) {
            return;
        }

        Rotation serverRotation = Core.INSTANCE.getRotationManager().getServerRotation();
        if (serverRotation == null) {
            return;
        }

        float targetYaw = serverRotation.yaw();
        float wrappedBodyRotation = Mth.wrapDegrees(bodyRotation - this.yBodyRot);
        this.yBodyRot += wrappedBodyRotation * 0.3F;

        float delta = Mth.wrapDegrees(targetYaw - this.yBodyRot);
        delta = Mth.clamp(delta, -75.0F, 75.0F);
        this.yBodyRot = targetYaw - delta;

        if (Math.abs(delta) > 50.0F) {
            this.yBodyRot += delta * 0.2F;
        }

        boolean reverse = delta < -90.0F || delta >= 90.0F;
        cir.setReturnValue(reverse ? -headRotation : headRotation);
    }
}