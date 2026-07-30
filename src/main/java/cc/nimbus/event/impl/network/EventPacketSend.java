package cc.nimbus.event.impl.network;
import net.minecraft.network.protocol.Packet;
import cc.nimbus.event.Cancelable;
public class EventPacketSend extends Cancelable { private Packet<?> packet; public EventPacketSend(){} public EventPacketSend(Packet<?> packet){this.packet=packet;} public EventPacketSend set(Packet<?> value){reset();packet=value;return this;} public Packet<?> getPacket(){return packet;} public void setPacket(Packet<?> value){packet=value;} }
