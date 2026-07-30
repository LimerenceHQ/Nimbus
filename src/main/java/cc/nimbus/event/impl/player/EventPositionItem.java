package cc.nimbus.event.impl.player;
import net.minecraft.network.protocol.Packet;
import cc.nimbus.event.Cancelable;
public class EventPositionItem extends Cancelable { private Packet<?> packet; public EventPositionItem(){} public EventPositionItem(Packet<?> packet){this.packet=packet;} public EventPositionItem set(Packet<?> value){reset();packet=value;return this;} public Packet<?> getPacket(){return packet;} public void setPacket(Packet<?> value){packet=value;} }
