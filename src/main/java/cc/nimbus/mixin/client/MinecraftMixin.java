package cc.nimbus.mixin.client;

import cc.nimbus.event.impl.game.EventRotation;
import cc.nimbus.event.impl.player.EventItemUse;
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
import cc.nimbus.features.module.modules.combat.BowAssistant;
import cc.nimbus.features.module.modules.combat.AutoTrap;
import cc.nimbus.features.module.modules.combat.KillAura;
import cc.nimbus.features.module.modules.player.FireGuard;
import cc.nimbus.features.module.modules.movement.Scaffold;
import cc.nimbus.features.module.modules.render.OldHitting;
import cc.nimbus.utils.mixin.MixinModuleLookup;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Inject(method = "tick",at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;pick(F)V", shift = At.Shift.BEFORE))
    public void hookRotation(CallbackInfo ci) {
        EventBus.call(new EventRotation());
    }

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
        OldHitting oldHitting = MixinModuleLookup.enabled(OldHitting.class);
        if (oldHitting != null
                && minecraft.player != null
                && minecraft.player.isUsingItem()
                && !oldHitting.shouldIgnoreUse(minecraft.player)
                && minecraft.player.getMainHandItem().is(ItemTags.SWORDS)
                && minecraft.options.keyAttack.consumeClick()) {
            minecraft.player.swing(InteractionHand.MAIN_HAND);
        }
    }

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void nimbus$suppressAuraManualAttack(CallbackInfoReturnable<Boolean> cir) {
        if (nimbus$auraOwnsAttack()
                || nimbus$fireGuardOwnsBlockBreaking()
                || nimbus$autoTrapOwnsBlockBreaking()) cir.setReturnValue(false);
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void nimbus$suppressAuraBlockBreaking(boolean attacking, CallbackInfo ci) {
        if (attacking && (nimbus$auraOwnsAttack()
                || nimbus$fireGuardOwnsBlockBreaking()
                || nimbus$autoTrapOwnsBlockBreaking())) ci.cancel();
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void nimbus$suppressOwnedItemUse(CallbackInfo ci) {
        EventItemUse event = new EventItemUse();
        EventBus.call(event);
        if (event.isCancel()) {
            ci.cancel();
        }
        Scaffold scaffold = MixinModuleLookup.enabled(Scaffold.class);
        BowAssistant bowAssistant = MixinModuleLookup.enabled(BowAssistant.class);
        if ((scaffold != null && scaffold.attemptedPlacementThisTick())
                || (bowAssistant != null && bowAssistant.autoReleasedThisTick())
                || nimbus$auraOwnsSwordUse()) {
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

    @Unique
    private static boolean nimbus$fireGuardOwnsBlockBreaking() {
        FireGuard fireGuard = MixinModuleLookup.enabled(FireGuard.class);
        return fireGuard != null && fireGuard.ownsBlockBreaking();
    }

    @Unique
    private static boolean nimbus$autoTrapOwnsBlockBreaking() {
        AutoTrap autoTrap = MixinModuleLookup.enabled(AutoTrap.class);
        return autoTrap != null && autoTrap.ownsBlockBreaking();
    }

    @Inject(method = "close", at = @At("HEAD")) private void nimbus$close(CallbackInfo ci) { Nimbus.shutdown(); }
}
