package cc.nimbus.features.module.modules.combat;

import cc.nimbus.core.Core;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.component.Priority;
import cc.nimbus.event.impl.game.EventClick;
import cc.nimbus.event.impl.game.EventTick;
import cc.nimbus.event.impl.game.EventWorldLoad;
import cc.nimbus.event.impl.network.EventPacketReceive;
import cc.nimbus.event.impl.player.EventMoveInput;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.BooleanSetting;
import cc.nimbus.features.module.api.setting.impl.ModeSetting;
import cc.nimbus.features.module.api.setting.impl.MultiSelectSetting;
import cc.nimbus.features.module.api.setting.impl.NumberSetting;
import cc.nimbus.features.rotation.MovementFix;
import cc.nimbus.features.rotation.Rotation;
import cc.nimbus.features.rotation.RotationMath;
import cc.nimbus.features.rotation.RotationPriority;
import cc.nimbus.features.rotation.RotationRaycast;
import cc.nimbus.features.rotation.RotationRequest;
import cc.nimbus.features.rotation.SmoothMode;
import cc.nimbus.managers.PacketDirection;
import cc.nimbus.mixin.accessor.player.MultiPlayerGameModeAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Reduces server knockback while retaining the configurable vanilla and legit paths. */
@ModuleInfo(
        name = "Velocity",
        description = "Reduces incoming knockback with vanilla or legit techniques.",
        category = ModuleCategory.Combat
)
public final class Velocity extends BasicModule {
    private static final int ENTITY_FLAGS_INDEX = 0;
    private static final int SPRINTING_FLAG = 3;

    public final ModeSetting mode = new ModeSetting("Mode", new String[]{"Vanilla", "Legit"}, "Vanilla");
    public final NumberSetting horizontal = new NumberSetting("Horizontal", 0, 0, 100, 1,
            () -> mode.is("Vanilla"));
    public final NumberSetting vertical = new NumberSetting("Vertical", 0, 0, 100, 1,
            () -> mode.is("Vanilla"));
    public final MultiSelectSetting legitModules = new MultiSelectSetting(
            "Legit Modules", new String[]{"Delay", "Jump Reset", "Reduce"},
            () -> mode.is("Legit"));

    public final MultiSelectSetting releaseTiming = new MultiSelectSetting(
            "Release Timing", new String[]{"Until Landing", "Until Sprint"},
            () -> mode.is("Legit") && legitModules.isSelected("Delay"));
    public final ModeSetting releaseMode = new ModeSetting(
            "Release Mode", new String[]{"Release All", "EntityData Delay"}, "Release All",
            () -> mode.is("Legit") && legitModules.isSelected("Delay"));
    public final NumberSetting maxLimitedTick = new NumberSetting(
            "Max Limited Tick", 20, 0, 40, 1,
            () -> mode.is("Legit") && legitModules.isSelected("Delay"));

    public final ModeSetting attackCountMode = new ModeSetting(
            "Attack Count Mode", new String[]{"Custom", "Calculate"}, "Custom",
            () -> mode.is("Legit") && legitModules.isSelected("Reduce"));
    public final NumberSetting count = new NumberSetting(
            "Count", 5, 1, 5, 1,
            () -> mode.is("Legit") && legitModules.isSelected("Reduce") && attackCountMode.is("Custom"));
    public final NumberSetting attackRange = new NumberSetting(
            "Attack Range", 3, 0, 6, 0.01,
            () -> mode.is("Legit") && legitModules.isSelected("Reduce"));
    public final BooleanSetting throughWalls = new BooleanSetting(
            "Through Walls", false,
            () -> mode.is("Legit") && legitModules.isSelected("Reduce"));
    public final NumberSetting throughWallsRange = new NumberSetting(
            "Through Walls Range", 0, 0, 6, 0.01,
            () -> mode.is("Legit") && legitModules.isSelected("Reduce") && throughWalls.getValue());

    private final Minecraft minecraft = Minecraft.getInstance();
    private DelayState delayState = DelayState.IDLE;
    private int bufferTicks = -1;
    private boolean holdingSprintEntityData;
    private int ticksSinceVelocity = -1;
    private int attackCount = -1;
    private boolean handleVelocity;
    private boolean needHandleRotation;
    private RotationData finalRotation = new RotationData(new Rotation(0, 0), 0);
    private boolean handleJumpReset;

    @Override
    protected void onEnable() {
        resetState(false);
    }

    @Override
    protected void onDisable() {
        resetState(true);
    }

