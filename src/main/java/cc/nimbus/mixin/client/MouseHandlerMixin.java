package cc.nimbus.mixin.client;

import cc.nimbus.event.impl.input.EventMouseButton;
import cc.nimbus.event.impl.input.EventMouseScroll;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import cc.nimbus.event.EventBus;
import cc.nimbus.event.impl.input.*;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void nimbus$button(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        if (EventBus.call(new EventMouseButton(info.button(), action, info.modifiers())).isCancel()) ci.cancel();
    }
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void nimbus$scroll(long window, double x, double y, CallbackInfo ci) {
        if (EventBus.call(new EventMouseScroll(x, y)).isCancel()) ci.cancel();
    }
}
