package cc.nimbus.mixin.accessor.player;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.prediction.PredictiveAction;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MultiPlayerGameMode.class)
public interface MultiPlayerGameModeAccessor {
    @Invoker("ensureHasSentCarriedItem")
    void nimbus$ensureHasSentCarriedItem();
    @Accessor("localPlayerMode")
    GameType nimbus$getLocalPlayerMode();

    @Invoker("startPrediction")
    void nimbus$startPrediction(ClientLevel level, PredictiveAction action);
}
