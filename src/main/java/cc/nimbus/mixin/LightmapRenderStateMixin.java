package cc.nimbus.mixin;

import cc.nimbus.features.module.modules.render.FullBright;
import cc.nimbus.utils.mixin.MixinModuleLookup;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LightTexture.class)
public abstract class LightmapRenderStateMixin {
    @Redirect(
            method = "updateLightTexture",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/dimension/DimensionType;ambientLight()F"
            )
    )
    private float nimbus$ambientLight(DimensionType type) {
        FullBright module = MixinModuleLookup.enabled(FullBright.class);
        return module != null && module.mode.is("Gamma") ? 1.0f : type.ambientLight();
    }

    @Redirect(
            method = "updateLightTexture",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;hasEffect(Lnet/minecraft/core/Holder;)Z",
                    ordinal = 0
            )
    )
    private boolean nimbus$hasNightVision(LocalPlayer player, Holder<MobEffect> effect) {
        FullBright module = MixinModuleLookup.enabled(FullBright.class);
        return (module != null && module.mode.is("Night Vision")) || player.hasEffect(effect);
    }

}
