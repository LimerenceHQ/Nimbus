package cc.nimbus.mixin.client;

import cc.nimbus.event.impl.game.*;
import cc.nimbus.event.impl.player.EventItemUse;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
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
import cc.nimbus.features.module.modules.combat.BowAssistant;
import cc.nimbus.features.module.modules.combat.AutoTrap;
import cc.nimbus.features.module.modules.combat.KillAura;
import cc.nimbus.features.module.modules.player.FireGuard;
import cc.nimbus.features.module.modules.movement.Scaffold;
import cc.nimbus.features.module.modules.render.OldHitting;
import cc.nimbus.utils.mixin.MixinModuleLookup;
import cc.nimbus.core.Core;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Inject(method = "updateTitle", at = @At("HEAD"), cancellable = true)
    private void nimbus$legacyTitle(CallbackInfo ci) {
        if (!Core.INSTANCE.isInitialized()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getWindow() == null) {
            ci.cancel();
            return;
        }
        if (Core.INSTANCE.getConfigManager().clientSettings().arentIOnePointEight.getValue()) {
            minecraft.getWindow().setTitle("Minecraft 1.8.9");
            ci.cancel();
        }
    }

    @Inject(method = "tick",at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;pick(F)V", shift = At.Shift.BEFORE))
    public void hookRotation(CallbackInfo ci) {
        EventBus.call(new EventRotation());
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void nimbus$preTick(CallbackInfo ci) {
        EventBus.call(new EventTick(EventTick.Phase.PRE));
    }
    @Inject(method = "tick", at = @At("TAIL"))
    private void nimbus$postTick(CallbackInfo ci) {
        EventBus.call(new EventTick(EventTick.Phase.POST));
    }
    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;handleKeybinds()V", shift = At.Shift.BEFORE))
    public void hookRotationApplied(CallbackInfo ci) {
        EventBus.call(new RotationAppliedEvent());
    }

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void onSetLevel(ClientLevel clientLevel, ReceivingLevelScreen.Reason reason, CallbackInfo ci) {

        EventBus.call(new EventWorldLoad());
    }

    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void nimbus$oldHittingSwingWhileUsing(CallbackInfo ci) {
        EventBus.call(new EventClick());
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
        BowAssistant bowAssistant = MixinModuleLookup.enabled(BowAssistant.class);
        if ((bowAssistant != null && bowAssistant.autoReleasedThisTick())
                || nimbus$auraOwnsSwordUse()) {
            ci.cancel();
        }
    }

    @Unique
    private static boolean nimbus$auraOwnsAttack() {
        KillAura aura = MixinModuleLookup.enabled(KillAura.class);
        return aura != null && aura.attackTarget != null;
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
        return fireGuard != null && fireGuard.isActing();
    }

    @Unique
    private static boolean nimbus$autoTrapOwnsBlockBreaking() {
        AutoTrap autoTrap = MixinModuleLookup.enabled(AutoTrap.class);
        return autoTrap != null && autoTrap.ownsBlockBreaking();
    }

    @Inject(method = "close", at = @At("HEAD")) private void nimbus$close(CallbackInfo ci) { Nimbus.shutdown(); }
}
