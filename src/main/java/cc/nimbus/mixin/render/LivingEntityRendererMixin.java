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
public abstract class LivingEntityRendererMixin <T extends LivingEntity, M extends EntityModel<T>>
        implements LivingEntityRendererAccess {
    @Shadow
    protected EntityModel<?> model;

    @Override
    public EntityModel<?> nimbus$getModel() {
        return model;
    }

    @Inject(
            method = {"render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"},
            at = {@At("HEAD")}
    )
    private void renderHead(
            T pEntity, float pEntityYaw, float pPartialTicks, PoseStack pMatrixStack, MultiBufferSource pBuffer, int pPackedLight, CallbackInfo ci
    ) {
        EventRotationRender.currentEntity = pEntity;
    }

    @Redirect(
            method = {"render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/Mth;rotLerp(FFF)F",
                    ordinal = 1
            )
    )
    private float rotAnimationYaw(float pDelta, float pStart, float pEnd) {

        if (nimbus$isInventoryPreview()) {
            return Mth.rotLerp(pDelta, pStart, pEnd);
        }

        EventRotationRender event = new EventRotationRender(pEnd, pStart, 0.0F, 0.0F);
        if (EventRotationRender.currentEntity == Minecraft.getInstance().player) {
            EventBus.call(event);
        }

        return Mth.rotLerp(pDelta, event.getLastYaw(), event.getYaw());
    }

    @Redirect(
            method = {"render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/Mth;lerp(FFF)F",
                    ordinal = 0
            )
    )
    private float rotAnimationPitch(float pDelta, float pStart, float pEnd) {

        if (nimbus$isInventoryPreview()) {
            return Mth.lerp(pDelta, pStart, pEnd);
        }

        EventRotationRender event = new EventRotationRender(0.0F, 0.0F, pEnd, pStart);
        if (EventRotationRender.currentEntity == Minecraft.getInstance().player) {
            EventBus.call(event);
        }

        return Mth.lerp(pDelta, event.getLastPitch(), event.getPitch());
    }

    @Unique
    private static boolean nimbus$isInventoryPreview() {
        return Minecraft.getInstance().screen instanceof InventoryScreen;
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("TAIL"))
    private void nimbus$captureSkeletonPose(LivingEntity entity, float yaw, float partialTick, PoseStack poseStack,
                                             MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        if (!Core.INSTANCE.isInitialized()
                || !(entity instanceof AbstractClientPlayer)
                || !(model instanceof PlayerModel<?> playerModel)) return;

        ESP esp = Core.INSTANCE.getModuleManager().getModule(ESP.class);
        if (esp != null && esp.isEnabled() && esp.skeleton.getValue()) {
            esp.captureSkeletonPose(entity.getId(), playerModel);
        }
    }
}
