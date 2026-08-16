package cc.nimbus.event.impl.network;

import cc.nimbus.event.Cancelable;
import net.minecraft.network.protocol.Packet;

public class EventPacketSend extends Cancelable {
    private Packet<?> packet;

    public EventPacketSend() {
    }

    public EventPacketSend(Packet<?> packet) {
        this.packet = packet;
    }

    public EventPacketSend set(Packet<?> packet) {
        reset();
        this.packet = packet;
        return this;
    }

    public Packet<?> getPacket() {
        return packet;
    }

    public void setPacket(Packet<?> packet) {
        this.packet = packet;
    }
}
