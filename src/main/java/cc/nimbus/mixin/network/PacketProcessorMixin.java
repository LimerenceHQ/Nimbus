package cc.nimbus.mixin.network;

import cc.nimbus.core.Core;
import cc.nimbus.managers.PacketManager;
import cc.nimbus.utils.network.PacketUtils;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.network.PacketProcessor$ListenerAndPacket")
public abstract class PacketProcessorMixin {
    @Redirect(
            method = "handle",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/Packet;handle(Lnet/minecraft/network/PacketListener;)V"
            )
    )
    private void nimbus$handleInbound(Packet<?> packet, PacketListener listener) {
        if (!Core.INSTANCE.isInitialized()
                || PacketUtils.isProtocolControl(packet)) {
            nimbus$handle(packet, listener);
            return;
        }

        PacketManager packetManager = Core.INSTANCE.getPacketManager();
        if (!packetManager.inboundsHook(packet)) return;
        nimbus$handle(packet, listener);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void nimbus$handle(Packet<?> packet, PacketListener listener) {
        ((Packet) packet).handle(listener);
    }
}
