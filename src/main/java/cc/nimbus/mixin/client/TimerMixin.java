package cc.nimbus.mixin.client;

import cc.nimbus.managers.GameStateManager;
import net.minecraft.client.Timer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Timer.class)
public abstract class TimerMixin {
    @Shadow
    public float tickDelta;

    @Inject(method = "advanceTime", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Timer;tickDelta:F", shift = At.Shift.AFTER))
    private void hookTimer(CallbackInfoReturnable<Integer> cir) {
        if (GameStateManager.timerSpeed > 0.0F) {
            tickDelta *= GameStateManager.timerSpeed;
        }
    }
}
