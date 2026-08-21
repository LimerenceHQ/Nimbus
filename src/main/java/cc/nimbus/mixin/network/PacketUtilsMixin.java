package cc.nimbus.mixin.network;

import cc.nimbus.core.Core;
import net.minecraft.ReportedException;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.server.RunningOnDifferentThreadException;
import net.minecraft.util.thread.BlockableEventLoop;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static net.minecraft.network.protocol.PacketUtils.makeReportedException;

@Mixin(PacketUtils.class)
public abstract class PacketUtilsMixin {

    @Shadow
    @Final
    private static Logger LOGGER;

    @Inject(method = "ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/util/thread/BlockableEventLoop;)V",at = @At("HEAD"),cancellable = true)
    private static <T extends PacketListener> void ensureRunningOnSameThread(Packet<T> p_131364_, T p_131365_, BlockableEventLoop<?> p_131366_, CallbackInfo ci) throws RunningOnDifferentThreadException {
        ci.cancel();
        if (!p_131366_.isSameThread()) {
            p_131366_.executeIfPossible(() -> {
                if (p_131365_.shouldHandleMessage(p_131364_)) {
                    try {
                        Core.INSTANCE.getPacketManager();
                        if (Core.INSTANCE.getPacketManager().inboundsHook(p_131364_)) {
                            p_131364_.handle(p_131365_);
                        }
                    } catch (Exception var4) {
                        if (var4 instanceof ReportedException) {
                            ReportedException reportedException = (ReportedException)var4;
                            if (reportedException.getCause() instanceof OutOfMemoryError) {
                                throw makeReportedException(var4, p_131364_, p_131365_);
                            }
                        }

                        p_131365_.onPacketError(p_131364_, var4);
                    }
                } else {
                    LOGGER.debug("Ignoring packet due to disconnection: {}", p_131364_);
                }

            });
            throw RunningOnDifferentThreadException.RUNNING_ON_DIFFERENT_THREAD;
        }
    }
}
