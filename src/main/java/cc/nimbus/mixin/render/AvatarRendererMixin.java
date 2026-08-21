package cc.nimbus.mixin.render;

import cc.nimbus.core.Core;
import cc.nimbus.features.module.modules.render.Nametag;
import cc.nimbus.features.rotation.Rotation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public abstract class AvatarRendererMixin {
    @Inject(
            method = "extractRenderState(Lnet/minecraft/client/player/AbstractClientPlayer;"
                    + "Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;F)V",
            at = @At("TAIL")
    )
    private void nimbus$hideVanillaNametag(AbstractClientPlayer avatar, PlayerRenderState state,
                                           float partialTick, CallbackInfo ci) {
        if (!Core.INSTANCE.isInitialized()) return;
        if (avatar == Minecraft.getInstance().player) {
            Rotation rotation = Core.INSTANCE.getRotationManager().getAnimationRotation(partialTick);
            if (rotation != null) {
                state.bodyRot = rotation.yaw();
                state.yRot = 0.0f;
                state.xRot = rotation.pitch();
            }
        }
        Nametag nametag = Core.INSTANCE.getModuleManager().getModule(Nametag.class);
        if (nametag != null && nametag.isEnabled()) {
            state.nameTag = null;
            state.scoreText = null;
        }
    }
}
