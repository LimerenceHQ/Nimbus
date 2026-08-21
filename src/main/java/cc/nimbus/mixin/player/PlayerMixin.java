package cc.nimbus.mixin.player;

import cc.nimbus.event.EventBus;
import cc.nimbus.event.impl.player.EventTravel;
import cc.nimbus.features.module.modules.render.OldHitting;
import cc.nimbus.utils.mixin.FallDistanceAccess;
import cc.nimbus.utils.mixin.MixinModuleLookup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerMixin {
    @Inject(method = "getAttackStrengthScale", at = @At("HEAD"), cancellable = true)
    private void nimbus$oldHittingAttackStrength(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (MixinModuleLookup.enabled(OldHitting.class) != null) {
            cir.setReturnValue(1.0f);
        }
    }

    @Inject(method = "getCurrentItemAttackStrengthDelay", at = @At("HEAD"), cancellable = true)
    private void nimbus$oldHittingAttackDelay(CallbackInfoReturnable<Float> cir) {
        if (MixinModuleLookup.enabled(OldHitting.class) != null) {
            cir.setReturnValue(1.0f);
        }
    }

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void onTravel(Vec3 travelVector, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = (Player) (Object) this;
        if (!(player instanceof LocalPlayer localPlayer) || minecraft.player != localPlayer) {
            return;
        }

        EventTravel event = new EventTravel();
        EventBus.call(event);
        if (event.isCancel()) {
            if (localPlayer instanceof FallDistanceAccess access) {
                access.nimbus$updateFallDistance();
            }
            ci.cancel();
        }
    }
}
