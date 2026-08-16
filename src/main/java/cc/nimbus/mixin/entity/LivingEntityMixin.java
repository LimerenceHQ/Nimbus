package cc.nimbus.mixin.entity;

import cc.nimbus.event.EventBus;
import cc.nimbus.event.impl.player.EventJump;
import cc.nimbus.event.impl.player.EventTravel;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
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
}
