package cc.nimbus.mixin.render;

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
            method = "getBrightness",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/dimension/DimensionType;ambientLight()F"
            )
    )
    private static float nimbus$ambientLight(DimensionType type) {
        FullBright module = MixinModuleLookup.enabled(FullBright.class);
        return module != null && module.mode.is("Gamma") ? 1.0f : type.ambientLight();
    }

}
