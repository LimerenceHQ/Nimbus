package cc.nimbus.mixin.client;

import cc.nimbus.managers.GameStateManager;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DeltaTracker.Timer.class)
public abstract class TimerMixin {
    @Shadow
    private float deltaTicks;

    @Inject(method = "advanceTime(JZ)I", at = @At("TAIL"))
    private void hookTimer(long timeMillis, boolean paused, CallbackInfoReturnable<Integer> cir) {
        if (GameStateManager.timerSpeed > 0.0F) {
            deltaTicks *= GameStateManager.timerSpeed;
        }
    }
}
