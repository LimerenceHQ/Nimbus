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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
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

    @Unique
    private EventJump jumpEvent;

    @Unique private float preYaw = 0f;

    @Inject(method = "jumpFromGround",at = @At("HEAD"),cancellable = true)
    public void jumpPre(CallbackInfo callbackInfo) {
        if((LivingEntity)(Object)this instanceof Player playerEntity) {
            jumpEvent = new EventJump(playerEntity.getYRot());
            EventBus.call(jumpEvent);
            if (jumpEvent.isCancel()) {
                callbackInfo.cancel();
                return;
            }
            preYaw = playerEntity.getYRot();
            playerEntity.setYRot(jumpEvent.getYaw());
        }
    }

    @Inject(method = "jumpFromGround",at = @At("TAIL"))
    public void jumpPost(CallbackInfo ci){
        if((LivingEntity)(Object)this instanceof Player playerEntity && jumpEvent != null) {
            jumpEvent = null;
            playerEntity.setYRot(preYaw);
        }
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