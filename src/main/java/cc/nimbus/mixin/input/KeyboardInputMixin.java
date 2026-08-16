package cc.nimbus.mixin.input;

import cc.nimbus.event.EventBus;
import cc.nimbus.event.impl.player.EventMoveInput;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends ClientInput {
    @Inject(method = "tick", at = @At("TAIL"))
    private void nimbus$moveInput(CallbackInfo ci) {
        float originalForward = axis(keyPresses.forward(), keyPresses.backward());
        float originalStrafe = axis(keyPresses.left(), keyPresses.right());
        EventMoveInput event = EventBus.call(new EventMoveInput(originalForward, originalStrafe, keyPresses.jump()));
        float forward = event.isCancel() ? 0.0f : event.getForward();
        float strafe = event.isCancel() ? 0.0f : event.getStrafe();
        Vec2 next = new Vec2(strafe, forward);
        moveVector = next.lengthSquared() > 1.0f ? next.normalized() : next;
        keyPresses = new Input(
                forward > 0.0f,
                forward < 0.0f,
                strafe > 0.0f,
                strafe < 0.0f,
                !event.isCancel() && event.getJump(),
                keyPresses.shift(),
                keyPresses.sprint()
        );
    }

    private static float axis(boolean positive, boolean negative) {
        if (positive == negative) return 0.0f;
        return positive ? 1.0f : -1.0f;
    }
}
