package cc.nimbus.mixin.player;

import cc.nimbus.features.module.modules.render.OldHitting;
import cc.nimbus.utils.mixin.MixinModuleLookup;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
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
}