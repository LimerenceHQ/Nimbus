package cc.nimbus.mixin.render;

import cc.nimbus.core.Core;
import cc.nimbus.features.module.modules.render.ESP;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Shadow
    protected EntityModel<?> model;

    @Inject(
            method = "render(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/model/EntityModel;setupAnim("
                            + "Lnet/minecraft/client/renderer/entity/state/EntityRenderState;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void nimbus$captureSkeletonPose(LivingEntityRenderState state, PoseStack poseStack,
                                             MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        if (!Core.INSTANCE.isInitialized()
                || !(model instanceof PlayerModel playerModel)
                || !(state instanceof PlayerRenderState playerState)) return;

        ESP esp = Core.INSTANCE.getModuleManager().getModule(ESP.class);
        if (esp != null && esp.isEnabled() && esp.skeleton.getValue()) {
            esp.captureSkeletonPose(playerState.id, playerModel);
        }
    }
}
