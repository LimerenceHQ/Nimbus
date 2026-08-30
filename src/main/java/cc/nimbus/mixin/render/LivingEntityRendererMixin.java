package cc.nimbus.mixin.render;

import cc.nimbus.core.Core;
import cc.nimbus.event.EventBus;
import cc.nimbus.event.impl.render.EventRotationRender;
import cc.nimbus.features.module.modules.render.ESP;
import cc.nimbus.utils.mixin.LivingEntityRendererAccess;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin
        implements LivingEntityRendererAccess {
    @Shadow
    protected EntityModel<?> model;

    @Unique
    private LivingEntity nimbus$renderEntity;

    @Override
    public EntityModel<?> nimbus$getModel() {
        return model;
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
    private void nimbus$extractRotation(LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo ci) {
        nimbus$renderEntity = entity;
        EventRotationRender.currentEntity = entity;
        if (nimbus$isInventoryPreview() || entity != Minecraft.getInstance().player) return;
        // LivingEntityRenderState.yRot is relative to bodyRot, while the rotation event uses absolute yaw.
        float vanillaHeadYaw = state.bodyRot + state.yRot;
        EventRotationRender event = new EventRotationRender(vanillaHeadYaw, vanillaHeadYaw, state.xRot, state.xRot);
        EventBus.call(event);
        float animatedYaw = Mth.rotLerp(partialTick, event.getLastYaw(), event.getYaw());
        state.yRot = Mth.wrapDegrees(animatedYaw - state.bodyRot);
        state.xRot = Mth.lerp(partialTick, event.getLastPitch(), event.getPitch());
    }

    @Unique
    private static boolean nimbus$isInventoryPreview() {
        return Minecraft.getInstance().screen instanceof InventoryScreen;
    }

    @Inject(method = "render(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("TAIL"))
    private void nimbus$captureSkeletonPose(LivingEntityRenderState state, PoseStack poseStack,
                                             MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        if (!Core.INSTANCE.isInitialized()
                || !(model instanceof PlayerModel playerModel)) return;

        ESP esp = Core.INSTANCE.getModuleManager().getModule(ESP.class);
        if (esp != null && esp.isEnabled() && esp.skeleton.getValue()) {
            if (nimbus$renderEntity != null) {
                esp.captureSkeletonPose(nimbus$renderEntity.getId(), playerModel);
            }
        }
    }
}
