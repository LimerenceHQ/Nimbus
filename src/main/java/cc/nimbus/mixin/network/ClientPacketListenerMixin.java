package cc.nimbus.mixin.network;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
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
