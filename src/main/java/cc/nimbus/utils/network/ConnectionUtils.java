package cc.nimbus.utils.network;

import cc.nimbus.network.PacketEventGuard;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;

/** Connection helpers used for queue replay without dispatching PacketEvent. */
public final class ConnectionUtils {
    private ConnectionUtils() {
    }

    public static void sendPacketNoEvent(Connection connection, Packet<?> packet) {
        if (connection == null || packet == null) return;
        PacketEventGuard.suppress();
        try {
            connection.send(packet);
        } finally {
            PacketEventGuard.restore();
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void receivePacketNoEvent(Connection connection, Packet<?> packet) {
        if (connection == null || packet == null || !connection.isConnected()) return;
        PacketListener listener = connection.getPacketListener();
        if (listener == null) return;
        PacketEventGuard.suppress();
        try {
            ((Packet) packet).handle(listener);
        } finally {
            PacketEventGuard.restore();
        }
    }
}
