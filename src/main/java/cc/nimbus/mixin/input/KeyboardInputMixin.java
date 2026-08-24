package cc.nimbus.mixin.input;

import cc.nimbus.event.EventBus;
import cc.nimbus.event.impl.player.EventMoveInput;
import net.minecraft.client.Options;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends Input {
    @Shadow
    @Final
    private Options options;

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(boolean slowDown, float factor, CallbackInfo ci) {
        EventMoveInput event = new EventMoveInput(this.forwardImpulse, this.leftImpulse, this.jumping, this.options.keySprint.isDown(), this.shiftKeyDown);
        EventBus.call(event);

        if (event.isCancel()) {
            this.forwardImpulse = 0.0F;
            this.leftImpulse = 0.0F;
            this.up = false;
            this.down = false;
            this.left = false;
            this.right = false;
            this.jumping = false;
            this.shiftKeyDown = false;
            this.options.keySprint.setDown(false);
            return;
        }

        this.forwardImpulse = event.getForward();
        this.leftImpulse = event.getStrafe();
        this.up = event.getForward() > 0.0F;
        this.down = event.getForward() < 0.0F;
        this.left = event.getStrafe() > 0.0F;
        this.right = event.getStrafe() < 0.0F;
        this.jumping = event.getJump();
        this.shiftKeyDown = event.getSneak();
        this.options.keySprint.setDown(event.getSprint() && event.getForward() > 0.0F);
    }
}
