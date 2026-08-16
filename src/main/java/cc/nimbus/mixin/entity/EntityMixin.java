package cc.nimbus.mixin.entity;

import cc.nimbus.event.EventBus;
import cc.nimbus.event.impl.player.EventRotationApplied;
import cc.nimbus.event.impl.player.EventStrafe;
import cc.nimbus.features.rotation.Rotation;
import cc.nimbus.features.rotation.RotationMath;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Redirect(method = "moveRelative", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;getYRot()F"))
    private float nimbus$strafeYaw(Entity entity) {
        if (entity != Minecraft.getInstance().player) return entity.getYRot();
        return EventBus.call(new EventStrafe(entity.getYRot())).getYaw();
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
