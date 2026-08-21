package cc.nimbus.mixin.accessor.network;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Mojang-mapped equivalent of the Yarn PlayerMoveC2SPacket accessor. */
@Mixin(ServerboundMovePlayerPacket.class)
public interface ServerboundMovePlayerPacketAccessor {
    @Mutable
    @Accessor("yRot")
    void nimbus$setYaw(float yaw);

    @Mutable
    @Accessor("xRot")
    void nimbus$setPitch(float pitch);

    @Mutable
    @Accessor("hasRot")
    void nimbus$setChangeLook(boolean changeLook);
}
