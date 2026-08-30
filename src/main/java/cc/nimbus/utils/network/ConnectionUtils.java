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
    private static final Field CHANNEL = findField(Connection.class, "channel", "n", "f_129468_");
    private static final Field PACKET_LISTENER = findField(Connection.class, "packetListener", "q", "f_129470_");
    private static final Field SENT_PACKETS = findField(Connection.class, "sentPackets", "v", "f_129475_");
    private static final Method DO_SEND_PACKET = findMethod(Connection.class, "doSendPacket", "c", "m_243087_");

    private ConnectionUtils() {
    }

    public static void sendPacketNoEvent(Connection connection, Packet<?> packet) {
        try {
            SENT_PACKETS.setInt(connection, SENT_PACKETS.getInt(connection) + 1);
        } catch (IllegalAccessException ignored) {
        }

        Channel channel = getChannel(connection);
        if (channel.eventLoop().inEventLoop()) {
            doSendPacket(connection, packet);
        } else {
            channel.eventLoop().execute(() -> doSendPacket(connection, packet));
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

    private static void doSendPacket(Connection connection, Packet<?> packet) {
        try {
            if (DO_SEND_PACKET.getParameterCount() == 3) {
                DO_SEND_PACKET.invoke(connection, packet, null, false);
            } else {
                // Legacy 1.20.x signature retained for compatibility with older mappings.
                ConnectionProtocol protocol = ConnectionProtocol.PLAY;
                DO_SEND_PACKET.invoke(connection, packet, null, protocol, protocol);
            }
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
                if (method.getName().equals(alt) && isSendPacketMethod(method)) {
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        throw new IllegalStateException("Failed to resolve method on " + owner.getName());
    }

    private static boolean isSendPacketMethod(Method method) {
        Class<?>[] parameters = method.getParameterTypes();
        return (parameters.length == 3 || parameters.length == 4)
                && Packet.class.isAssignableFrom(parameters[0])
                && PacketSendListener.class.isAssignableFrom(parameters[1])
                && (parameters[2] == boolean.class || parameters[2] == Boolean.class
                || parameters[2] == ConnectionProtocol.class);
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
