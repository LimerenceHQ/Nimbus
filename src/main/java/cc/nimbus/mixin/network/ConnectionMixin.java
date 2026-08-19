package cc.nimbus.mixin.network;

import cc.nimbus.core.Core;
import cc.nimbus.event.EventBus;
import cc.nimbus.event.impl.network.EventPacketSend;
import cc.nimbus.network.InboundPacketEventPacket;
import cc.nimbus.network.PacketEventGuard;
import cc.nimbus.utils.network.access.ConnectionPacketAccess;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketProcessor;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.configuration.ClientboundFinishConfigurationPacket;
import net.minecraft.network.protocol.login.ClientboundLoginCompressionPacket;
import net.minecraft.network.protocol.login.ClientboundLoginFinishedPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
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

    @Redirect(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/Connection;genericsFtw(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;)V"))
    private static void nimbus$dispatchInbound(Packet<?> packet, PacketListener listener) {
        if (nimbus$isProtocolControlPacket(packet) || PacketEventGuard.isSuppressed()
                || !Core.INSTANCE.isInitialized()) {
            nimbus$handlePacket(packet, listener);
            return;
        }

        PacketProcessor processor = Minecraft.getInstance().packetProcessor();
        Packet<?> eventPacket = new InboundPacketEventPacket<>(packet);
        nimbus$ensurePacketThread(eventPacket, listener, processor);
    }

    @Unique
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void nimbus$handlePacket(Packet<?> packet, PacketListener listener) {
        ((Packet) packet).handle(listener);
    }

    @Unique
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void nimbus$ensurePacketThread(Packet<?> packet, PacketListener listener,
                                                    PacketProcessor processor) {
        PacketUtils.ensureRunningOnSameThread((Packet) packet, listener, processor);
        if (processor.isSameThread()) ((Packet) packet).handle(listener);
    }

    @Unique
    private static boolean nimbus$isProtocolControlPacket(Packet<?> packet) {
        return packet instanceof ClientboundLoginCompressionPacket
                || packet instanceof ClientboundLoginFinishedPacket
                || packet instanceof ClientboundFinishConfigurationPacket;
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
        PacketEventGuard.suppress();
        try {
            channelRead0(null, packet);
        } catch (Exception error) {
            throw new IllegalStateException("Failed to process packet without events", error);
        } finally {
            PacketEventGuard.restore();
            nimbus$receivingReplacement.set(false);
        }
    }
}
