package cc.nimbus.mixin.render;

import cc.nimbus.features.music.integration.MusicToastState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ToastManager.class)
public abstract class ToastManagerMixin {
    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "render", at = @At("TAIL"))
    private void nimbus$renderMusicToast(GuiGraphics graphics, CallbackInfo ci) {
        if (!MusicToastState.active()) return;
        graphics.drawString(minecraft.font, Component.literal(MusicToastState.text()),
                8, 8, 0xFFFFFFFF, true);
    }
}
