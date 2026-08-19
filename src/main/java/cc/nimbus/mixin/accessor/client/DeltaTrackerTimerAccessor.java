package cc.nimbus.mixin.accessor.client;

import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DeltaTracker.Timer.class)
public interface DeltaTrackerTimerAccessor {
    @Accessor("deltaTicks")
    float nimbus$getDeltaTicks();

    @Accessor("deltaTickResidual")
    float nimbus$getDeltaTickResidual();

    @Accessor("deltaTicks")
    void nimbus$setDeltaTicks(float value);
}
