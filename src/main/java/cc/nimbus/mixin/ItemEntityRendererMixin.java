package cc.nimbus.mixin;

import cc.nimbus.features.module.modules.render.ItemPhysics;
import cc.nimbus.features.module.modules.render.item.PhysicalItemRenderState;
import cc.nimbus.utils.mixin.MixinModuleLookup;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;F)V",
            at = @At("RETURN"))
    private void nimbus$extractGrounded(ItemEntity entity, ItemEntityRenderState state,
                                        float partialTick, CallbackInfo ci) {
        ((PhysicalItemRenderState) state).nimbus$setGrounded(entity.onGround());
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/ItemEntityRenderer;submitMultipleFromCount(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/ItemClusterRenderState;Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/phys/AABB;)V"))
    private void nimbus$itemPhysics(ItemEntityRenderState state, PoseStack poseStack,
                                    SubmitNodeCollector collector, CameraRenderState camera,
                                    CallbackInfo ci) {
        ItemPhysics module = MixinModuleLookup.enabled(ItemPhysics.class);
        if (module == null) return;

        if (((PhysicalItemRenderState) state).nimbus$isGrounded()) {
            if (module.removeBob.getValue()) {
                float bob = Mth.sin(state.ageInTicks / 10.0f + state.bobOffset) * 0.1f + 0.1f;
                poseStack.translate(0.0f, -bob, 0.0f);
            }
            float spin = ItemEntity.getSpin(state.ageInTicks, state.bobOffset);
            poseStack.mulPose(Axis.YP.rotation(-spin));
            poseStack.mulPose(Axis.YP.rotationDegrees(Math.floorMod(state.seed, 360)));
            poseStack.mulPose(Axis.XP.rotationDegrees(module.groundedTilt.getValue().floatValue()));
        } else if (module.airRotation.getValue()) {
            float speed = module.rotationSpeed.getValue().floatValue();
            float angle = state.ageInTicks * speed;
            poseStack.mulPose(Axis.XP.rotationDegrees(angle + Math.floorMod(state.seed, 180)));
            poseStack.mulPose(Axis.ZP.rotationDegrees(angle * 0.65f + Math.floorMod(state.seed >>> 8, 180)));
        }
    }
}
