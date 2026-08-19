package cc.nimbus.mixin.player;

import cc.nimbus.core.Core;
import cc.nimbus.event.EventBus;
import cc.nimbus.event.EventType;
import cc.nimbus.event.impl.player.EventMotion;
import cc.nimbus.event.impl.player.EventPreUpdate;
import cc.nimbus.event.impl.player.EventSlowDown;
import cc.nimbus.event.impl.player.EventUpdate;
import cc.nimbus.utils.mixin.LocalPlayerStateAccess;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.item.component.UseEffects;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.phys.Vec2;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin extends AbstractClientPlayer implements LocalPlayerStateAccess {
    @Shadow private int positionReminder;
    public LocalPlayerMixin(ClientLevel p_250460_, GameProfile p_249912_) {
        super(p_250460_, p_249912_);
    }

    @Unique private boolean nimbus$motionActive;
    @Unique private double nimbus$oldX;
    @Unique private double nimbus$oldY;
    @Unique private double nimbus$oldZ;
    @Unique private float nimbus$oldYaw;
    @Unique private float nimbus$oldPitch;
    @Unique private boolean nimbus$oldGround;
    @Unique private EventMotion nimbus$motion;
    @Unique private boolean nimbus$cancelItemSlowDown;
    @Final
    public ClientPacketListener connection;
    @Shadow
    @Final
    protected Minecraft minecraft;

    @Shadow
    protected abstract boolean isControlledCamera();

    @Shadow
    protected int sprintTriggerTime;

    @Shadow
    public ClientInput input;

    @Shadow
    private boolean crouching;

    @Shadow
    private int autoJumpTime;

    @Shadow
    protected abstract void moveTowardsClosestSpace(double p_108705_, double p_108706_);

    @Shadow
    protected abstract float itemUseSpeedMultiplier();

    @Shadow
    private static Vec2 modifyInputSpeedForSquareMovement(Vec2 vec2) {
        throw new AssertionError();
    }

    @Shadow
    protected abstract boolean isMovingSlowly();

    @Shadow
    protected abstract void  handlePortalTransitionEffect(boolean b);

    @Shadow
    protected abstract Portal.Transition  getActivePortalLocalTransition();

    @Shadow
    protected abstract boolean canStartSprinting();

    @Shadow
    protected abstract boolean isSlowDueToUsingItem();

    @Shadow
    protected abstract boolean shouldStopSwimSprinting();

    @Shadow
    protected abstract boolean shouldStopRunSprinting();

    @Shadow
    private boolean wasFallFlying;

    @Shadow
    private int waterVisionTime;

    @Shadow
    @Nullable
    public abstract PlayerRideableJumping jumpableVehicle();

    @Shadow
    private int jumpRidingTicks;

    @Shadow
    private float jumpRidingScale;

    @Shadow
    public abstract float getJumpRidingScale();

    @Shadow
    protected abstract void sendRidingJump();

    @Inject(method = "modifyInput", at = @At("HEAD"), cancellable = true)
    private void nimbus$modifyInput(Vec2 vec2, CallbackInfoReturnable<Vec2> cir) {
        if (vec2.lengthSquared() == 0.0F) {
            cir.setReturnValue(vec2);
            return;
        }

        Vec2 movement = vec2.scale(0.98F);
        if (this.isUsingItem() && !this.isPassenger() && !nimbus$cancelItemSlowDown) {
            movement = movement.scale(this.itemUseSpeedMultiplier());
        }
        if (this.isMovingSlowly()) {
            movement = movement.scale((float) this.getAttributeValue(Attributes.SNEAKING_SPEED));
        }
        cir.setReturnValue(modifyInputSpeedForSquareMovement(movement));
    }

    @Inject(method = "aiStep", at = @At("HEAD"), cancellable = true)
    private void onAiStep(CallbackInfo ci) {
        if (!Core.INSTANCE.isInitialized()) return;

        ci.cancel();
        nimbus$cancelItemSlowDown = false;

        EventUpdate preUpdate = new EventUpdate(EventType.PRE);
        EventBus.call(preUpdate);

        if (preUpdate.isCancel()) {
            return;
        }

        if (this.sprintTriggerTime > 0) {
            --this.sprintTriggerTime;
        }

        if (!(this.minecraft.screen instanceof LevelLoadingScreen)) {
            this.handlePortalTransitionEffect(this.getActivePortalLocalTransition() == Portal.Transition.CONFUSION);
            this.processPortalCooldown();
        }

        boolean bl = this.input.keyPresses.jump();
        boolean bl2 = this.input.keyPresses.shift();
        boolean bl3 = this.input.hasForwardImpulse();
        Abilities abilities = this.getAbilities();
        this.crouching = !abilities.flying && !this.isSwimming() && !this.isPassenger() && this.canPlayerFitWithinBlocksAndEntitiesWhen(Pose.CROUCHING) && (this.isShiftKeyDown() || !this.isSleeping() && !this.canPlayerFitWithinBlocksAndEntitiesWhen(Pose.STANDING));
        this.input.tick();
        EventSlowDown slowDown = EventBus.call(new EventSlowDown());
        nimbus$cancelItemSlowDown = slowDown.isCancel();
        this.minecraft.getTutorial().onInput(this.input);
        boolean bl4 = false;
        if (this.autoJumpTime > 0) {
            --this.autoJumpTime;
            bl4 = true;
            this.input.makeJump();
        }

        if (!this.noPhysics) {
            this.moveTowardsClosestSpace(this.getX() - (double)this.getBbWidth() * 0.35, this.getZ() + (double)this.getBbWidth() * 0.35);
            this.moveTowardsClosestSpace(this.getX() - (double)this.getBbWidth() * 0.35, this.getZ() - (double)this.getBbWidth() * 0.35);
            this.moveTowardsClosestSpace(this.getX() + (double)this.getBbWidth() * 0.35, this.getZ() - (double)this.getBbWidth() * 0.35);
            this.moveTowardsClosestSpace(this.getX() + (double)this.getBbWidth() * 0.35, this.getZ() + (double)this.getBbWidth() * 0.35);
        }

        if (bl2
                || this.isSlowDueToUsingItem() && !this.isPassenger() && !nimbus$cancelItemSlowDown
                || this.input.keyPresses.backward()) {
            this.sprintTriggerTime = 0;
        }

        if (this.canStartSprinting()) {
            if (!bl3) {
                if (this.sprintTriggerTime > 0) {
                    this.setSprinting(true);
                } else {
                    this.sprintTriggerTime = (Integer)this.minecraft.options.sprintWindow().get();
                }
            }

            if (this.input.keyPresses.sprint()) {
                this.setSprinting(true);
            }
        }

        if (this.isSprinting()) {
            if (this.isSwimming()) {
                if (this.shouldStopSwimSprinting()) {
                    this.setSprinting(false);
                }
            } else if (this.shouldStopRunSprinting()) {
                this.setSprinting(false);
            }
        }

        boolean bl5 = false;
        if (abilities.mayfly) {
            if (this.minecraft.gameMode.isSpectator()) {
                if (!abilities.flying) {
                    abilities.flying = true;
                    bl5 = true;
                    this.onUpdateAbilities();
                }
            } else if (!bl && this.input.keyPresses.jump() && !bl4) {
                if (this.jumpTriggerTime == 0) {
                    this.jumpTriggerTime = 7;
                } else if (!this.isSwimming() && (this.getVehicle() == null || this.jumpableVehicle() != null)) {
                    abilities.flying = !abilities.flying;
                    if (abilities.flying && this.onGround()) {
                        this.jumpFromGround();
                    }

                    bl5 = true;
                    this.onUpdateAbilities();
                    this.jumpTriggerTime = 0;
                }
            }
        }

        if (this.input.keyPresses.jump() && !bl5 && !bl && !this.onClimbable() && this.tryToStartFallFlying()) {
            this.connection.send(new ServerboundPlayerCommandPacket(this, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
        }

        this.wasFallFlying = this.isFallFlying();
        if (this.isInWater() && this.input.keyPresses.shift() && this.isAffectedByFluids()) {
            this.goDownInWater();
        }

        if (this.isEyeInFluid(FluidTags.WATER)) {
            int i = this.isSpectator() ? 10 : 1;
            this.waterVisionTime = Mth.clamp(this.waterVisionTime + i, 0, 600);
        } else if (this.waterVisionTime > 0) {
            this.isEyeInFluid(FluidTags.WATER);
            this.waterVisionTime = Mth.clamp(this.waterVisionTime - 10, 0, 600);
        }

        if (abilities.flying && this.isControlledCamera()) {
            int i = 0;
            if (this.input.keyPresses.shift()) {
                --i;
            }

            if (this.input.keyPresses.jump()) {
                ++i;
            }

            if (i != 0) {
                this.setDeltaMovement(this.getDeltaMovement().add((double)0.0F, (double)((float)i * abilities.getFlyingSpeed() * 3.0F), (double)0.0F));
            }
        }

        PlayerRideableJumping playerRideableJumping = this.jumpableVehicle();
        if (playerRideableJumping != null && playerRideableJumping.getJumpCooldown() == 0) {
            if (this.jumpRidingTicks < 0) {
                ++this.jumpRidingTicks;
                if (this.jumpRidingTicks == 0) {
                    this.jumpRidingScale = 0.0F;
                }
            }

            if (bl && !this.input.keyPresses.jump()) {
                this.jumpRidingTicks = -10;
                playerRideableJumping.onPlayerJump(Mth.floor(this.getJumpRidingScale() * 100.0F));
                this.sendRidingJump();
            } else if (!bl && this.input.keyPresses.jump()) {
                this.jumpRidingTicks = 0;
                this.jumpRidingScale = 0.0F;
            } else if (bl) {
                ++this.jumpRidingTicks;
                if (this.jumpRidingTicks < 10) {
                    this.jumpRidingScale = (float)this.jumpRidingTicks * 0.1F;
                } else {
                    this.jumpRidingScale = 0.8F + 2.0F / (float)(this.jumpRidingTicks - 9) * 0.1F;
                }
            }
        } else {
            this.jumpRidingScale = 0.0F;
        }


        EventUpdate postUpdate = new EventUpdate(EventType.POST);
        EventBus.call(postUpdate);

        if (postUpdate.isCancel()) {
            return;
        }

        super.aiStep();
        if (this.onGround() && this.getAbilities().flying && !this.minecraft.gameMode.isSpectator()) {
            this.getAbilities().flying = false;
            this.onUpdateAbilities();
        }

    }


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
        EventBus.call(new EventPreUpdate());
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

    @Override
    public void nimbus$setPositionReminder(int ticks) {
        positionReminder = ticks;
    }
}
