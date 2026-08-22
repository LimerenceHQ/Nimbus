package cc.nimbus.mixin.player;

import cc.nimbus.core.Core;
import cc.nimbus.features.module.modules.render.OldHitting;
import cc.nimbus.features.rotation.Rotation;
import cc.nimbus.utils.mixin.MixinModuleLookup;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerMixin {
    @Inject(method = "getAttackStrengthScale", at = @At("HEAD"), cancellable = true)
    private void nimbus$oldHittingAttackStrength(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (MixinModuleLookup.enabled(OldHitting.class) != null) {
            cir.setReturnValue(1.0f);
        }
    }

    @Inject(method = "getCurrentItemAttackStrengthDelay", at = @At("HEAD"), cancellable = true)
    private void nimbus$oldHittingAttackDelay(CallbackInfoReturnable<Float> cir) {
        if (MixinModuleLookup.enabled(OldHitting.class) != null) {
            cir.setReturnValue(1.0f);
        }
    }

    @Redirect(method = "attack", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getYRot()F"))
    private float hookFixRotation(Player instance) {

        Player player = (Player) (Object) this;
        if (!(player instanceof LocalPlayer)) {
            instance.getYRot();
        }

        Rotation targetRotation = Core.INSTANCE.getRotationManager().getServerRotation();
        if (targetRotation != null) {
            return targetRotation.yaw();
        }

        return instance.getYRot();
    }
}