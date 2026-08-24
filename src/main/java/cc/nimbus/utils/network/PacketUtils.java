package cc.nimbus.utils.network;

import cc.nimbus.mixin.accessor.network.ServerboundMovePlayerPacketAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.protocol.login.ClientboundLoginCompressionPacket;
import net.minecraft.network.protocol.login.ClientboundHelloPacket;
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

    public static void setPlayerMovePacketYaw(ServerboundMovePlayerPacket packet, float yaw) {
        ((ServerboundMovePlayerPacketAccessor) packet).nimbus$setYaw(yaw);
    }

    public static void setPlayerMovePacketPitch(ServerboundMovePlayerPacket packet, float pitch) {
        ((ServerboundMovePlayerPacketAccessor) packet).nimbus$setPitch(pitch);
    }

    public static boolean isEssential(Packet<?> packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return true;
        if (minecraft.screen instanceof LevelLoadingScreen) return true;
        if (packet instanceof ClientboundServerDataPacket
                || packet instanceof ClientboundLoginCompressionPacket
                || packet instanceof ClientboundHelloPacket
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
