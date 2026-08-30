package cc.nimbus.mixin.render;

import cc.nimbus.core.Core;
import cc.nimbus.features.module.modules.render.Nametag;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public abstract class AvatarRendererMixin {
    @Inject(method = "renderNameTag(Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"), cancellable = true)
    private void nimbus$hideVanillaNametag(PlayerRenderState avatar, Component name, PoseStack pose,
                                           MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (!Core.INSTANCE.isInitialized()) return;

        Nametag nametag = Core.INSTANCE.getModuleManager().getModule(Nametag.class);
        if (nametag != null && nametag.isEnabled()) {
            ci.cancel();
        }
    }
}
