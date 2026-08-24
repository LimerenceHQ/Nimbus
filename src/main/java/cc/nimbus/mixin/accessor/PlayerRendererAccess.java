package cc.nimbus.mixin.accessor;

import net.minecraft.client.player.AbstractClientPlayer;

public interface PlayerRendererAccess {
    void nimbus$setModelProperties(AbstractClientPlayer player);
}
