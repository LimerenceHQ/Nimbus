package cc.nimbus.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import cc.nimbus.event.handler.EventManager;
import cc.nimbus.event.impl.game.EventScreenChange;

@Mixin(Gui.class)
public class GuiMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void nimbus$setScreen(Screen screen, CallbackInfo ci) { if (EventManager.call(new EventScreenChange(screen)).isCancel()) ci.cancel(); }
}
