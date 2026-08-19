package cc.nimbus.features.module.modules.combat;

import cc.nimbus.core.Core;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.component.Priority;
import cc.nimbus.event.impl.game.EventClick;
import cc.nimbus.event.impl.game.EventRotation;
import cc.nimbus.event.impl.game.EventTick;
import cc.nimbus.event.impl.game.EventWorldLoad;
import cc.nimbus.event.impl.network.EventPacketReceive;
import cc.nimbus.event.impl.network.EventPacketSend;
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
import cc.nimbus.managers.GameStateManager;
import cc.nimbus.managers.PacketDirection;
import cc.nimbus.mixin.accessor.network.ServerboundInteractPacketAccessor;
import cc.nimbus.mixin.accessor.player.MultiPlayerGameModeAccessor;
import cc.nimbus.utils.entity.rotation.RotationUtils;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

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
    public boolean handleVelocity;
    private boolean needHandleRotation;
    private RotationData finalRotation = new RotationData(new Rotation(0, 0), 0);
    private boolean handleJumpReset;
    /** Last server-authoritative position for entities whose movement is shown immediately. */
    private final Map<Integer, Vec3> serverPositions = new HashMap<>();

    @Override
    protected void onEnable() {
        resetState(true);
    }

    @Override
    protected void onDisable() {
        resetState(true);
    }

    @EventTarget
    private void onWorldLoad(EventWorldLoad event) {
        resetState(true);
        serverPositions.clear();
    }

    @EventTarget
    private void onRotate(EventRotation event) {
        if (needHandleRotation && mc.player != null && mc.player.onGround()
                && Math.abs(finalRotation.difference()) <= 45.0f) {
            Core.INSTANCE.getRotationManager().submit(this, new RotationRequest(
                    finalRotation.rotation(), RotationPriority.VERY_HIGH, MovementFix.SILENT,
                    true, SmoothMode.ADVANCED, 180.0f, 0, 0));
            needHandleRotation = false;
        }
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

        if (delayState != DelayState.BUFFERING) {
            return;
        }

        if (bufferTicks >= 0) {
            bufferTicks++;
        }

        if (holdingSprintEntityData) {
            if (!handleVelocity) {
                releaseInbound();
                resetDelay();
            }
            return;
        }

        if (!shouldRelease()) {
            return;
        }

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
        if (mc.player == null || packet == null) return;

        if (packet instanceof ClientboundPlayerPositionPacket) {
            if (delayState == DelayState.BUFFERING) releaseInbound();
            resetDelay();
            serverPositions.clear();
            handleVelocity = false;
            ticksSinceVelocity = -1;
            needHandleRotation = false;
            return;
        }

        if (mode.is("Vanilla")) {
            applyVanilla(event);
            return;
        }

        if (event.getPacket() instanceof ClientboundSetEntityMotionPacket motion) {
            if (isValidVelocityPacket(motion)) {

                double velocityX = motion.getMovement().x / 8000.0;
                double velocityZ = motion.getMovement().z / 8000.0;
                Vec3 knockback = new Vec3(velocityX, 0.0, velocityZ).normalize();
                Vec3 opposite = mc.player.position().add(-knockback.x, 0.0, -knockback.z);
                finalRotation = getOppositeRotation(RotationUtils.toRotation(opposite, false));

                attackCount = computeReduceTicks(motion.getMovement().x, motion.getMovement().z);

                if (!legitModules.isSelected("Delay") || cantSprint()) {
                    handleVelocity = true;
                    ticksSinceVelocity = 0;
                    if (legitModules.isSelected("Jump Reset")) {
                        needHandleRotation = true;
                    }
                }
            }
        }
    }

    @EventTarget
    private void onPacketReceive(EventPacketReceive event) {
        if (event.getPacket() instanceof ClientboundPlayerPositionPacket) {
            if (delayState == DelayState.BUFFERING) {
                releaseInbound();
            }
            resetDelay();
        }

        if (!(event.getPacket() instanceof ClientboundSetEntityMotionPacket packet)) {
            return;
        }

        if (delayState != DelayState.IDLE || cantSprint() || !isValidVelocityPacket(packet)) {
            return;
        }

        bufferTicks = 0;
        delayState = DelayState.BUFFERING;
        holdingSprintEntityData = false;
        Core.INSTANCE.getPacketManager().freeze(PacketDirection.INBOUND);
    }


    @EventTarget(priority = Priority.Lowest)
    private void onMoveInput(EventMoveInput event) {
        if (mode.is("Legit") && legitModules.isSelected("Delay") && delayState == DelayState.BUFFERING) {
            event.setForward(1.0f);
        }
        if (mode.is("Legit") && legitModules.isSelected("Reduce") && handleVelocity) {
            event.setForward(1.0f);
        }


        if (ticksSinceVelocity >= 0) {
            if (ticksSinceVelocity < 4 && Math.abs(finalRotation.difference) <= 45F) {
                if (mc.player != null && GameStateManager.clientOnGround()) {
                    handleJumpReset = true;
                    event.setJump(true);
                }
                if (handleJumpReset) {
                    float[] movement = computeMovement(finalRotation.rotation());
                    if (movement[0] != 0.0f) event.setForward(movement[0]);
                    if (movement[1] != 0.0f) event.setStrafe(movement[1]);
                }
            }else if (handleJumpReset) handleJumpReset = false;
        }
    }

    @EventTarget
    private void onClick(EventClick event) {
        if (!mode.is("Legit") || !legitModules.isSelected("Reduce") || mc.gameMode == null || mc.player == null) return;
        if (handleVelocity) {
            if (getRayCastPlayer() != null && mc.player.isSprinting()) {
                ((MultiPlayerGameModeAccessor)mc.gameMode).nimbus$ensureHasSentCarriedItem();
                mc.getConnection().send(ServerboundInteractPacket.createAttackPacket(getRayCastPlayer(), mc.player.isShiftKeyDown()));
                if (mc.gameMode.getPlayerMode() != GameType.SPECTATOR) {
                    mc.player.setDeltaMovement(mc.player.getDeltaMovement().multiply(0.6, 1.0, 0.6));
                    mc.player.setSprinting(false);
                }
                mc.player.swing(InteractionHand.MAIN_HAND);
            }
        }
    }

    private void applyVanilla(EventPacketReceive event) {
        if (!(event.getPacket() instanceof ClientboundSetEntityMotionPacket packet)
                || mc.player == null || packet.getId() != mc.player.getId()) return;

        Vec3 movement = packet.getMovement();
        double horizontalScale = horizontal.getValue().doubleValue() / 100.0;
        double verticalScale = vertical.getValue().doubleValue() / 100.0;
        event.setPacket(new ClientboundSetEntityMotionPacket(
                packet.getId(), new Vec3(
                        movement.x * horizontalScale,
                        movement.y * verticalScale,
                        movement.z * horizontalScale)));
    }

    private Player getRayCastPlayer() {
        EntityHitResult hitResult = RotationRaycast.rayCastEntity(
                Core.INSTANCE.getRotationManager().getCurrentRotation(), attackRange.getValue().doubleValue(), false);

        if (!(hitResult != null && hitResult.getType() == HitResult.Type.ENTITY)
                && throughWallsRange.getValue().doubleValue() > 0) {
            hitResult = RotationRaycast.rayCastEntity(
                    Core.INSTANCE.getRotationManager().getCurrentRotation(), throughWallsRange.getValue().doubleValue(), false);
        }

        if (hitResult != null && hitResult.getType() == HitResult.Type.ENTITY
                && hitResult.getEntity() instanceof Player) {
            return (Player) hitResult.getEntity();
        }

        return null;
    }

    private boolean isValidVelocityPacket(ClientboundSetEntityMotionPacket packet) {
        if (mc.player == null || packet.getId() != mc.player.getId()) return false;
        Vec3 movement = packet.getMovement();
        return movement.y > 0.0
                && new Vec3(movement.x, 0.0, movement.z).length() >= 0.1
                && !mc.player.onClimbable()
                && !mc.player.isInWater()
                && !mc.player.isInLava();
    }

    private RotationData getOppositeRotation(Rotation rotation) {
        if (mc.player == null) return null;

        float targetYaw = rotation.yaw();
        Rotation oppositeRotation = new Rotation(targetYaw,Core.INSTANCE.getRotationManager().getCurrentRotation().pitch());
        return new RotationData(oppositeRotation,0);
    }


    private boolean cantSprint() {
        if (mc.player == null) return true;
        return mc.player.hasEffect(MobEffects.BLINDNESS)
                || mc.player.hasEffect(MobEffects.SLOWNESS)
                || mc.player.isShiftKeyDown()
                || mc.player.isCrouching()
                || (!mc.player.isPassenger()
                && mc.player.getFoodData().getFoodLevel() <= 6
                && !mc.player.getAbilities().mayfly);
    }

    private boolean shouldRelease() {
        int maxTicks = maxLimitedTick.getValue().intValue();
        boolean untilLanding = releaseTiming.isSelected("Until Landing");
        boolean untilSprint = releaseTiming.isSelected("Until Sprint");
        boolean onGround = mc.player != null && GameStateManager.clientOnGround();
        boolean sprinting = mc.player != null && mc.player.isSprinting();
        boolean timedOut = bufferTicks >= maxTicks;
        boolean cannotSprint = cantSprint();

        if (untilLanding && untilSprint) return (onGround && sprinting) || timedOut || cannotSprint;
        if (untilLanding) return onGround || timedOut || cannotSprint;
        if (untilSprint) return sprinting || timedOut || cannotSprint;
        return timedOut || cannotSprint;
    }

    private int computeReduceTicks(double motionX, double motionZ) {
        double knockback = Math.hypot(motionX, motionZ) * 8000.0;
        double value = 0.000408163 * knockback + 0.7142857;
        return Mth.clamp((int) Math.round(value), 1, 5);
    }

    private float[] computeMovement(Rotation target) {
        if (mc.player == null) return new float[]{0.0f, 0.0f};
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
        if (mc.player == null || !(packet instanceof ClientboundSetEntityDataPacket data)
                || data.id() != mc.player.getId()) return false;
        for (SynchedEntityData.DataValue<?> value : data.packedItems()) {
            if (value.id() == ENTITY_FLAGS_INDEX && value.value() instanceof Byte flags) {
                return (flags & (1 << SPRINTING_FLAG)) == 0;
            }
        }
        return false;
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
        serverPositions.clear();
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