    @EventTarget
    private void onWorldLoad(EventWorldLoad event) {
        resetState(false);
    }

    @EventTarget
    private void onTick(EventTick event) {
        if (!event.isPre() || !mode.is("Legit")) return;

        if (attackCountMode.is("Custom") && legitModules.isSelected("Reduce")) {
            attackCount = count.getValue().intValue();
        }

        if (ticksSinceVelocity >= 0) {
            ticksSinceVelocity++;
            if (ticksSinceVelocity >= 10 || ticksSinceVelocity >= attackCount) {
                ticksSinceVelocity = -1;
                handleVelocity = false;
            }
        }

        if (needHandleRotation && minecraft.player != null && minecraft.player.onGround()
                && Math.abs(finalRotation.difference()) <= 45.0f) {
            Core.INSTANCE.getRotationManager().submit(this, new RotationRequest(
                    finalRotation.rotation(), RotationPriority.VERY_HIGH, MovementFix.SILENT,
                    true, SmoothMode.ADVANCED, 180.0f, 0, 0));
            needHandleRotation = false;
        }

        if (!legitModules.isSelected("Delay") || delayState != DelayState.BUFFERING) return;
        if (bufferTicks >= 0) bufferTicks++;

        if (holdingSprintEntityData) {
            if (!handleVelocity) {
                releaseInbound();
                resetDelay();
            }
            return;
        }

        if (!shouldRelease()) return;
        if (releaseMode.is("EntityData Delay")) {
            boolean stoppedAtSprintEntityData = releaseUntilSprintEntityData();
            startVelocityHandling();
            if (stoppedAtSprintEntityData) {
                holdingSprintEntityData = true;
                return;
            }
            resetDelay();
            return;
        }

        releaseInbound();
        startVelocityHandling();
        resetDelay();
    }

    @EventTarget
    private void onPacket(EventPacketReceive event) {
        Packet<?> packet = event.getPacket();
        if (minecraft.player == null || packet == null) return;

        if (packet instanceof ClientboundPlayerPositionPacket) {
            if (delayState == DelayState.BUFFERING) releaseInbound();
            resetDelay();
            handleVelocity = false;
            ticksSinceVelocity = -1;
            needHandleRotation = false;
            return;
        }

        if (mode.is("Vanilla")) {
            applyVanilla(event);
            return;
        }

        if (!(packet instanceof ClientboundSetEntityMotionPacket motion)
                || !isValidVelocityPacket(motion)) return;

        attackCount = computeReduceTicks(motion.getMovement().x, motion.getMovement().z);
        if (legitModules.isSelected("Jump Reset")) {
            prepareJumpReset(motion);
        }

        if (legitModules.isSelected("Delay") && !cantSprint()) {
            if (delayState == DelayState.IDLE && minecraft.player.tickCount > 60
                    && minecraft.level != null && minecraft.getConnection() != null) {
                bufferTicks = 0;
                holdingSprintEntityData = false;
                delayState = DelayState.BUFFERING;
                Core.INSTANCE.getPacketManager().freeze(PacketDirection.INBOUND);
            }
        } else {
            startVelocityHandling();
        }
    }

    @EventTarget(priority = Priority.Lowest)
    private void onMoveInput(EventMoveInput event) {
        if (mode.is("Legit") && legitModules.isSelected("Delay") && delayState == DelayState.BUFFERING) {
            event.setForward(1.0f);
        }
        if (mode.is("Legit") && legitModules.isSelected("Reduce") && handleVelocity) {
            event.setForward(1.0f);
        }

        if (!mode.is("Legit") || !legitModules.isSelected("Jump Reset") || !handleVelocity
                || ticksSinceVelocity < 0 || ticksSinceVelocity >= 4
                || Math.abs(finalRotation.difference()) > 45.0f) {
            handleJumpReset = false;
            return;
        }

        if (minecraft.player != null && minecraft.player.onGround()) {
            handleJumpReset = true;
            event.setJump(true);
        }
        if (handleJumpReset) {
            float[] movement = computeMovement(finalRotation.rotation());
            if (movement[0] != 0.0f) event.setForward(movement[0]);
            if (movement[1] != 0.0f) event.setStrafe(movement[1]);
        }
    }

    @EventTarget
    private void onClick(EventClick event) {
        if (!mode.is("Legit") || !legitModules.isSelected("Reduce") || !handleVelocity) return;
        attackTargetAfterVelocity();
    }

