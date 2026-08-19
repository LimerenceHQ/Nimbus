package cc.nimbus.mixin.render;

import cc.nimbus.features.module.modules.hud.PotionHUD;
import cc.nimbus.utils.mixin.MixinModuleLookup;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class PotionEffectGuiMixin {
    @Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true)
    private void nimbus$hideVanillaPotionEffects(GuiGraphics graphics, DeltaTracker delta,
                                                  CallbackInfo ci) {
        if (MixinModuleLookup.enabled(PotionHUD.class) != null) ci.cancel();
    }
}
