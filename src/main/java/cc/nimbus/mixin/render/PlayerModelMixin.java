package cc.nimbus.mixin.render;

import cc.nimbus.core.Core;
import cc.nimbus.features.module.modules.render.ESP;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;)V", at = @At("TAIL"))
    private void nimbus$captureSkeletonPose(PlayerRenderState state, CallbackInfo ci) {
        if (!Core.INSTANCE.isInitialized()) return;
        ESP esp = Core.INSTANCE.getModuleManager().getModule(ESP.class);
        if (esp != null && esp.isEnabled() && esp.skeleton.getValue()) {
            esp.captureSkeletonPose(state.id, (PlayerModel) (Object) this);
        }
    }
}
