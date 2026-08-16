package cc.nimbus.mixin.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import cc.nimbus.event.EventBus;
import cc.nimbus.event.impl.game.EventScreenChange;

@Mixin(Minecraft.class)
public class GuiMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void nimbus$setScreen(Screen screen, CallbackInfo ci) { if (EventBus.call(new EventScreenChange(screen)).isCancel()) ci.cancel(); }
}
