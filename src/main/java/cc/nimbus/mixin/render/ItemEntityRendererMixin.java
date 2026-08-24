package cc.nimbus.mixin.render;

import cc.nimbus.features.module.modules.render.ItemPhysics;
import cc.nimbus.utils.mixin.MixinModuleLookup;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin {
    @Inject(method = "render(Lnet/minecraft/world/entity/item/ItemEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"))
    private void nimbus$itemPhysics(ItemEntity entity, float yaw, float partialTick, PoseStack poseStack,
                                    MultiBufferSource buffer, int packedLight,
                                    CallbackInfo ci) {
        ItemPhysics module = MixinModuleLookup.enabled(ItemPhysics.class);
        if (module == null) return;

        float bob = Mth.sin((entity.getAge() + partialTick) / 10.0f + entity.bobOffs) * 0.1f + 0.1f;
        if (module.removeBob.getValue()) poseStack.translate(0.0f, -bob, 0.0f);

        float vanillaSpin = entity.getSpin(partialTick);
        poseStack.mulPose(Axis.YP.rotation(-vanillaSpin));
        poseStack.mulPose(Axis.XP.rotationDegrees(module.groundedTilt.getValue().floatValue()));
    }
}
