package cc.nimbus.mixin.player;

import cc.nimbus.features.module.modules.render.NoFov;
import cc.nimbus.utils.mixin.MixinModuleLookup;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
    @Inject(method = "getFieldOfViewModifier", at = @At("HEAD"), cancellable = true)
    private void nimbus$noFov(CallbackInfoReturnable<Float> cir) {
        NoFov module = MixinModuleLookup.enabled(NoFov.class);
        if (module != null) cir.setReturnValue(module.fov.getValue().floatValue());
    }
}
