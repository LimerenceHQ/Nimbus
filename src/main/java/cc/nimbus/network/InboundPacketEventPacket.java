package cc.nimbus.network;

import cc.nimbus.core.Core;
import cc.nimbus.managers.PacketManager;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public final class InboundPacketEventPacket<T extends PacketListener> implements Packet<T> {
    private final Packet<T> packet;

    public InboundPacketEventPacket(Packet<T> packet) {
        this.packet = packet;
    }

    @Override
    public PacketType<? extends Packet<T>> type() {
        return packet.type();
    }

    @Override
    public void handle(T listener) {
        PacketManager packetManager = Core.INSTANCE.getPacketManager();
        if (!packetManager.inboundsHook(packet)) return;
        Packet<?> selected = packetManager.consumeInboundHookPacket();
        if (selected != null) handlePacket(selected, listener);
    }

    @Override
    public boolean isSkippable() {
        return packet.isSkippable();
    }

    @Override
    public boolean isTerminal() {
        return packet.isTerminal();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void handlePacket(Packet<?> packet, PacketListener listener) {
        ((Packet) packet).handle(listener);
    }
}
