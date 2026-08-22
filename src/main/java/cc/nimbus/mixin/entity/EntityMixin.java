package cc.nimbus.mixin.entity;

import cc.nimbus.event.EventBus;
import cc.nimbus.event.impl.player.EventRayTrace;
import cc.nimbus.event.impl.player.EventRotationApplied;
import cc.nimbus.event.impl.player.EventSlowMovement;
import cc.nimbus.event.impl.player.EventStrafe;
import cc.nimbus.features.rotation.Rotation;
import cc.nimbus.features.rotation.RotationMath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {

    @Shadow
    protected Vec3 stuckSpeedMultiplier;

    @Inject(method = "getViewVector", at = @At("HEAD"), cancellable = true)
    private void onGetViewVector(float partialTicks, CallbackInfoReturnable<Vec3> cir) {
        Entity entity = (Entity) (Object) this;
        if (!(entity instanceof LocalPlayer)) {
            return;
        }

        EventRayTrace event = new EventRayTrace(entity, entity.getViewYRot(partialTicks), entity.getViewXRot(partialTicks));
        EventBus.call(event);

        float pitchRadians = event.getPitch() * ((float) Math.PI / 180.0F);
        float yawRadians = -event.getYaw() * ((float) Math.PI / 180.0F);
        float cosYaw = Mth.cos(yawRadians);
        float sinYaw = Mth.sin(yawRadians);
        float cosPitch = Mth.cos(pitchRadians);
        float sinPitch = Mth.sin(pitchRadians);
        cir.setReturnValue(new Vec3(sinYaw * cosPitch, -sinPitch, cosYaw * cosPitch));
    }

    @Inject(method = "moveRelative", at = @At("HEAD"), cancellable = true)
    private void onMoveRelative(float friction, Vec3 movementInput, CallbackInfo ci) {

        Entity entity = (Entity) (Object) this;
        if (!(entity instanceof LocalPlayer)) {
            return;
        }

        EventStrafe event = new EventStrafe(entity.getYRot());
        EventBus.call(event);

        if (event.isCancel()) {
            entity.setDeltaMovement(entity.getDeltaMovement().add(movementInput));
            ci.cancel();
            return;
        }

        Vec3 adjustedInput = new Vec3(movementInput.x, movementInput.y, movementInput.z);
        double length = adjustedInput.lengthSqr();
        Vec3 velocity;

        if (length < 1.0E-7D) {
            velocity = Vec3.ZERO;
        } else {
            Vec3 scaled = (length > 1.0D ? adjustedInput.normalize() : adjustedInput).scale(friction);
            float yawRadians = event.getYaw() * ((float) Math.PI / 180.0F);
            float sin = Mth.sin(yawRadians);
            float cos = Mth.cos(yawRadians);
            velocity = new Vec3(scaled.x * cos - scaled.z * sin, scaled.y, scaled.z * cos + scaled.x * sin);
        }

        entity.setDeltaMovement(entity.getDeltaMovement().add(velocity));
        ci.cancel();
    }

    @Inject(method = "makeStuckInBlock", at = @At("RETURN"))
    private void onMakeStuckInBlock(BlockState state, Vec3 multiplier, CallbackInfo ci) {
        Entity thisEntity = (Entity) (Object) this;
        if (!(thisEntity instanceof LocalPlayer)) {
            return;
        }

        EventSlowMovement event = new EventSlowMovement(state, multiplier);
        EventBus.call(event);

        if (event.isCancel()) {
            this.stuckSpeedMultiplier = Vec3.ZERO;
            return;
        }
        this.stuckSpeedMultiplier = event.getStuckSpeedMultiplier();
    }

    @Inject(method = "getLookAngle", at = @At("HEAD"), cancellable = true)
    private void nimbus$lookRotation(CallbackInfoReturnable<Vec3> cir) {
        Entity entity = (Entity) (Object) this;
        if (entity != Minecraft.getInstance().player) return;
        EventRotationApplied event = EventBus.call(new EventRotationApplied(entity.getYRot(), entity.getXRot()));
        if (event.getYaw() != entity.getYRot() || event.getPitch() != entity.getXRot()) {
            cir.setReturnValue(RotationMath.direction(new Rotation(event.getYaw(), event.getPitch())));
        }
    }
}