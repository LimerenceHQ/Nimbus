package cc.nimbus.mixin.player;

import cc.nimbus.event.EventBus;
import cc.nimbus.event.EventType;
import cc.nimbus.event.impl.player.EventMotion;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @Unique private boolean nimbus$motionActive;
    @Unique private double nimbus$oldX;
    @Unique private double nimbus$oldY;
    @Unique private double nimbus$oldZ;
    @Unique private float nimbus$oldYaw;
    @Unique private float nimbus$oldPitch;
    @Unique private boolean nimbus$oldGround;
    @Unique private EventMotion nimbus$motion;

    @Inject(method = "sendPosition", at = @At("HEAD"), cancellable = true)
    private void nimbus$preMotion(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        nimbus$oldX = player.getX();
        nimbus$oldY = player.getY();
        nimbus$oldZ = player.getZ();
        nimbus$oldYaw = player.getYRot();
        nimbus$oldPitch = player.getXRot();
        nimbus$oldGround = player.onGround();

        nimbus$motion = EventBus.call(new EventMotion(
                nimbus$oldX, nimbus$oldY, nimbus$oldZ,
                nimbus$oldYaw, nimbus$oldPitch,
                nimbus$oldYaw, nimbus$oldPitch,
                nimbus$oldGround, EventType.PRE));
        if (nimbus$motion.isCancel()) {
            EventBus.call(new EventMotion(
                    nimbus$motion.getPosX(), nimbus$motion.getPosY(), nimbus$motion.getPosZ(),
                    nimbus$motion.getYaw(), nimbus$motion.getPitch(),
                    nimbus$oldYaw, nimbus$oldPitch,
                    nimbus$motion.isGround(), EventType.POST));
            nimbus$motion = null;
            ci.cancel();
            return;
        }

        player.setPos(nimbus$motion.getPosX(), nimbus$motion.getPosY(), nimbus$motion.getPosZ());
        player.setYRot(nimbus$motion.getYaw());
        player.setXRot(nimbus$motion.getPitch());
        player.setOnGround(nimbus$motion.isGround());
        nimbus$motionActive = true;
    }

    @Inject(method = "sendPosition", at = @At("RETURN"))
    private void nimbus$postMotion(CallbackInfo ci) {
        if (!nimbus$motionActive) return;
        LocalPlayer player = (LocalPlayer) (Object) this;
        EventMotion motion = nimbus$motion;
        player.setPos(nimbus$oldX, nimbus$oldY, nimbus$oldZ);
        player.setYRot(nimbus$oldYaw);
        player.setXRot(nimbus$oldPitch);
        player.setOnGround(nimbus$oldGround);
        nimbus$motionActive = false;
        nimbus$motion = null;
        EventBus.call(new EventMotion(
                motion.getPosX(), motion.getPosY(), motion.getPosZ(),
                motion.getYaw(), motion.getPitch(),
                nimbus$oldYaw, nimbus$oldPitch,
                motion.isGround(), EventType.POST));
    }
}
