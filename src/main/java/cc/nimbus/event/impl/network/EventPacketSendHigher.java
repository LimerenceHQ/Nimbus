package cc.nimbus.event.impl.network;
import net.minecraft.network.protocol.Packet;
import cc.nimbus.event.Cancelable;
public class EventPacketSendHigher extends Cancelable { private Packet<?> packet; public EventPacketSendHigher(){} public EventPacketSendHigher(Packet<?> packet){this.packet=packet;} public EventPacketSendHigher set(Packet<?> value){reset();packet=value;return this;} public Packet<?> getPacket(){return packet;} public void setPacket(Packet<?> value){packet=value;} }
