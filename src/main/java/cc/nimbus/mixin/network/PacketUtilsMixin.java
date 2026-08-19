package cc.nimbus.mixin.network;

import cc.nimbus.network.InboundPacketEventPacket;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketProcessor;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.server.RunningOnDifferentThreadException;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies the client-thread handoff to Nimbus receive-event packets. */
@Mixin(PacketUtils.class)
public abstract class PacketUtilsMixin {
    @Inject(method = "ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
            at = @At("HEAD"), cancellable = true)
    private static <T extends PacketListener> void nimbus$ensureRunningOnSameThread(
            Packet<T> packet, T listener, PacketProcessor processor, CallbackInfo ci) {
        if (!(packet instanceof InboundPacketEventPacket<?> eventPacket)) return;

        ci.cancel();
        if (!processor.isSameThread()) {
            nimbus$schedule(processor, listener, eventPacket);
            throw RunningOnDifferentThreadException.RUNNING_ON_DIFFERENT_THREAD;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void nimbus$schedule(PacketProcessor processor, PacketListener listener, Packet<?> packet) {
        PacketProcessor rawProcessor = processor;
        rawProcessor.scheduleIfPossible(listener, (Packet) packet);
    }
}
