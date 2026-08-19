package cc.nimbus.mixin.accessor.player;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({LocalPlayer.class})
public interface LocalPlayerAccessor {
   @Accessor("wasSprinting")
   boolean isWasSprinting();
}
