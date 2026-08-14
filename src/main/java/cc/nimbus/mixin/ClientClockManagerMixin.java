package cc.nimbus.mixin;

import cc.nimbus.features.module.modules.render.WorldTweaks;
import cc.nimbus.utils.mixin.MixinModuleLookup;
import net.minecraft.client.ClientClockManager;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientClockManager.class)
public abstract class ClientClockManagerMixin {
    @Inject(method = "getTotalTicks", at = @At("HEAD"), cancellable = true)
    private void nimbus$displayedTime(Holder<WorldClock> clock, CallbackInfoReturnable<Long> cir) {
        if (!clock.is(WorldClocks.OVERWORLD)) return;
        WorldTweaks module = MixinModuleLookup.enabled(WorldTweaks.class);
        if (module != null && module.changeTime.getValue()) {
            cir.setReturnValue(module.visualTimeTicks());
        }
    }
}
