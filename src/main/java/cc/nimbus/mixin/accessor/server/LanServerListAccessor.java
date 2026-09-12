package cc.nimbus.mixin.accessor.server;

import net.minecraft.client.server.LanServer;
import net.minecraft.client.server.LanServerDetection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(LanServerDetection.LanServerList.class)
public interface LanServerListAccessor {
    @Accessor("servers")
    List<LanServer> nimbus$getServers();

    @Accessor("isDirty")
    void nimbus$setDirty(boolean dirty);
}
