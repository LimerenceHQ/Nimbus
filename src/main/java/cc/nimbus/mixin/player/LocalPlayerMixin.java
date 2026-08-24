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
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps LocalPlayer movement and packet generation vanilla on 1.20.1. */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin extends AbstractClientPlayer {
    @Shadow
    private double xLast;
    @Shadow
    private double yLast1;
    @Shadow
    private double zLast;
    @Shadow
    private float yRotLast;
    @Shadow
    private float xRotLast;
    @Shadow
    private boolean lastOnGround;
    @Shadow
    private boolean wasShiftKeyDown;
    @Shadow
    private int positionReminder;
    @Shadow
    private boolean autoJumpEnabled;
    @Shadow
    @Final
    public ClientPacketListener connection;
    @Shadow
    @Final
    protected Minecraft minecraft;

    public LocalPlayerMixin(ClientLevel p_250460_, GameProfile p_249912_) {
        super(p_250460_, p_249912_);
    }

    @Shadow
    protected abstract void sendIsSprintingIfNeeded();
    @Shadow
    protected abstract boolean isControlledCamera();

    @Shadow
    protected int sprintTriggerTime;

    @Shadow
    protected abstract void handleNetherPortalClient();

    @Shadow
    public Input input;

    @Shadow
    protected abstract boolean hasEnoughImpulseToStartSprinting();

    @Shadow
    private boolean crouching;

    @Shadow
    public abstract boolean isMovingSlowly();

    @Shadow
    private int autoJumpTime;

    @Shadow
    protected abstract void moveTowardsClosestSpace(double p_108705_, double p_108706_);

    @Shadow
    protected abstract boolean canStartSprinting();

    @Shadow
    protected abstract boolean hasEnoughFoodToStartSprinting();

    @Shadow
    private boolean wasFallFlying;

    @Shadow
    private int waterVisionTime;

    @Shadow
    public abstract PlayerRideableJumping jumpableVehicle();

    @Shadow
    private int jumpRidingTicks;

    @Shadow
    private float jumpRidingScale;

    @Shadow
    public abstract float getJumpRidingScale();

    @Shadow
    protected abstract void sendRidingJump();

    @Inject(method = "tick", at = @At("HEAD"))
    public void injectUpdateEvent(CallbackInfo ci) {
        EventBus.call(new EventTickUpdate());
    }

    @Inject(method = "aiStep", at = @At("HEAD"), cancellable = true)
    private void onAiStep(CallbackInfo ci) {
        if (!Core.INSTANCE.isInitialized()) return;

        ci.cancel();

        EventUpdate preUpdate = new EventUpdate(EventType.PRE);
        EventBus.call(preUpdate);

        if (preUpdate.isCancel()) {
            return;
        }

        if (this.sprintTriggerTime > 0) {
            --this.sprintTriggerTime;
        }

        if (!(this.minecraft.screen instanceof ReceivingLevelScreen)) {
            this.handleNetherPortalClient();
        }

        boolean bl = this.input.jumping;
        boolean bl2 = this.input.shiftKeyDown;
        boolean bl3 = this.hasEnoughImpulseToStartSprinting();
        this.crouching = !this.getAbilities().flying && !this.isSwimming() && this.canEnterPose(Pose.CROUCHING) && (this.isShiftKeyDown() || !this.isSleeping() && !this.canEnterPose(Pose.STANDING));
        float f = Mth.clamp(0.3F + EnchantmentHelper.getSneakingSpeedBonus(this), 0.0F, 1.0F);
        this.input.tick(this.isMovingSlowly(), f);
        this.minecraft.getTutorial().onInput(this.input);
        EventSlowDown event = new EventSlowDown();
        EventBus.call(event);
        if (this.isUsingItem() && !this.isPassenger()) {
            if (!event.isCancel()) {
                this.input.leftImpulse *= 0.2F;
                this.input.forwardImpulse *= 0.2F;
                this.sprintTriggerTime = 0;
            }
        }

        boolean bl4 = false;
        if (this.autoJumpTime > 0) {
            --this.autoJumpTime;
            bl4 = true;
            this.input.jumping = true;
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
                if (!this.onGround() && !this.input.shiftKeyDown && bl8 || !this.isInWater()) {
                    this.setSprinting(false);
                }
            } else if (bl9) {
                this.setSprinting(false);
            }
        }

        boolean bl8 = false;
        if (this.getAbilities().mayfly) {
            if (this.minecraft.gameMode.isAlwaysFlying()) {
                if (!this.getAbilities().flying) {
                    this.getAbilities().flying = true;
                    bl8 = true;
                    this.onUpdateAbilities();
                }
            } else if (!bl && this.input.jumping && !bl4) {
                if (this.jumpTriggerTime == 0) {
                    this.jumpTriggerTime = 7;
                } else if (!this.isSwimming()) {
                    this.getAbilities().flying = !this.getAbilities().flying;
                    bl8 = true;
                    this.onUpdateAbilities();
                    this.jumpTriggerTime = 0;
                }
            }
        }

        if (this.input.jumping && !bl8 && !bl && !this.getAbilities().flying && !this.isPassenger() && !this.onClimbable()) {
            ItemStack itemStack = this.getItemBySlot(EquipmentSlot.CHEST);
            if (itemStack.is(Items.ELYTRA) && ElytraItem.isFlyEnabled(itemStack) && this.tryToStartFallFlying()) {
                this.connection.send(new ServerboundPlayerCommandPacket(this, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
            }
        }

        this.wasFallFlying = this.isFallFlying();
        if (this.isInWater() && this.input.shiftKeyDown && this.isAffectedByFluids()) {
            this.goDownInWater();
        }

        if (this.isEyeInFluid(FluidTags.WATER)) {
            int i = this.isSpectator() ? 10 : 1;
            this.waterVisionTime = Mth.clamp(this.waterVisionTime + i, 0, 600);
        } else if (this.waterVisionTime > 0) {
            this.isEyeInFluid(FluidTags.WATER);
            this.waterVisionTime = Mth.clamp(this.waterVisionTime - 10, 0, 600);
        }

        if (this.getAbilities().flying && this.isControlledCamera()) {
            int i = 0;
            if (this.input.shiftKeyDown) {
                --i;
            }

            if (this.input.jumping) {
                ++i;
            }

            if (i != 0) {
                this.setDeltaMovement(this.getDeltaMovement().add((double)0.0F, (double)((float)i * this.getAbilities().getFlyingSpeed() * 3.0F), (double)0.0F));
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

            if (bl && !this.input.jumping) {
                this.jumpRidingTicks = -10;
                playerRideableJumping.onPlayerJump(Mth.floor(this.getJumpRidingScale() * 100.0F));
                this.sendRidingJump();
            } else if (!bl && this.input.jumping) {
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
        }  else {
            this.jumpRidingScale = 0.0F;
        }

        EventUpdate postUpdate = new EventUpdate(EventType.POST);
        EventBus.call(postUpdate);

        if (postUpdate.isCancel()) {
            return;
        }

        super.aiStep();
        if (this.onGround() && this.getAbilities().flying && !this.minecraft.gameMode.isAlwaysFlying()) {
            this.getAbilities().flying = false;
            this.onUpdateAbilities();
        }

    }

    @Inject(method = "sendPosition", at = @At("HEAD"), cancellable = true)
    private void onSendPosition(CallbackInfo ci) {
        if (!Core.INSTANCE.isInitialized()) return;

        LocalPlayer player = (LocalPlayer) (Object) this;

        EventMotion motionEvent = new EventMotion(
                EventType.PRE,
                player.getYRot(),
                player.getXRot(),
                player.getX(),
                player.getY(),
                player.getZ(),
                player.onGround()
        );
       EventBus.call(motionEvent);

        if (motionEvent.isCancel()) {
            ci.cancel();
            return;
        }

        sendIsSprintingIfNeeded();
        EventBus.call(new EventPreUpdate(EventType.PRE));
        boolean shiftDown = player.isShiftKeyDown();
        if (shiftDown != this.wasShiftKeyDown) {
            ServerboundPlayerCommandPacket.Action action = shiftDown
                    ? ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY
                    : ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY;
            this.connection.send(new ServerboundPlayerCommandPacket(player, action));
            this.wasShiftKeyDown = shiftDown;
        }

        if (isControlledCamera()) {
            double dx = motionEvent.getPosX() - this.xLast;
            double dy = motionEvent.getPosY() - this.yLast1;
            double dz = motionEvent.getPosZ() - this.zLast;
            double dyaw = motionEvent.getYaw() - this.yRotLast;
            double dpitch = motionEvent.getPitch() - this.xRotLast;

            ++this.positionReminder;
            boolean moved = Mth.lengthSquared(dx, dy, dz) > Mth.square(2.0E-4D) || this.positionReminder >= 20;
            boolean rotated = dyaw != 0.0D || dpitch != 0.0D;

            if (player.isPassenger()) {
                Vec3 velocity = player.getDeltaMovement();
                this.connection.send(new ServerboundMovePlayerPacket.PosRot(
                        velocity.x,
                        -999.0D,
                        velocity.z,
                        motionEvent.getYaw(),
                        motionEvent.getPitch(),
                        motionEvent.isGround()
                ));
                moved = false;
            } else if (moved && rotated) {
                this.connection.send(new ServerboundMovePlayerPacket.PosRot(
                        motionEvent.getPosX(),
                        motionEvent.getPosY(),
                        motionEvent.getPosZ(),
                        motionEvent.getYaw(),
                        motionEvent.getPitch(),
                        motionEvent.isGround()
                ));
            } else if (moved) {
                this.connection.send(new ServerboundMovePlayerPacket.Pos(
                        motionEvent.getPosX(),
                        motionEvent.getPosY(),
                        motionEvent.getPosZ(),
                        motionEvent.isGround()
                ));
            } else if (rotated) {
                this.connection.send(new ServerboundMovePlayerPacket.Rot(
                        motionEvent.getYaw(),
                        motionEvent.getPitch(),
                        motionEvent.isGround()
                ));
            } else if (this.lastOnGround != motionEvent.isGround()) {
                this.connection.send(new ServerboundMovePlayerPacket.StatusOnly(motionEvent.isGround()));
            }

            if (moved) {
                this.xLast = motionEvent.getPosX();
                this.yLast1 = motionEvent.getPosY();
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
                EventType.POST,
                motionEvent.getYaw(),
                motionEvent.getPitch(),
                motionEvent.getPosX(),
                motionEvent.getPosY(),
                motionEvent.getPosZ(),
                motionEvent.isGround()
        );
        EventBus.call(postEvent);
        ci.cancel();
    }
}
