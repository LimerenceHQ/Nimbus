package cc.nimbus.mixin;

import cc.nimbus.features.module.modules.render.WorldTweaks;
import cc.nimbus.utils.mixin.MixinModuleLookup;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public final class FogRendererMixin {
    @Inject(method = "setupFog", at = @At("RETURN"))
    private void nimbus$worldFog(Camera camera, int renderDistance, DeltaTracker deltaTracker,
                                 float worldDarkening, ClientLevel level,
                                 CallbackInfoReturnable<FogData> cir) {
        WorldTweaks module = MixinModuleLookup.enabled(WorldTweaks.class);
        if (module == null || !module.modifyFog.getValue()) return;

        FogData fog = cir.getReturnValue();
        float start = module.fogStart.getValue().floatValue();
        float end = Math.max(start, module.fogEnd.getValue().floatValue());
        int color = module.fogColor.getValue();
        fog.environmentalStart = start;
        fog.renderDistanceStart = start;
        fog.environmentalEnd = end;
        fog.renderDistanceEnd = end;
        fog.skyEnd = end;
        fog.cloudEnd = end;
        fog.color.set(
                ((color >>> 16) & 0xFF) / 255.0f,
                ((color >>> 8) & 0xFF) / 255.0f,
                (color & 0xFF) / 255.0f,
                1.0f);
    }
}
