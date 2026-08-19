package cc.nimbus.event.impl.network;

import cc.nimbus.event.Cancelable;
import net.minecraft.network.protocol.Packet;

public class EventPacketReceive extends Cancelable {
    private Packet<?> packet;
    /**
     * PacketManager uses this to keep latency-sensitive visual packets out of
     * a module's inbound delay queue. It does not skip event delivery or the
     * vanilla packet handler.
     */
    private boolean bypassInboundQueue;

    public EventPacketReceive() {
    }

    public EventPacketReceive(Packet<?> packet) {
        this.packet = packet;
    }

    public EventPacketReceive set(Packet<?> packet) {
        reset();
        this.packet = packet;
        this.bypassInboundQueue = false;
        return this;
    }

    public Packet<?> getPacket() {
        return packet;
    }

    public void setPacket(Packet<?> packet) {
        this.packet = packet;
    }

    public boolean isBypassInboundQueue() {
        return bypassInboundQueue;
    }

    public void setBypassInboundQueue(boolean bypassInboundQueue) {
        this.bypassInboundQueue = bypassInboundQueue;
    }
}
