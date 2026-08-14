package cc.nimbus.mixin;

import cc.nimbus.features.module.modules.render.OldHitting;
import cc.nimbus.utils.mixin.MixinModuleLookup;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
    @Shadow
    public abstract void renderItem(LivingEntity entity, ItemStack stack, ItemDisplayContext displayContext,
                                    PoseStack poseStack, SubmitNodeCollector collector, int packedLight);

    @Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
    private void nimbus$transformHeldItem(AbstractClientPlayer player, float partialTick, float pitch,
                                         InteractionHand hand, float swingProgress, ItemStack stack,
                                         float equippedProgress, PoseStack poseStack,
                                         SubmitNodeCollector collector, int packedLight, CallbackInfo ci) {
        OldHitting module = MixinModuleLookup.enabled(OldHitting.class);
        if (module == null || stack.isEmpty() || !stack.is(ItemTags.SWORDS)
                || player.isUsingItem()) return;

        HumanoidArm arm = hand == InteractionHand.MAIN_HAND
                ? player.getMainArm() : player.getMainArm().getOpposite();
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        poseStack.pushPose();
        module.transform(poseStack, side, equippedProgress, swingProgress, hand, arm);
        renderItem(player, stack, arm == HumanoidArm.RIGHT
                        ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                        : ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                poseStack, collector, packedLight);
        poseStack.popPose();
        ci.cancel();
    }
}
