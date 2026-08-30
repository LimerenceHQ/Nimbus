package cc.nimbus.mixin.render;

import cc.nimbus.utils.mixin.PlayerRendererAccess;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin implements PlayerRendererAccess {
    @Override
    public void nimbus$setModelProperties(AbstractClientPlayer player) {
        // 1.21.4 applies model visibility while extracting PlayerRenderState.
    }
}
