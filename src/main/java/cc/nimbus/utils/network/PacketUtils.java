package cc.nimbus.utils.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.configuration.ClientboundFinishConfigurationPacket;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundServerDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.login.ClientboundLoginCompressionPacket;
import net.minecraft.network.protocol.login.ClientboundLoginFinishedPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class PacketUtils {
    private PacketUtils() {
    }

    public static void sendPacketNoEvent(Packet<?> packet) {
        Connection connection = connection();
        if (connection != null && packet != null) {
            ConnectionUtils.sendPacketNoEvent(connection, packet);
        }
    }

    public static void receivePacketNoEvent(Packet<?> packet) {
        Connection connection = connection();
        if (connection != null && packet != null) {
            ConnectionUtils.receivePacketNoEvent(connection,packet);
        }
    }

    public static boolean isEssential(Packet<?> packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return true;
        if (packet instanceof ClientboundServerDataPacket
                || packet instanceof ClientboundLoginCompressionPacket
                || packet instanceof ClientboundLoginFinishedPacket
                || packet instanceof ClientboundFinishConfigurationPacket
                || packet instanceof ClientboundPlayerInfoUpdatePacket
                || packet instanceof ClientboundDisconnectPacket
                || packet instanceof ClientboundLevelChunkWithLightPacket
                || packet instanceof ClientboundLoginPacket
                || packet instanceof ClientboundMapItemDataPacket
                || packet instanceof ClientboundSetDefaultSpawnPositionPacket
                || packet instanceof ClientboundSystemChatPacket
                || packet instanceof ClientboundSetTitleTextPacket
                || packet instanceof ClientboundSetSubtitleTextPacket
                || packet instanceof ClientboundRemoveEntitiesPacket
                || packet instanceof ClientboundRespawnPacket
                || packet instanceof ClientboundSetHealthPacket
                || packet instanceof ClientboundSoundPacket
                || packet instanceof ClientboundSetPlayerTeamPacket
                || packet instanceof ClientboundAddEntityPacket
        ) {
            return true;
        }
        if (packet instanceof ClientboundEntityEventPacket status && minecraft.level != null) {
            Entity entity = status.getEntity(minecraft.level);
            return status.getEventId() == 2 && entity instanceof LivingEntity;
        }
        return minecraft.player.tickCount <= 60;
    }

    private static Connection connection() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.getConnection() == null ? null : minecraft.getConnection().getConnection();
    }
}
