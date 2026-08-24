package cc.nimbus.mixin.render;

import cc.nimbus.features.module.modules.hud.Potion;
import cc.nimbus.utils.mixin.MixinModuleLookup;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class PotionEffectGuiMixin {
    @Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true)
    private void nimbus$hideVanillaPotionEffects(GuiGraphics graphics, CallbackInfo ci) {
        if (MixinModuleLookup.enabled(Potion.class) != null) ci.cancel();
    }
}
