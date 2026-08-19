package cc.nimbus.mixin.network;

import cc.nimbus.core.Core;
import cc.nimbus.event.EventBus;
import cc.nimbus.event.impl.network.EventPacketReceive;
import cc.nimbus.event.impl.network.EventPacketSend;
import cc.nimbus.utils.network.access.ConnectionPacketAccess;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class ConnectionMixin implements ConnectionPacketAccess {
    @Unique private final ThreadLocal<Boolean> nimbus$sendingReplacement = ThreadLocal.withInitial(() -> false);
    @Unique private final ThreadLocal<Boolean> nimbus$receivingReplacement = ThreadLocal.withInitial(() -> false);

    @Shadow private void sendPacket(Packet<?> packet, ChannelFutureListener listener, boolean flush) {
    }

    @Shadow protected abstract void channelRead0(ChannelHandlerContext context, Packet<?> packet) throws Exception;

    @Inject(method = "sendPacket", at = @At("HEAD"), cancellable = true)
    private void nimbus$packetSend(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
        if (nimbus$sendingReplacement.get()) return;
        EventPacketSend event = EventBus.call(new EventPacketSend(packet));
        if (event.isCancel() || event.getPacket() == null) {
            ci.cancel();
            return;
        }
        if (event.getPacket() != packet) {
            nimbus$sendingReplacement.set(true);
            try {
                sendPacket(event.getPacket(), listener, flush);
            } finally {
                nimbus$sendingReplacement.set(false);
            }
            ci.cancel();
        }
    }

    @Inject(method = "doSendPacket", at = @At("RETURN"))
    private void nimbus$packetSent(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
        if (Core.INSTANCE.isInitialized()) {
            Core.INSTANCE.getRotationManager().recordSentPacket(packet);
        }
    }

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V",
            at = @At("HEAD"), cancellable = true)
    private void nimbus$packetReceive(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) throws Exception {
        if (nimbus$receivingReplacement.get()) return;
        EventPacketReceive event = EventBus.call(new EventPacketReceive(packet));
        if (event.isCancel() || event.getPacket() == null) {
            ci.cancel();
            return;
        }
        if (event.getPacket() != packet) {
            nimbus$receivingReplacement.set(true);
            try {
                channelRead0(context, event.getPacket());
            } finally {
                nimbus$receivingReplacement.set(false);
            }
            ci.cancel();
        }
    }

    @Override
    public void nimbus$sendPacketNoEvent(Packet<?> packet) {
        nimbus$sendingReplacement.set(true);
        try {
            sendPacket(packet, null, true);
        } finally {
            nimbus$sendingReplacement.set(false);
        }
    }

    @Override
    public void nimbus$receivePacketNoEvent(Packet<?> packet) {
        nimbus$receivingReplacement.set(true);
        try {
            channelRead0(null, packet);
        } catch (Exception error) {
            throw new IllegalStateException("Failed to process packet without events", error);
        } finally {
            nimbus$receivingReplacement.set(false);
        }
    }
}