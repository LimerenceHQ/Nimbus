package cc.nimbus.mixin;

import cc.nimbus.core.Core;
import cc.nimbus.features.module.modules.render.Nametag;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
    @Inject(
            method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/SubmitNodeCollector;"
                    + "Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void nimbus$hideVanillaNametag(AvatarRenderState state, PoseStack poseStack,
                                           SubmitNodeCollector collector, CameraRenderState camera,
                                           CallbackInfo ci) {
        if (!Core.INSTANCE.isInitialized()) return;
        Nametag nametag = Core.INSTANCE.getModuleManager().getModule(Nametag.class);
        if (nametag != null && nametag.isEnabled()) ci.cancel();
    }
}
