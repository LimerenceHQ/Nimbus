package cc.nimbus.utils.mixin;

import net.minecraft.client.player.AbstractClientPlayer;

public interface PlayerRendererAccess {
    void nimbus$setModelProperties(AbstractClientPlayer player);
}
