package cc.nimbus.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import cc.nimbus.ui.alt.GuiAltManager;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    protected TitleScreenMixin(net.minecraft.network.chat.Component title) { super(title); }

    @Inject(method = "init", at = @At("TAIL"))
    private void nimbus$addAltButton(CallbackInfo ci) {
        addRenderableWidget(Button.builder(net.minecraft.network.chat.Component.literal("Alt"),
                        _ -> Minecraft.getInstance().setScreenAndShow(new GuiAltManager(this)))
                .bounds(Math.max(8, width - 68), 10, 60, 20)
                .build());
    }
}