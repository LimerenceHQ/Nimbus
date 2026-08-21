package cc.nimbus.mixin.render;

import cc.nimbus.core.Core;
import cc.nimbus.ui.alt.AccountScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.client.gui.screens.TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    private static final int NIMBUS_BUTTON_WIDTH = 200;
    private static final int NIMBUS_BUTTON_HEIGHT = 20;

    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void nimbus$addAltManagerButton(CallbackInfo ci) {
        int x = (width - NIMBUS_BUTTON_WIDTH) / 2;
        int y = height / 4 + 168;
        Component label = Component.literal(Core.INSTANCE.isInitialized()
                ? Core.INSTANCE.getI18nManager().get("nimbus.menu.alt_manager", "Alt Manager")
                : "Alt Manager");
        addRenderableWidget(Button.builder(label,
                button -> minecraft.setScreen(new AccountScreen(this)))
                .bounds(x, y, NIMBUS_BUTTON_WIDTH, NIMBUS_BUTTON_HEIGHT)
                .build());
    }
}