    private void applyVanilla(EventPacketReceive event) {
        if (!(event.getPacket() instanceof ClientboundSetEntityMotionPacket packet)
                || minecraft.player == null || packet.getId() != minecraft.player.getId()) return;

        Vec3 movement = packet.getMovement();
        double horizontalScale = horizontal.getValue().doubleValue() / 100.0;
        double verticalScale = vertical.getValue().doubleValue() / 100.0;
        event.setPacket(new ClientboundSetEntityMotionPacket(
                packet.getId(), new Vec3(
                        movement.x * horizontalScale,
                        movement.y * verticalScale,
                        movement.z * horizontalScale)));
    }

    private boolean isValidVelocityPacket(ClientboundSetEntityMotionPacket packet) {
        if (minecraft.player == null || packet.getId() != minecraft.player.getId()) return false;
        Vec3 movement = packet.getMovement();
        return movement.y > 0.0
                && new Vec3(movement.x, 0.0, movement.z).length() >= 0.1
                && !minecraft.player.onClimbable()
                && !minecraft.player.isInWater()
                && !minecraft.player.isInLava();
    }

    private boolean cantSprint() {
        if (minecraft.player == null) return true;
        return minecraft.player.hasEffect(MobEffects.BLINDNESS)
                || minecraft.player.hasEffect(MobEffects.SLOWNESS)
                || minecraft.player.isShiftKeyDown()
                || minecraft.player.isCrouching()
                || (!minecraft.player.isPassenger()
                && minecraft.player.getFoodData().getFoodLevel() <= 6
                && !minecraft.player.getAbilities().mayfly);
    }

    private boolean shouldRelease() {
        int maxTicks = maxLimitedTick.getValue().intValue();
        boolean untilLanding = releaseTiming.isSelected("Until Landing");
        boolean untilSprint = releaseTiming.isSelected("Until Sprint");
        boolean onGround = minecraft.player != null && minecraft.player.onGround();
        boolean sprinting = minecraft.player != null && minecraft.player.isSprinting();
        boolean timedOut = bufferTicks >= maxTicks;
        boolean cannotSprint = cantSprint();

        if (untilLanding && untilSprint) return (onGround && sprinting) || timedOut || cannotSprint;
        if (untilLanding) return onGround || timedOut || cannotSprint;
        if (untilSprint) return sprinting || timedOut || cannotSprint;
        return timedOut || cannotSprint;
    }

    private void prepareJumpReset(ClientboundSetEntityMotionPacket packet) {
        Vec3 horizontalKnockback = new Vec3(packet.getMovement().x, 0.0, packet.getMovement().z);
        if (horizontalKnockback.lengthSqr() <= 1.0E-8 || minecraft.player == null) return;
        Vec3 opposite = minecraft.player.position().subtract(horizontalKnockback.normalize());
        Rotation target = RotationMath.toRotation(minecraft.player.getEyePosition(), opposite);
        Rotation current = Core.INSTANCE.getRotationManager().getEffectiveRotation();
        finalRotation = new RotationData(new Rotation(target.yaw(), current.pitch()),
                Mth.wrapDegrees(target.yaw() - current.yaw()));
        needHandleRotation = true;
        if (minecraft.player.onGround() && Math.abs(finalRotation.difference()) <= 45.0f) {
            Core.INSTANCE.getRotationManager().submit(this, new RotationRequest(
                    finalRotation.rotation(), RotationPriority.VERY_HIGH, MovementFix.SILENT,
                    true, SmoothMode.ADVANCED, 180.0f, 0, 0));
            needHandleRotation = false;
        }
    }

    private int computeReduceTicks(double motionX, double motionZ) {
        // ClientboundSetEntityMotionPacket exposes movement in blocks/tick; the
        // reference formula was calibrated against the old 1/8000 wire values.
        double knockback = Math.hypot(motionX, motionZ) * 8000.0;
        double value = 0.000408163 * knockback + 0.7142857;
        return Mth.clamp((int) Math.round(value), 1, 5);
    }

    private float[] computeMovement(Rotation target) {
        if (minecraft.player == null) return new float[]{0.0f, 0.0f};
        float deltaYaw = Mth.wrapDegrees(target.yaw()
                - Core.INSTANCE.getRotationManager().getEffectiveRotation().yaw());
        if (deltaYaw > 22.5f) deltaYaw -= 45.0f;
        else if (deltaYaw < -22.5f) deltaYaw += 45.0f;

        double radians = Math.toRadians(deltaYaw);
        double x = Math.sin(radians);
        double z = Math.cos(radians);
        float forward = z > 0.707 ? 1.0f : z < -0.707 ? -1.0f : 0.0f;
        float strafe = x > 0.707 ? -1.0f : x < -0.707 ? 1.0f : 0.0f;
        return new float[]{forward, strafe};
    }

