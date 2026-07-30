package cc.nimbus.event.impl.network;
import net.minecraft.network.protocol.Packet;
import cc.nimbus.event.Cancelable;
public class EventPacketReceive extends Cancelable { private Packet<?> packet; public EventPacketReceive(){} public EventPacketReceive(Packet<?> packet){this.packet=packet;} public EventPacketReceive set(Packet<?> value){reset();packet=value;return this;} public Packet<?> getPacket(){return packet;} public void setPacket(Packet<?> value){packet=value;} }
