package cc.nimbus.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import cc.nimbus.Nimbus;
import cc.nimbus.event.handler.EventManager;
import cc.nimbus.event.impl.game.EventTick;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Inject(method = "tick", at = @At("HEAD")) private void nimbus$preTick(CallbackInfo ci) {
        EventManager.call(new EventTick(EventTick.Phase.PRE));
    }
    @Inject(method = "tick", at = @At("TAIL")) private void nimbus$postTick(CallbackInfo ci) {
        EventManager.call(new EventTick(EventTick.Phase.POST));
    }
    @Inject(method = "close", at = @At("HEAD")) private void nimbus$close(CallbackInfo ci) { Nimbus.shutdown(); }
}
