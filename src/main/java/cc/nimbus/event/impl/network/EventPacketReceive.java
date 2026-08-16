package cc.nimbus.event.impl.network;

import cc.nimbus.event.Cancelable;
import net.minecraft.network.protocol.Packet;

public class EventPacketReceive extends Cancelable {
    private Packet<?> packet;

    public EventPacketReceive() {
    }

    public EventPacketReceive(Packet<?> packet) {
        this.packet = packet;
    }

    public EventPacketReceive set(Packet<?> packet) {
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
