package cc.nimbus.mixin.network;

import cc.nimbus.features.module.modules.player.NameProtect;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "sendChat", at = @At("HEAD"))
    private void nimbus$rememberOutgoingChat(String message, CallbackInfo ci) {
        NameProtect.observeOutgoingChat(message);
    }

    @Redirect(
            method = "handleSetPlayerTeamPacket",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/scores/Scoreboard;removePlayerFromTeam(Ljava/lang/String;Lnet/minecraft/world/scores/PlayerTeam;)V"
            )
    )
    private void nimbus$removePlayerFromTeamSafely(
            Scoreboard scoreboard, String playerName, PlayerTeam team) {
        if (scoreboard.getPlayersTeam(playerName) == team) {
            scoreboard.removePlayerFromTeam(playerName, team);
        }
    }
}
