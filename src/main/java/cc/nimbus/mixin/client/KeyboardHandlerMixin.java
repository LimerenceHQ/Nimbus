package cc.nimbus.mixin.client;

import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import cc.nimbus.event.EventBus;
import cc.nimbus.event.impl.input.EventKeyPress;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void nimbus$keyPress(long window, int key, int scanCode, int action, int modifiers, CallbackInfo ci) {
        EventKeyPress event = EventBus.call(new EventKeyPress(key, action, modifiers));
        if (event.isCancel()) ci.cancel();
    }
}
