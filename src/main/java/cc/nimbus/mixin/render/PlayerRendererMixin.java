package cc.nimbus.mixin.render;

import cc.nimbus.mixin.accessor.PlayerRendererAccess;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin implements PlayerRendererAccess {
    @Shadow
    private void setModelProperties(AbstractClientPlayer player) {
        throw new IllegalStateException("Mixin shadow");
    }

    @Override
    public void nimbus$setModelProperties(AbstractClientPlayer player) {
        setModelProperties(player);
    }
}
