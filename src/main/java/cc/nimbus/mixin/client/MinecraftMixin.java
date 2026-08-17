package cc.nimbus.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import cc.nimbus.Nimbus;
import cc.nimbus.event.EventBus;
import cc.nimbus.event.impl.game.EventClick;
import cc.nimbus.event.impl.game.EventTick;
import cc.nimbus.event.impl.game.EventWorldLoad;
import cc.nimbus.features.module.modules.combat.KillAura;
import cc.nimbus.features.module.modules.movement.Scaffold;
import cc.nimbus.features.module.modules.render.OldHitting;
import cc.nimbus.utils.mixin.MixinModuleLookup;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Inject(method = "tick", at = @At("HEAD")) private void nimbus$preTick(CallbackInfo ci) {
        EventBus.call(new EventTick(EventTick.Phase.PRE));
    }
    @Inject(method = "tick", at = @At("TAIL")) private void nimbus$postTick(CallbackInfo ci) {
        EventBus.call(new EventTick(EventTick.Phase.POST));
    }
    @Inject(method = "setLevel", at = @At("TAIL"))
    private void nimbus$worldLoad(ClientLevel level, CallbackInfo ci) {
        EventBus.call(new EventWorldLoad(level));
    }
    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void nimbus$oldHittingSwingWhileUsing(CallbackInfo ci) {
        EventBus.call(new EventClick());
        Minecraft minecraft = Minecraft.getInstance();
        if (MixinModuleLookup.enabled(OldHitting.class) != null
                && minecraft.player != null
                && minecraft.player.isUsingItem()
                && minecraft.player.getMainHandItem().is(ItemTags.SWORDS)
                && minecraft.options.keyAttack.consumeClick()) {
            minecraft.player.swing(InteractionHand.MAIN_HAND);
        }
    }

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void nimbus$suppressAuraManualAttack(CallbackInfoReturnable<Boolean> cir) {
        if (nimbus$auraOwnsAttack()) cir.setReturnValue(false);
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void nimbus$suppressAuraBlockBreaking(boolean attacking, CallbackInfo ci) {
        if (attacking && nimbus$auraOwnsAttack()) ci.cancel();
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void nimbus$suppressOwnedItemUse(CallbackInfo ci) {
        Scaffold scaffold = MixinModuleLookup.enabled(Scaffold.class);
        if ((scaffold != null && scaffold.attemptedPlacementThisTick()) || nimbus$auraOwnsSwordUse()) {
            ci.cancel();
        }
    }

    @Unique
    private static boolean nimbus$auraOwnsAttack() {
        KillAura aura = MixinModuleLookup.enabled(KillAura.class);
        return aura != null && aura.getAttackTarget() != null;
    }

    @Unique
    private static boolean nimbus$auraOwnsSwordUse() {
        Minecraft minecraft = Minecraft.getInstance();
        return nimbus$auraOwnsAttack()
                && minecraft.player != null
                && minecraft.player.getMainHandItem().is(ItemTags.SWORDS);
    }

    @Inject(method = "close", at = @At("HEAD")) private void nimbus$close(CallbackInfo ci) { Nimbus.shutdown(); }
}
