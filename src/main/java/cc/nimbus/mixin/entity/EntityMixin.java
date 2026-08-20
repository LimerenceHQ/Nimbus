package cc.nimbus.mixin.entity;

import cc.nimbus.event.EventBus;
import cc.nimbus.event.impl.player.EventRotationApplied;
import cc.nimbus.event.impl.player.EventSlowMovement;
import cc.nimbus.event.impl.player.EventStrafe;
import cc.nimbus.managers.rotation.Rotation;
import cc.nimbus.managers.rotation.RotationMath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
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

    @Redirect(method = "moveRelative", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;getYRot()F"))
    private float nimbus$strafeYaw(Entity entity) {
        if (entity != Minecraft.getInstance().player) return entity.getYRot();
        return EventBus.call(new EventStrafe(entity.getYRot())).getYaw();
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