    private void startVelocityHandling() {
        ticksSinceVelocity = 0;
        handleVelocity = true;
        if (legitModules.isSelected("Jump Reset")) {
            needHandleRotation = true;
            if (minecraft.player != null && minecraft.player.onGround()
                    && Math.abs(finalRotation.difference()) <= 45.0f) {
                Core.INSTANCE.getRotationManager().submit(this, new RotationRequest(
                        finalRotation.rotation(), RotationPriority.VERY_HIGH, MovementFix.SILENT,
                        true, SmoothMode.ADVANCED, 180.0f, 0, 0));
                needHandleRotation = false;
            }
        }
    }

    private boolean releaseUntilSprintEntityData() {
        while (Core.INSTANCE.getPacketManager().queueSize(PacketDirection.INBOUND) > 0) {
            Packet<?> packet = Core.INSTANCE.getPacketManager().peek(PacketDirection.INBOUND);
            if (isSprintEntityData(packet)) return true;
            packet = Core.INSTANCE.getPacketManager().take(PacketDirection.INBOUND);
            if (packet != null) cc.nimbus.utils.network.PacketUtils.receivePacketNoEvent(packet);
        }
        Core.INSTANCE.getPacketManager().dispatch(PacketDirection.INBOUND);
        return false;
    }

    private boolean isSprintEntityData(Packet<?> packet) {
        if (minecraft.player == null || !(packet instanceof ClientboundSetEntityDataPacket data)
                || data.id() != minecraft.player.getId()) return false;
        for (SynchedEntityData.DataValue<?> value : data.packedItems()) {
            if (value.id() == ENTITY_FLAGS_INDEX && value.value() instanceof Byte flags) {
                return (flags & (1 << SPRINTING_FLAG)) == 0;
            }
        }
        return false;
    }

    private void attackTargetAfterVelocity() {
        if (minecraft.player == null || !minecraft.player.isSprinting()
                || minecraft.level == null || minecraft.gameMode == null
                || minecraft.getConnection() == null) return;
        HitResult hit = RotationRaycast.raycast(
                minecraft.player, Core.INSTANCE.getRotationManager().getEffectiveRotation(),
                attackRange.getValue().doubleValue(), false, Entity::isPickable);
        Player target = hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof Player player
                ? player : null;
        if (target == null) {
            if (!throughWalls.getValue() || throughWallsRange.getValue().doubleValue() <= 0.0) return;
            hit = RotationRaycast.raycast(
                    minecraft.player, Core.INSTANCE.getRotationManager().getEffectiveRotation(),
                    throughWallsRange.getValue().doubleValue(), true, Entity::isPickable);
            if (!(hit instanceof EntityHitResult entityHit) || !(entityHit.getEntity() instanceof Player player)) return;
            target = player;
        }

        ((MultiPlayerGameModeAccessor) minecraft.gameMode).nimbus$ensureHasSentCarriedItem();
        minecraft.getConnection().send(ServerboundInteractPacket.createAttackPacket(
                target, minecraft.player.isShiftKeyDown()));
        if (minecraft.gameMode.getPlayerMode() != GameType.SPECTATOR) {
            minecraft.player.setDeltaMovement(minecraft.player.getDeltaMovement().multiply(0.6, 1.0, 0.6));
            minecraft.player.setSprinting(false);
        }
        minecraft.player.swing(InteractionHand.MAIN_HAND);
    }

    private void releaseInbound() {
        Core.INSTANCE.getPacketManager().dispatch(PacketDirection.INBOUND);
    }

    private void resetState(boolean releasePackets) {
        if (releasePackets && Core.INSTANCE.isInitialized()
                && Core.INSTANCE.getPacketManager().isFrozen(PacketDirection.INBOUND)) {
            releaseInbound();
        }
        if (Core.INSTANCE.isInitialized()) Core.INSTANCE.getRotationManager().cancel(this);
        resetDelay();
        ticksSinceVelocity = -1;
        attackCount = -1;
        handleVelocity = false;
        needHandleRotation = false;
        handleJumpReset = false;
        finalRotation = new RotationData(new Rotation(0, 0), 0);
    }

    private void resetDelay() {
        bufferTicks = -1;
        delayState = DelayState.IDLE;
        holdingSprintEntityData = false;
    }

    private enum DelayState {
        IDLE,
        BUFFERING
    }

    private record RotationData(Rotation rotation, float difference) {
    }
}
