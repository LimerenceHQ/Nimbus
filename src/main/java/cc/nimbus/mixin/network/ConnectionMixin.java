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
    @Shadow public abstract java.net.SocketAddress getRemoteAddress();
    @Unique private boolean nimbus$isLocalProxy(){
        var address=getRemoteAddress();
        return address instanceof java.net.InetSocketAddress inet && inet.getAddress()!=null && inet.getAddress().isLoopbackAddress();
    }
    @Inject(method="disconnect",at=@At("HEAD"))
    private void nimbus$logDisconnect(net.minecraft.network.chat.Component reason,CallbackInfo ci){
        if(nimbus$isLocalProxy())org.slf4j.LoggerFactory.getLogger("NimbusProxy").warn("B disconnect from {}: {}",getRemoteAddress(),reason.getString());
    }
    @Inject(method="exceptionCaught",at=@At("HEAD"))
    private void nimbus$logConnectionException(ChannelHandlerContext ctx,Throwable error,CallbackInfo ci){
        if(nimbus$isLocalProxy())org.slf4j.LoggerFactory.getLogger("NimbusProxy").error("B connection exception: "+getRemoteAddress(),error);
    }

    @Inject(method = "sendPacket", at = @At("HEAD"), cancellable = true)
    private void nimbus$packetSend(Packet<?> packet, PacketSendListener packetSendListener, CallbackInfo ci) {
        if (!Core.INSTANCE.isInitialized()) return;
        if (!Core.INSTANCE.getPacketManager().outboundsHook(packet)) {
            ci.cancel();
        }
    }

}
