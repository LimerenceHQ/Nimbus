package cc.nimbus.event.impl.network;

import cc.nimbus.event.Cancelable;
import net.minecraft.network.protocol.Packet;

import java.util.Objects;

/** Unified, mutable packet event for both network directions. */
public final class PacketEvent extends Cancelable {
    public enum PacketState {
        RECEIVE,
        SEND
    }

    private Packet<?> packet;
    private final PacketState packetState;
    private boolean captured;

    public PacketEvent(Packet<?> packet, PacketState packetState) {
        this.packet = packet;
        this.packetState = Objects.requireNonNull(packetState, "packetState");
    }

    public Packet<?> getPacket() {
        return packet;
    }

    public void setPacket(Packet<?> packet) {
        this.packet = packet;
    }

    public PacketState getPacketState() {
        return packetState;
    }

    public boolean isReceive() {
        return packetState == PacketState.RECEIVE;
    }

    public boolean isSend() {
        return packetState == PacketState.SEND;
    }

    public boolean isCaptured() {
        return captured;
    }

    public void markCaptured() {
        captured = true;
        cancelEvent();
    }

}
