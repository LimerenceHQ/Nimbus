package cc.nimbus.mixin.network;

import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketProcessor;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.RunningOnDifferentThreadException;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * Direct Mojang-mapped 1.21.11 translation of the old Yarn
 * NetworkThreadUtils.forceMainThread hook. No client/module/event dependency
 * is used here; PacketProcessor performs vanilla packet dispatch and error
 * handling after the packet is queued.
 */
@Mixin(net.minecraft.network.protocol.PacketUtils.class)
public abstract class MixinNetworkThreadUtils {
    /**
     * @reason direct Yarn-to-Mojang mapping translation
     */
    @Overwrite
    public static <T extends PacketListener> void ensureRunningOnSameThread(
            Packet<T> packet,
            T listener,
            PacketProcessor processor
    ) throws RunningOnDifferentThreadException {
        if (!processor.isSameThread()) {
            processor.scheduleIfPossible(listener, packet);
            throw RunningOnDifferentThreadException.RUNNING_ON_DIFFERENT_THREAD;
        }
    }
}
