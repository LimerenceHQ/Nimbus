package cc.nimbus.mixin;

import cc.nimbus.features.module.modules.render.BetterThirdPerson;
import cc.nimbus.utils.mixin.MixinModuleLookup;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @ModifyArg(method = "setup", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"), index = 0)
    private float nimbus$thirdPersonDistance(float vanillaDistance) {
        BetterThirdPerson module = MixinModuleLookup.enabled(BetterThirdPerson.class);
        return module == null ? vanillaDistance : module.renderDistance(vanillaDistance);
    }
}
