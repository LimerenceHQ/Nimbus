package cc.nimbus.mixin.player;

import cc.nimbus.core.Core;
import cc.nimbus.event.EventBus;
import cc.nimbus.event.EventType;
import cc.nimbus.event.impl.game.EventTickUpdate;
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
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
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
    @Shadow private double xLast;
    @Shadow private double yLast;
    @Shadow private double zLast;
    @Shadow private float yRotLast;
    @Shadow private float xRotLast;
    @Shadow private boolean lastOnGround;
    @Shadow private boolean autoJumpEnabled;
    public LocalPlayerMixin(ClientLevel p_250460_, GameProfile p_249912_) {
        super(p_250460_, p_249912_);
    }

    @Unique private boolean nimbus$cancelItemSlowDown;
    @Unique private boolean nimbus$wasShiftKeyDown;
    @Final
    public ClientPacketListener connection;
    @Shadow
    @Final
    protected Minecraft minecraft;

    @Shadow
    protected abstract boolean isControlledCamera();

    @Shadow
    private void sendIsSprintingIfNeeded() {
        throw new AssertionError();
    }

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
    public abstract boolean isMovingSlowly();

    @Shadow
    protected abstract boolean shouldStopSprinting();

    @Shadow
    protected abstract boolean hasEnoughImpulseToStartSprinting();

    @Shadow
    protected abstract boolean hasEnoughFoodToStartSprinting();

    @Shadow
    public abstract Portal.Transition  getActivePortalLocalTransition();

    @Shadow
    protected abstract boolean canStartSprinting();

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

    @Shadow
    protected abstract void handleConfusionTransitionEffect(boolean b);

    @Inject(method = "tick", at = @At("HEAD"))
    public void injectUpdateEvent(CallbackInfo ci) {
        EventBus.call(new EventTickUpdate());
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
            this.handleConfusionTransitionEffect(this.getActivePortalLocalTransition() == Portal.Transition.CONFUSION);
            this.processPortalCooldown();
        }

        boolean bl = this.input.keyPresses.jump();
        boolean bl2 = this.input.keyPresses.shift();
        boolean bl3 = this.hasEnoughImpulseToStartSprinting();
        Abilities abilities = this.getAbilities();
        this.crouching = !abilities.flying && !this.isSwimming() && !this.isPassenger() && this.canPlayerFitWithinBlocksAndEntitiesWhen(Pose.CROUCHING) && (this.isShiftKeyDown() || !this.isSleeping() && !this.canPlayerFitWithinBlocksAndEntitiesWhen(Pose.STANDING));
        this.input.tick();
        this.minecraft.getTutorial().onInput(this.input);
        if (this.shouldStopSprinting()) {
            this.setSprinting(false);
        }
        EventSlowDown event = new EventSlowDown();
        EventBus.call(event);
        if (this.isUsingItem() && !this.isPassenger() && !event.isCancel()) {
            ClientInput var10000 = this.input;
            var10000.leftImpulse *= 0.2F;
            var10000 = this.input;
            var10000.forwardImpulse *= 0.2F;
            this.sprintTriggerTime = 0;
        }

        if (this.isMovingSlowly()) {
            float f = (float)this.getAttributeValue(Attributes.SNEAKING_SPEED);
            ClientInput var17 = this.input;
            var17.leftImpulse *= f;
            var17 = this.input;
            var17.forwardImpulse *= f;
        }


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

        if (bl2) {
            this.sprintTriggerTime = 0;
        }

        boolean bl5 = this.canStartSprinting();
        boolean bl6 = this.isPassenger() ? this.getVehicle().onGround() : this.onGround();
        boolean bl7 = !bl2 && !bl3;
        if ((bl6 || this.isUnderWater()) && bl7 && bl5) {
            if (this.sprintTriggerTime <= 0 && !this.minecraft.options.keySprint.isDown()) {
                this.sprintTriggerTime = 7;
            } else {
                this.setSprinting(true);
            }
        }

        if ((!this.isInWater() || this.isUnderWater()) && bl5 && this.minecraft.options.keySprint.isDown()) {
            this.setSprinting(true);
        }

        if (this.isSprinting()) {
            boolean bl8 = !this.input.hasForwardImpulse() || !this.hasEnoughFoodToStartSprinting();
            boolean bl9 = bl8 || this.horizontalCollision && !this.minorHorizontalCollision || this.isInWater() && !this.isUnderWater();
            if (this.isSwimming()) {
                if (!this.onGround() && !this.input.keyPresses.shift() && bl8 || !this.isInWater()) {
                    this.setSprinting(false);
                }
            } else if (bl9) {
                this.setSprinting(false);
            }
        }

        boolean bl8 = false;
        if (abilities.mayfly) {
            if (this.minecraft.gameMode.isAlwaysFlying()) {
                if (!abilities.flying) {
                    abilities.flying = true;
                    bl8 = true;
                    this.onUpdateAbilities();
                }
            } else if (!bl && this.input.keyPresses.jump() && !bl4) {
                if (this.jumpTriggerTime == 0) {
                    this.jumpTriggerTime = 7;
                } else if (!this.isSwimming()) {
                    abilities.flying = !abilities.flying;
                    if (abilities.flying && this.onGround()) {
                        this.jumpFromGround();
                    }

                    bl8 = true;
                    this.onUpdateAbilities();
                    this.jumpTriggerTime = 0;
                }
            }
        }

        if (this.input.keyPresses.jump() && !bl8 && !bl && !this.onClimbable() && this.tryToStartFallFlying()) {
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
        if (this.onGround() && abilities.flying && !this.minecraft.gameMode.isAlwaysFlying()) {
            abilities.flying = false;
            this.onUpdateAbilities();
        }
    }

    @Inject(method = "sendPosition", at = @At("HEAD"), cancellable = true)
    private void nimbus$onSendPosition(CallbackInfo ci) {
        if (!Core.INSTANCE.isInitialized()) return;

        LocalPlayer player = (LocalPlayer) (Object) this;
        EventMotion motionEvent = new EventMotion(
                player.getX(),
                player.getY(),
                player.getZ(),
                player.getYRot(),
                player.getXRot(),
                player.getYRot(),
                player.getXRot(),
                player.onGround(),
                EventType.PRE
        );
        EventBus.call(motionEvent);

        if (motionEvent.isCancel()) {
            ci.cancel();
            return;
        }

        this.sendIsSprintingIfNeeded();
        EventBus.call(new EventPreUpdate());

        boolean shiftDown = player.isShiftKeyDown();
        if (shiftDown != this.nimbus$wasShiftKeyDown) {
            this.connection.send(new ServerboundPlayerInputPacket(this.input.keyPresses));
            this.nimbus$wasShiftKeyDown = shiftDown;
        }

        if (this.isControlledCamera()) {
            double dx = motionEvent.getPosX() - this.xLast;
            double dy = motionEvent.getPosY() - this.yLast;
            double dz = motionEvent.getPosZ() - this.zLast;
            double dyaw = motionEvent.getYaw() - this.yRotLast;
            double dpitch = motionEvent.getPitch() - this.xRotLast;

            ++this.positionReminder;
            boolean moved = Mth.lengthSquared(dx, dy, dz) > Mth.square(2.0E-4D)
                    || this.positionReminder >= 20;
            boolean rotated = dyaw != 0.0D || dpitch != 0.0D;
            boolean horizontalCollision = player.horizontalCollision;

            if (player.isPassenger()) {
                Vec3 velocity = player.getDeltaMovement();
                this.connection.send(new ServerboundMovePlayerPacket.PosRot(
                        velocity.x,
                        -999.0D,
                        velocity.z,
                        motionEvent.getYaw(),
                        motionEvent.getPitch(),
                        motionEvent.isGround(),
                        horizontalCollision
                ));
                moved = false;
            } else if (moved && rotated) {
                this.connection.send(new ServerboundMovePlayerPacket.PosRot(
                        motionEvent.getPosX(),
                        motionEvent.getPosY(),
                        motionEvent.getPosZ(),
                        motionEvent.getYaw(),
                        motionEvent.getPitch(),
                        motionEvent.isGround(),
                        horizontalCollision
                ));
            } else if (moved) {
                this.connection.send(new ServerboundMovePlayerPacket.Pos(
                        motionEvent.getPosX(),
                        motionEvent.getPosY(),
                        motionEvent.getPosZ(),
                        motionEvent.isGround(),
                        horizontalCollision
                ));
            } else if (rotated) {
                this.connection.send(new ServerboundMovePlayerPacket.Rot(
                        motionEvent.getYaw(),
                        motionEvent.getPitch(),
                        motionEvent.isGround(),
                        horizontalCollision
                ));
            } else if (this.lastOnGround != motionEvent.isGround()) {
                this.connection.send(new ServerboundMovePlayerPacket.StatusOnly(
                        motionEvent.isGround(),
                        horizontalCollision
                ));
            }

            if (moved) {
                this.xLast = motionEvent.getPosX();
                this.yLast = motionEvent.getPosY();
                this.zLast = motionEvent.getPosZ();
                this.positionReminder = 0;
            }

            if (rotated) {
                this.yRotLast = motionEvent.getYaw();
                this.xRotLast = motionEvent.getPitch();
            }

            this.lastOnGround = motionEvent.isGround();
            this.autoJumpEnabled = this.minecraft.options.autoJump().get();
        }

        EventMotion postEvent = new EventMotion(
                motionEvent.getPosX(),
                motionEvent.getPosY(),
                motionEvent.getPosZ(),
                motionEvent.getYaw(),
                motionEvent.getPitch(),
                motionEvent.getCameraYaw(),
                motionEvent.getCameraPitch(),
                motionEvent.isGround(),
                EventType.POST
        );
        EventBus.call(postEvent);
        ci.cancel();
    }



    @Override
    public void nimbus$setPositionReminder(int ticks) {
        positionReminder = ticks;
    }

}
