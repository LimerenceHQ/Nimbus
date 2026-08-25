package cc.nimbus.mixin.render;

import cc.nimbus.utils.mixin.GuiHudRenderAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ToastComponent.class)
public abstract class ToastComponentMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void nimbus$extractHud(GuiGraphics graphics, CallbackInfo ci) {
        GuiHudRenderAccess gui = nimbus$guiAccess();
        if (gui != null) gui.nimbus$extractHud(graphics);
    }

    private static GuiHudRenderAccess nimbus$guiAccess() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.gui instanceof GuiHudRenderAccess access ? access : null;
    }
}
