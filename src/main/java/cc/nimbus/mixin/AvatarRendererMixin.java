package cc.nimbus.mixin;

import cc.nimbus.core.Core;
import cc.nimbus.features.module.modules.render.Nametag;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;"
                    + "Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL")
    )
    private void nimbus$hideVanillaNametag(Avatar avatar, AvatarRenderState state,
                                           float partialTick, CallbackInfo ci) {
        if (!Core.INSTANCE.isInitialized()) return;
        Nametag nametag = Core.INSTANCE.getModuleManager().getModule(Nametag.class);
        if (nametag != null && nametag.isEnabled()) {
            state.nameTag = null;
            state.scoreText = null;
        }
    }
}
