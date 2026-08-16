package cc.nimbus.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import cc.nimbus.Nimbus;
import cc.nimbus.event.EventBus;
import cc.nimbus.event.impl.game.EventClick;
import cc.nimbus.event.impl.game.EventTick;
import cc.nimbus.event.impl.game.EventReadyAttack;
import cc.nimbus.event.impl.game.EventWorldLoad;
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
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void nimbus$readyAttack(CallbackInfoReturnable<Boolean> cir) {
        if (EventBus.call(new EventReadyAttack()).isCancel()) cir.setReturnValue(false);
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
    @Inject(method = "close", at = @At("HEAD")) private void nimbus$close(CallbackInfo ci) { Nimbus.shutdown(); }
}
