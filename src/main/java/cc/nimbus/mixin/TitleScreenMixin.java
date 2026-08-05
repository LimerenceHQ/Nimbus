package cc.nimbus.mixin;

import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import cc.nimbus.core.Core;
import cc.nimbus.ui.menu.TitleScreen;

@Mixin(net.minecraft.client.gui.screens.TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    protected TitleScreenMixin(net.minecraft.network.chat.Component title) { super(title); }

    //@Inject(method = "init", at = @At("TAIL"))
    //private void nimbus$openNimbusMainMenu(CallbackInfo ci) {
    //    var skija = Core.INSTANCE.getRenderManager().skija();
    //    skija.validateSupport();
    //    if (skija.isSupported()) {
    //        minecraft.execute(() -> minecraft.setScreenAndShow(new TitleScreen()));
    //    }
    //}
}
