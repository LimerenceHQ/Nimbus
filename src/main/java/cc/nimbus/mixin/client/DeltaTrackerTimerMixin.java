package cc.nimbus.mixin.client;

import cc.nimbus.managers.ClientManger;
import cc.nimbus.mixin.accessor.client.DeltaTrackerTimerAccessor;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DeltaTracker.Timer.class)
public abstract class DeltaTrackerTimerMixin {
    @Inject(method = "advanceGameTime", at = @At(value = "FIELD", target = "Lnet/minecraft/client/DeltaTracker$Timer;deltaTicks:F", shift = At.Shift.AFTER))
    private void nimbus$applyTimerSpeed(long gameTime, CallbackInfoReturnable<Integer> cir) {
        float speed = ClientManger.getTimerSpeed();
        if (speed > 0.0F && speed != 1.0F) {
            DeltaTrackerTimerAccessor timer = (DeltaTrackerTimerAccessor) (Object) this;
            timer.nimbus$setDeltaTicks(timer.nimbus$getDeltaTicks() * speed);
        }
    }
}
