package cc.nimbus.managers;

import cc.nimbus.core.interfaces.InstanceManager;
import cc.nimbus.event.EventBus;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.component.Priority;
import cc.nimbus.event.impl.game.EventTick;
import cc.nimbus.event.impl.game.EventWorldLoad;
import cc.nimbus.event.impl.player.EventJump;
import cc.nimbus.event.impl.player.EventMotion;
import cc.nimbus.event.impl.player.EventMoveInput;
import cc.nimbus.event.impl.player.EventRayTrace;
import cc.nimbus.event.impl.player.EventRotationApplied;
import cc.nimbus.event.impl.player.EventStrafe;
import cc.nimbus.features.rotation.MovementFix;
import cc.nimbus.features.rotation.Rotation;
import cc.nimbus.features.rotation.RotationMath;
import cc.nimbus.features.rotation.RotationRequest;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class RotationManager implements InstanceManager {
    private final Minecraft minecraft;
    private final List<PendingRequest> pending = new ArrayList<>();
    private ActiveRequest active;
    private Rotation currentRotation;
    private Rotation targetRotation;
    private Rotation lastRotation;
    private Rotation animationRotation;
    private Rotation lastAnimationRotation;
    private Rotation serverRotation = new Rotation(0.0f, 0.0f);
    private Level level;
    private long sequence;
    private boolean restoring;
    private int restoreTicks = 2;
    private MovementFix restoreMovementFix = MovementFix.NONE;

    public RotationManager(Minecraft minecraft) {
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
    }

    @Override
    public synchronized void init() {
        clearState();
        EventBus.register(this);
    }

    @Override
    public synchronized void shutdown() {
        EventBus.unregister(this);
        clearState();
    }

    public synchronized void submit(Object owner, RotationRequest request) {
        pending.add(new PendingRequest(Objects.requireNonNull(owner, "owner"),
                Objects.requireNonNull(request, "request"), ++sequence));
    }

    public synchronized void cancel(Object owner) {
        if (owner == null) return;
        pending.removeIf(request -> request.owner() == owner);
        if (active != null && active.owner() == owner) beginRestore();
    }

    public synchronized void clearRequests() {
        pending.clear();
        beginRestore();
    }

    public synchronized boolean isActive() {
        return currentRotation != null;
    }

    public synchronized MovementFix getMovementFix() {
        return active == null ? (restoring ? restoreMovementFix : MovementFix.NONE) : active.request().movementFix();
    }

    public synchronized Rotation getEffectiveRotation() {
        if (currentRotation != null) return currentRotation;
        if (minecraft.player != null) return new Rotation(minecraft.player.getYRot(), minecraft.player.getXRot());
        return serverRotation;
    }

    public synchronized Rotation getCurrentRotation() {
        return currentRotation;
    }

    public synchronized Rotation getTargetRotation() {
        return targetRotation;
    }

    public synchronized Rotation getLastRotation() {
        return lastRotation;
    }

    public synchronized Rotation getServerRotation() {
        return serverRotation;
    }

    public synchronized Rotation getAnimationRotation(float partialTick) {
        if (animationRotation == null) return null;
        Rotation previous = lastAnimationRotation == null ? animationRotation : lastAnimationRotation;
        return new Rotation(
                previous.yaw() + Mth.wrapDegrees(animationRotation.yaw() - previous.yaw()) * partialTick,
                Mth.lerp(partialTick, previous.pitch(), animationRotation.pitch())
        );
    }

    public void withEffectiveRotation(Player player, Runnable action) {
        Rotation rotation = getEffectiveRotation();
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        player.setYRot(rotation.yaw());
        player.setXRot(rotation.pitch());
        try {
            action.run();
        } finally {
            player.setYRot(yaw);
            player.setXRot(pitch);
        }
    }

    @EventTarget(priority = Priority.Lowest)
    private synchronized void onTick(EventTick event) {
        if (event.getPhase() != EventTick.Phase.PRE) return;
        if (minecraft.level == null || minecraft.player == null || !minecraft.player.isAlive()) {
            clearState();
            return;
        }
        if (level != minecraft.level) {
            clearState();
            level = minecraft.level;
            Rotation playerRotation = new Rotation(minecraft.player.getYRot(), minecraft.player.getXRot()).clamped();
            serverRotation = playerRotation;
        }

        PendingRequest winner = resolvePending();
        if (winner != null && (active == null
                || winner.request().priority().level() >= active.request().priority().level())) {
            active = new ActiveRequest(winner.owner(), winner.request(), winner.request().keepTicks());
            restoring = false;
        } else if (active != null) {
            active = active.decremented();
            if (active.ticksRemaining() < 0) beginRestore();
        }

        Rotation real = new Rotation(minecraft.player.getYRot(), minecraft.player.getXRot()).clamped();
        if (currentRotation == null) currentRotation = serverRotation;
        lastRotation = currentRotation;
        lastAnimationRotation = animationRotation == null ? currentRotation : animationRotation;

        if (active != null) {
            targetRotation = active.request().rotation();
            currentRotation = apply(currentRotation, targetRotation, active.request());
        } else if (restoring) {
            targetRotation = real;
            float speed = restoreSpeed(currentRotation, real);
            currentRotation = RotationMath.sensitivityPatch(
                    SmoothModeForRestore.apply(currentRotation, real, speed), currentRotation);
            if (currentRotation.distanceTo(real) < 0.75) {
                currentRotation = null;
                targetRotation = null;
                lastRotation = null;
                restoring = false;
            }
        } else {
            currentRotation = null;
            targetRotation = null;
            lastRotation = null;
        }
        animationRotation = currentRotation == null ? real : currentRotation;
    }

    @EventTarget(priority = Priority.Highest)
    private synchronized void onMotion(EventMotion event) {
        if (!event.isPre() || currentRotation == null) return;
        event.setRotation(currentRotation.yaw(), currentRotation.pitch());
    }

    @EventTarget(priority = Priority.Low)
    private synchronized void onMoveInput(EventMoveInput event) {
        if (currentRotation == null || getMovementFix() != MovementFix.SILENT || minecraft.player == null) return;
        float forward = event.getForward();
        float strafe = event.getStrafe();
        if (forward == 0.0f && strafe == 0.0f) return;
        float factor = Math.max(Math.abs(forward), Math.abs(strafe));
        double realYaw = minecraft.player.getYRot() - Math.toDegrees(Math.atan2(strafe, forward));
        int section = (int) Math.round(Mth.wrapDegrees(realYaw - currentRotation.yaw()) / 45.0);
        double angle = Math.toRadians(section * 45.0);
        event.setForward((float) Math.round(Math.cos(angle)) * factor);
        event.setStrafe((float) Math.round(-Math.sin(angle)) * factor);
    }

    @EventTarget(priority = Priority.Lowest)
    private synchronized void onStrafe(EventStrafe event) {
        if (currentRotation != null && getMovementFix() != MovementFix.NONE) event.setYaw(currentRotation.yaw());
    }

    @EventTarget(priority = Priority.Lowest)
    private synchronized void onJump(EventJump event) {
        if (currentRotation != null && getMovementFix() != MovementFix.NONE) event.setYaw(currentRotation.yaw());
    }

    @EventTarget(priority = Priority.Lowest)
    private synchronized void onLook(EventRotationApplied event) {
        if (currentRotation != null && getMovementFix() != MovementFix.NONE) {
            event.setYaw(currentRotation.yaw());
            event.setPitch(currentRotation.pitch());
        }
    }

    @EventTarget(priority = Priority.Lowest)
    private synchronized void onRayTrace(EventRayTrace event) {
        if (currentRotation != null) {
            event.setYaw(currentRotation.yaw());
            event.setPitch(currentRotation.pitch());
        }
    }

    public synchronized void recordSentPacket(net.minecraft.network.protocol.Packet<?> sentPacket) {
        if (!(sentPacket instanceof ServerboundMovePlayerPacket packet) || !packet.hasRotation()) return;
        serverRotation = new Rotation(packet.getYRot(serverRotation.yaw()), packet.getXRot(serverRotation.pitch())).clamped();
    }

    @EventTarget
    private synchronized void onWorldLoad(EventWorldLoad event) {
        clearState();
    }

    private Rotation apply(Rotation current, Rotation target, RotationRequest request) {
        Rotation next = request.smooth()
                ? request.smoothMode().apply(current, target, request.speed())
                : target;
        return RotationMath.sensitivityPatch(next.clamped(), current);
    }

    private float restoreSpeed(Rotation current, Rotation target) {
        return (float) Math.max(1.0, current.distanceTo(target) / Math.max(1, restoreTicks));
    }

    private PendingRequest resolvePending() {
        PendingRequest best = null;
        for (PendingRequest request : pending) {
            if (best == null
                    || request.request().priority().level() > best.request().priority().level()
                    || request.request().priority() == best.request().priority() && request.sequence() > best.sequence()) {
                best = request;
            }
        }
        pending.clear();
        return best;
    }

    private void beginRestore() {
        pending.clear();
        if (active != null) {
            restoreTicks = active.request().returnTicks();
            restoreMovementFix = active.request().movementFix();
        }
        if (currentRotation != null) restoring = true;
        active = null;
    }

    private void clearState() {
        pending.clear();
        active = null;
        currentRotation = null;
        targetRotation = null;
        lastRotation = null;
        animationRotation = null;
        lastAnimationRotation = null;
        serverRotation = new Rotation(0.0f, 0.0f);
        restoring = false;
        restoreTicks = 2;
        restoreMovementFix = MovementFix.NONE;
        level = null;
    }

    private record PendingRequest(Object owner, RotationRequest request, long sequence) {
    }

    private record ActiveRequest(Object owner, RotationRequest request, int ticksRemaining) {
        private ActiveRequest decremented() {
            return new ActiveRequest(owner, request, ticksRemaining - 1);
        }
    }

    private static final class SmoothModeForRestore {
        private static Rotation apply(Rotation current, Rotation target, float speed) {
            return cc.nimbus.features.rotation.SmoothMode.ADVANCED.apply(current, target, speed);
        }
    }
}
