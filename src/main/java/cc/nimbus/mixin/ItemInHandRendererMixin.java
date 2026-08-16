package cc.nimbus.mixin;

import cc.nimbus.core.Core;
import cc.nimbus.features.module.modules.combat.KillAura;
import cc.nimbus.features.module.modules.render.OldHitting;
import cc.nimbus.utils.mixin.MixinModuleLookup;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
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
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
    @Shadow
    public abstract void renderItem(LivingEntity entity, ItemStack stack, ItemDisplayContext displayContext,
                                    PoseStack poseStack, SubmitNodeCollector collector, int packedLight);

    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void nimbus$transformHeldItem(AbstractClientPlayer player, float partialTick, float pitch,
                                         InteractionHand hand, float swingProgress, ItemStack stack,
                                         float equippedProgress, PoseStack poseStack,
                                         SubmitNodeCollector collector, int packedLight, CallbackInfo ci) {
        OldHitting module = MixinModuleLookup.enabled(OldHitting.class);
        KillAura aura = MixinModuleLookup.enabled(KillAura.class);
        boolean auraBlocking = aura != null && aura.isRenderBlocking();
        if (module == null && auraBlocking && Core.INSTANCE.isInitialized()) {
            module = Core.INSTANCE.getModuleManager().getModule(OldHitting.class);
        }
        if (module == null || hand != InteractionHand.MAIN_HAND || stack.isEmpty()
                || !stack.is(ItemTags.SWORDS) || player.isScoping()
                || (!Minecraft.getInstance().options.keyUse.isDown() && !auraBlocking)) return;

        HumanoidArm arm = player.getMainArm();
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        poseStack.pushPose();
        module.transform(poseStack, side, equippedProgress, swingProgress, arm);
        renderItem(player, stack, arm == HumanoidArm.RIGHT
                        ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                        : ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                poseStack, collector, packedLight);
        poseStack.popPose();
        ci.cancel();
    }

    @Redirect(
            method = "renderArmWithItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"
            )
    )
    private void nimbus$scaleHeldSword(ItemInHandRenderer renderer, LivingEntity entity, ItemStack stack,
                                       ItemDisplayContext displayContext, PoseStack poseStack,
                                       SubmitNodeCollector collector, int packedLight) {
        OldHitting module = MixinModuleLookup.enabled(OldHitting.class);
        boolean mainHand = entity instanceof AbstractClientPlayer player
                && (player.getMainArm() == HumanoidArm.RIGHT
                ? displayContext == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                : displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND);
        if (module == null || !mainHand || !stack.is(ItemTags.SWORDS)) {
            renderItem(entity, stack, displayContext, poseStack, collector, packedLight);
            return;
        }

        float scale = module.scale.getValue().floatValue();
        poseStack.pushPose();
        poseStack.scale(scale, scale, scale);
        renderItem(entity, stack, displayContext, poseStack, collector, packedLight);
        poseStack.popPose();
    }
}
