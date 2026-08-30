package cc.nimbus.mixin.network;

import cc.nimbus.core.Core;
import cc.nimbus.managers.PacketManager;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class ConnectionMixin {
    @Inject(method = "sendPacket", at = @At("HEAD"), cancellable = true)
    private void nimbus$packetSend(Packet<?> packet, PacketSendListener packetSendListener, boolean flush, CallbackInfo ci) {
        if (!Core.INSTANCE.isInitialized()) return;
        if (!Core.INSTANCE.getPacketManager().outboundsHook(packet)) {
            ci.cancel();
        }
    }

}
