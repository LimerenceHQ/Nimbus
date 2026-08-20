package cc.nimbus.mixin.player;

import cc.nimbus.core.Core;
import cc.nimbus.event.EventBus;
import cc.nimbus.event.impl.player.EventAttack;
import cc.nimbus.features.module.modules.player.Stuck;
import cc.nimbus.features.module.modules.movement.Scaffold;
import cc.nimbus.managers.rotation.Rotation;
import cc.nimbus.utils.mixin.MixinModuleLookup;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
    @Unique private boolean nimbus$replacingAttack;
    @Unique private int nimbus$rotationDepth;
    @Unique private float nimbus$oldYaw;
    @Unique private float nimbus$oldPitch;

    @Shadow public abstract void attack(Player player, Entity target);

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void nimbus$attackPre(Player player, Entity target, CallbackInfo ci) {
        if (nimbus$replacingAttack) return;
        EventAttack event = EventBus.call(new EventAttack(target, EventAttack.Stage.PRE));
        if (event.isCancel() || event.getEntity() == null) {
            ci.cancel();
            return;
        }
        if (event.getEntity() != target) {
            nimbus$replacingAttack = true;
            try {
                attack(player, event.getEntity());
            } finally {
                nimbus$replacingAttack = false;
            }
            ci.cancel();
        }
    }

    @Inject(method = "attack", at = @At("RETURN"))
    private void nimbus$attackPost(Player player, Entity target, CallbackInfo ci) {
        EventBus.call(new EventAttack(target, EventAttack.Stage.POST));
    }

    @Inject(method = "useItem", at = @At("HEAD"), cancellable = true)
    private void nimbus$useItemRotation(Player player, InteractionHand hand,
                                        CallbackInfoReturnable<InteractionResult> cir) {
        if (nimbus$shouldBlockGoldenApple(player, hand)) {
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }
        nimbus$releaseStuckForPearl(player, hand);
        nimbus$pushRotation(player);
    }

    @Inject(method = "useItem", at = @At("RETURN"))
    private void nimbus$restoreUseItem(Player player, InteractionHand hand,
                                       CallbackInfoReturnable<InteractionResult> cir) {
        nimbus$popRotation(player);
    }

    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void nimbus$useItemOnRotation(net.minecraft.client.player.LocalPlayer player, InteractionHand hand,
                                          BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        nimbus$releaseStuckForPearl(player, hand);
        nimbus$pushRotation(player);
    }

    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void nimbus$restoreUseItemOn(net.minecraft.client.player.LocalPlayer player, InteractionHand hand,
                                         BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        nimbus$popRotation(player);
    }

    @Inject(method = "interact", at = @At("HEAD"))
    private void nimbus$interactRotation(Player player, Entity target, InteractionHand hand,
                                          CallbackInfoReturnable<InteractionResult> cir) {
        nimbus$pushRotation(player);
    }

    @Inject(method = "interact", at = @At("RETURN"))
    private void nimbus$restoreInteract(Player player, Entity target, InteractionHand hand,
                                         CallbackInfoReturnable<InteractionResult> cir) {
        nimbus$popRotation(player);
    }

    @Inject(method = "interactAt", at = @At("HEAD"))
    private void nimbus$interactAtRotation(Player player, Entity target, EntityHitResult hit, InteractionHand hand,
                                            CallbackInfoReturnable<InteractionResult> cir) {
        nimbus$pushRotation(player);
    }

    @Inject(method = "interactAt", at = @At("RETURN"))
    private void nimbus$restoreInteractAt(Player player, Entity target, EntityHitResult hit, InteractionHand hand,
                                           CallbackInfoReturnable<InteractionResult> cir) {
        nimbus$popRotation(player);
    }

    @Unique
    private void nimbus$pushRotation(Player player) {
        if (!Core.INSTANCE.isInitialized()) return;
        if (nimbus$rotationDepth++ > 0) return;
        nimbus$oldYaw = player.getYRot();
        nimbus$oldPitch = player.getXRot();
        Rotation rotation = Core.INSTANCE.getRotationManager().getEffectiveRotation();
        player.setYRot(rotation.yaw());
        player.setXRot(rotation.pitch());
    }

    @Unique
    private void nimbus$popRotation(Player player) {
        if (nimbus$rotationDepth <= 0) return;
        if (--nimbus$rotationDepth > 0) return;
        player.setYRot(nimbus$oldYaw);
        player.setXRot(nimbus$oldPitch);
    }

    @Unique
    private static void nimbus$releaseStuckForPearl(Player player, InteractionHand hand) {
        if (player.getItemInHand(hand).is(Items.ENDER_PEARL)) {
            Stuck stuck = MixinModuleLookup.enabled(Stuck.class);
            if (stuck != null) stuck.release(hand);
        }
    }

    @Unique
    private static boolean nimbus$shouldBlockGoldenApple(Player player, InteractionHand hand) {
        Scaffold scaffold = MixinModuleLookup.enabled(Scaffold.class);
        if (scaffold == null) return false;
        return player.getItemInHand(hand).is(Items.GOLDEN_APPLE)
                || player.getItemInHand(hand).is(Items.ENCHANTED_GOLDEN_APPLE);
    }
}
