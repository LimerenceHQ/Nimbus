package cc.nimbus.mixin.accessor.client;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Accessor("rightClickDelay")
    int nimbus$getRightClickDelay();

    @Accessor("rightClickDelay")
    void nimbus$setRightClickDelay(int value);
}
