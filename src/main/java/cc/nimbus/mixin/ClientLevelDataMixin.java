package cc.nimbus.mixin;

import cc.nimbus.features.module.modules.render.WorldTweaks;
import cc.nimbus.utils.mixin.MixinModuleLookup;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.ClientLevelData.class)
public abstract class ClientLevelDataMixin {
    @Inject(method = "getDayTime", at = @At("HEAD"), cancellable = true)
    private void nimbus$displayedTime(CallbackInfoReturnable<Long> cir) {
        WorldTweaks module = MixinModuleLookup.enabled(WorldTweaks.class);
        if (module != null && module.changeTime.getValue()) cir.setReturnValue(module.visualTimeTicks());
    }
}
