package cc.nimbus.mixin.input;

import cc.nimbus.event.EventBus;
import cc.nimbus.event.impl.player.EventMoveInput;
import net.minecraft.client.Options;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Input;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends ClientInput {
    @Shadow
    @Final
    private Options options;

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(boolean slowDown, float factor, CallbackInfo ci) {
        EventMoveInput event = new EventMoveInput(this.forwardImpulse, this.leftImpulse, this.keyPresses.jump(), this.options.keySprint.isDown(), this.keyPresses.shift());
        EventBus.call(event);

        if (event.isCancel()) {
            this.forwardImpulse = 0.0F;
            this.leftImpulse = 0.0F;
            this.keyPresses = new Input(false, false, false, false, false, false, false);
            this.options.keySprint.setDown(false);
            return;
        }

        this.forwardImpulse = event.getForward();
        this.leftImpulse = event.getStrafe();
        this.keyPresses = new Input(event.getForward() > 0.0F, event.getForward() < 0.0F,
                event.getStrafe() > 0.0F, event.getStrafe() < 0.0F,
                event.getJump(), event.getSneak(), event.getSprint());
        this.options.keySprint.setDown(event.getSprint() && event.getForward() > 0.0F);
    }
}
