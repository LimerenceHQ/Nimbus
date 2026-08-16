package cc.nimbus.mixin.entity;

import cc.nimbus.event.EventBus;
import cc.nimbus.event.impl.player.EventJump;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Redirect(method = "jumpFromGround", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;getYRot()F"))
    private float nimbus$jumpYaw(LivingEntity entity) {
        if (entity != Minecraft.getInstance().player) return entity.getYRot();
        return EventBus.call(new EventJump(entity.getYRot())).getYaw();
    }
}
