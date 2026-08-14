package cc.nimbus.mixin;

import cc.nimbus.features.module.modules.render.FullBright;
import cc.nimbus.utils.mixin.MixinModuleLookup;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapRenderStateExtractor.class)
public final class LightmapRenderStateExtractorMixin {
    @Unique
    private static final Vector3f NIMBUS_WHITE = new Vector3f(1.0f, 1.0f, 1.0f);

    @Inject(method = "extract", at = @At("RETURN"))
    private void nimbus$fullBright(LightmapRenderState state, float partialTick, CallbackInfo ci) {
        FullBright module = MixinModuleLookup.enabled(FullBright.class);
        if (module == null) return;

        state.needsUpdate = true;
        state.darknessEffectScale = 0.0f;
        state.bossOverlayWorldDarkening = 0.0f;
        if (module.mode.is("Gamma")) {
            state.ambientColor = NIMBUS_WHITE;
        } else {
            state.nightVisionEffectIntensity = 1.0f;
        }
    }
}
