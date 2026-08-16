package cc.nimbus.utils.network.access;

import net.minecraft.network.protocol.Packet;

public interface ConnectionPacketAccess {
    void nimbus$sendPacketNoEvent(Packet<?> packet);

    void nimbus$receivePacketNoEvent(Packet<?> packet);
}
