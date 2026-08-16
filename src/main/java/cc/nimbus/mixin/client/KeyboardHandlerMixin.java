package cc.nimbus.mixin.client;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import cc.nimbus.event.EventBus;
import cc.nimbus.event.impl.input.EventKeyPress;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void nimbus$keyPress(long window, int action, KeyEvent key, CallbackInfo ci) {
        EventKeyPress event = EventBus.call(new EventKeyPress(key.key(), action, key.modifiers()));
        if (event.isCancel()) ci.cancel();
    }
}
