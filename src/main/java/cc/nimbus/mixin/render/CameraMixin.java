package cc.nimbus.mixin.render;

import cc.nimbus.features.module.modules.render.BetterThirdPerson;
import cc.nimbus.utils.mixin.MixinModuleLookup;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @ModifyArg(method = "setup", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;getMaxZoom(D)D"), index = 0)
    private double nimbus$thirdPersonDistance(double vanillaDistance) {
        BetterThirdPerson module = MixinModuleLookup.enabled(BetterThirdPerson.class);
        return module == null ? vanillaDistance : (double) module.renderDistance((float) vanillaDistance);
    }
}
