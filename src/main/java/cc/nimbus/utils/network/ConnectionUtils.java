package cc.nimbus.utils.network;

import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class ConnectionUtils {
    private static final Field CHANNEL = findField(Connection.class,
            "channel", "field_11651", "f_129468_");
    private static final Field PACKET_LISTENER = findField(Connection.class,
            "packetListener", "field_11652", "f_129470_");
    private static final Field SENT_PACKETS = findField(Connection.class,
            "sentPackets", "field_11656", "f_129475_");
    private static final Method DO_SEND_PACKET = findMethod(Connection.class,
            "doSendPacket", "method_36942", "m_243087_");
    private static final Method GET_CURRENT_PROTOCOL = findMethod0(Connection.class,
            "getCurrentProtocol", "method_32306", "m_178315_");

    private ConnectionUtils() {
    }

    public static void sendPacketNoEvent(Connection connection, Packet<?> packet) {
        try {
            SENT_PACKETS.setInt(connection, SENT_PACKETS.getInt(connection) + 1);
        } catch (IllegalAccessException ignored) {
        }

        ConnectionProtocol protocol = getCurrentProtocol(connection);
        Channel channel = getChannel(connection);
        if (channel.eventLoop().inEventLoop()) {
            doSendPacket(connection, packet, null, protocol, ConnectionProtocol.getProtocolForPacket(packet));
        } else {
            channel.eventLoop().execute(() -> doSendPacket(connection, packet, null, protocol, ConnectionProtocol.getProtocolForPacket(packet)));
        }
    }

    @SuppressWarnings("unchecked")
    public static void receivePacketNoEvent(Connection connection, Packet<?> packet) {
        if (connection.isConnected()) {
            PacketListener listener = getPacketListener(connection);
            ((Packet<PacketListener>) packet).handle(listener);
        }
    }

    private static Channel getChannel(Connection connection) {
        try {
            return (Channel) CHANNEL.get(connection);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Failed to read channel field", e);
        }
    }

    private static PacketListener getPacketListener(Connection connection) {
        try {
            return (PacketListener) PACKET_LISTENER.get(connection);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Failed to read packetListener field", e);
        }
    }

    private static ConnectionProtocol getCurrentProtocol(Connection connection) {
        try {
            return (ConnectionProtocol) GET_CURRENT_PROTOCOL.invoke(connection);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Failed to invoke getCurrentProtocol", e);
        }
    }

    private static void doSendPacket(Connection connection, Packet<?> packet, PacketSendListener callbacks,
                                     ConnectionProtocol currentProtocol, ConnectionProtocol packetProtocol) {
        try {
            DO_SEND_PACKET.invoke(connection, packet, callbacks, currentProtocol, packetProtocol);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Failed to invoke doSendPacket", e);
        }
    }

    private static Field findField(Class<?> owner, String... names) {
        for (String name : names) {
            try {
                Field field = owner.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new IllegalStateException("Failed to resolve field on " + owner.getName());
    }

    private static Method findMethod(Class<?> owner, String name, String... altNames) {
        for (Method method : owner.getDeclaredMethods()) {
            if (method.getName().equals(name)) {
                method.setAccessible(true);
                return method;
            }
        }
        for (String alt : altNames) {
            for (Method method : owner.getDeclaredMethods()) {
                if (method.getName().equals(alt)) {
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        throw new IllegalStateException("Failed to resolve method on " + owner.getName());
    }

    private static Method findMethod0(Class<?> owner, String name, String... altNames) {
        for (Method method : owner.getDeclaredMethods()) {
            if (method.getName().equals(name)) {
                method.setAccessible(true);
                return method;
            }
        }
        for (String alt : altNames) {
            for (Method method : owner.getDeclaredMethods()) {
                if (method.getName().equals(alt)) {
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        throw new IllegalStateException("Failed to resolve method on " + owner.getName());
    }
}
