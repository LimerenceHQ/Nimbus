package cc.nimbus.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import cc.nimbus.Nimbus;
import cc.nimbus.event.EventBus;
import cc.nimbus.event.impl.game.EventTick;
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
    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void nimbus$oldHittingSwingWhileUsing(CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (MixinModuleLookup.enabled(OldHitting.class) != null
                && minecraft.player != null
                && minecraft.player.isUsingItem()
                && minecraft.player.getMainHandItem().is(ItemTags.SWORDS)
                && minecraft.options.keyAttack.isDown()) {
            minecraft.player.swing(InteractionHand.MAIN_HAND);
        }
    }
    @Inject(method = "close", at = @At("HEAD")) private void nimbus$close(CallbackInfo ci) { Nimbus.shutdown(); }
}
