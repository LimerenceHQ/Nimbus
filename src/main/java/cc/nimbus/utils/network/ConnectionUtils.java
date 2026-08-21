package cc.nimbus.utils.network;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Connection helpers used for queue replay without dispatching PacketEvent. */
public final class ConnectionUtils {
    private static final Field CHANNEL = findField(Connection.class, "channel", Channel.class);
    private static final Field SENT_PACKETS = findField(Connection.class, "sentPackets", int.class);
    private static final Method DO_SEND_PACKET = findMethod(Connection.class, "doSendPacket", "m_243087_");

    private ConnectionUtils() {
    }

    public static void sendPacketNoEvent(Connection connection, Packet<?> packet) {
        if (connection == null || packet == null) return;
        Channel channel = channel(connection);
        incrementSentPackets(connection);
        Runnable send = () -> {
            //PacketEventGuard.suppress();
            try {
                invokeDoSendPacket(connection, packet, null, true);
            } finally {
               // PacketEventGuard.restore();
            }
        };
        if (channel.eventLoop().inEventLoop()) send.run();
        else channel.eventLoop().execute(send);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void receivePacketNoEvent(Connection connection, Packet<?> packet) {
        if (connection == null || packet == null || !connection.isConnected()) return;
        PacketListener listener = connection.getPacketListener();
        if (listener == null) return;
    //    PacketEventGuard.suppress();
        try {
            ((Packet) packet).handle(listener);
        } finally {
      //      PacketEventGuard.restore();
        }
    }

    private static Channel channel(Connection connection) {
        try {
            return (Channel) CHANNEL.get(connection);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("Unable to access Connection.channel", error);
        }
    }

    private static void incrementSentPackets(Connection connection) {
        try {
            SENT_PACKETS.setInt(connection, SENT_PACKETS.getInt(connection) + 1);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("Unable to update Connection.sentPackets", error);
        }
    }

    private static void invokeDoSendPacket(Connection connection, Packet<?> packet,
                                           ChannelFutureListener listener, boolean flush) {
        try {
            DO_SEND_PACKET.invoke(connection, packet, listener, flush);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Unable to invoke Connection.doSendPacket", error);
        }
    }

    private static Field findField(Class<?> owner, String name, Class<?> type) {
        for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                if (!type.isAssignableFrom(field.getType()) && !(type == int.class && field.getType() == int.class)) continue;
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new IllegalStateException("Unable to resolve field " + owner.getName() + "." + name);
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

}
