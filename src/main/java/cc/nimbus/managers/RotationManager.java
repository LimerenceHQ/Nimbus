package cc.nimbus.managers;

import cc.nimbus.core.Core;
import cc.nimbus.core.interfaces.InstanceManager;
import cc.nimbus.event.EventBus;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.game.EventTick;
import cc.nimbus.event.impl.network.PacketEvent;
import cc.nimbus.event.impl.player.*;
import cc.nimbus.event.impl.render.EventRotationRender;
import cc.nimbus.features.rotation.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;

import java.util.*;

public class RotationManager implements InstanceManager {

    private final Minecraft mc;
    private Rotation serverRotation = Rotation.ZERO;
    private final List<Request> pendings = new ArrayList<>();
    private final Set<Class<?>> loggedMovePacketTypes = new HashSet<>();
    public Request request;
    private Rotation cachedTarget;
    private boolean dirty = true;

    private MovementFix strictMovementFix;

    public RotationManager(Minecraft minecraft) {
        this.mc = Objects.requireNonNull(minecraft, "minecraft");
    }

    @Override
    public synchronized void init() {
        reset();
        EventBus.register(this);
    }

    @Override
    public synchronized void shutdown() {
        EventBus.unregister(this);
        reset();
    }

    public void setRotations(Rotation rotation, MovementFix movementFix, boolean smooth, SmoothMode smoothMode, float rotationSpeed, int keepLength, int revTicks, Priority priority) {
        enqueue(new Request(rotation, priority, movementFix, smooth, smoothMode, rotationSpeed, keepLength, revTicks));
    }

    public void setRotations(Rotation rotation) {
        setRotations(rotation, MovementFix.NONE);
    }

    public void setRotations(Rotation rotation, MovementFix movementFix) {
        setRotations(rotation, movementFix, false, SmoothMode.LINEAR, 180.0f, 1, 0, Priority.MEDIUM);
    }

    public void setRotations(Rotation rotation, int keepLength, MovementFix movementFix) {
        setRotations(rotation, movementFix, true, SmoothMode.ADVANCED, 180.0f, keepLength, 0, Priority.MEDIUM);
    }

    public void setRotations(Rotation rotation, int keepLength, MovementFix movementFix, Priority priority) {
        setRotations(rotation, movementFix, false, SmoothMode.ADVANCED, 180.0f, keepLength, 0, priority);
    }

    public void setRotations(Rotation rotation, int keepLength, MovementFix movementFix, float rotationSpeed) {
        setRotations(rotation, movementFix, false, SmoothMode.ADVANCED, rotationSpeed, keepLength, 0, Priority.MEDIUM);
    }

    public void enqueue(Request request) {
        pendings.add(request);
        dirty = true;
    }

    public void reset() {
        if (mc.player == null) {
            request = null;
            cachedTarget = null;
            return;
        }

        if (Math.abs(Mth.wrapDegrees(serverRotation.yaw() - mc.player.getYRot())) < 1
                && Math.abs(serverRotation.pitch() - mc.player.getXRot()) < 1) {
            mc.player.setYRot(serverRotation.yaw() + Mth.wrapDegrees(mc.player.getYRot() - serverRotation.yaw()));
            cachedTarget = null;
            request = null;
            return;
        }

        request = new Request(
                new Rotation(mc.player.getYRot(), mc.player.getXRot()).normalized(),
                Priority.of(Integer.MIN_VALUE),
                MovementFix.SILENT,
                false,
                SmoothMode.LINEAR,
                180f,
                0,
                0
        );
        cachedTarget = request.getRotation();
    }

    public Rotation getTargetRotation() {
        if (!dirty) {
            return cachedTarget;
        }
        if (pendings.isEmpty()) {
            dirty = false;
            return cachedTarget;
        }

        Request winner = resolve(pendings);
        if (request != null && !request.isExpired() && winner.getPriority().isLowerThan(request.getPriority())) {
            dirty = false;
            return cachedTarget;
        }

        cachedTarget = winner.isSmooth()
                ? winner.getSmoothMode().apply(serverRotation, winner.getRotation(), winner.getRotationSpeed())
                : winner.getRotation();
        dirty = false;
        return cachedTarget;
    }

    private MovementFix getActiveMovementFix() {
        if (request == null) {
            return MovementFix.SILENT;
        }
        return strictMovementFix != null ? strictMovementFix : request.getMovementFix();
    }

    @EventTarget(priority = cc.nimbus.event.component.Priority.Highest)
    public void onTick(EventTick event) {
        if (request != null && event.isPre()) {
            request.decrement();
            if (request.isExpired()) {
                reset();
                dirty = true;
            }
        }
    }

    @EventTarget(priority = cc.nimbus.event.component.Priority.Lowest)
    public void onUpdate(EventUpdate event) {
        if (event.isPost()) return;

        applyPendings();
        GameStateManager.eyePos = mc.player.getEyePosition();;
    }

    @EventTarget(priority = cc.nimbus.event.component.Priority.Lowest)
    public void onMotion(EventPreUpdate event) {
        lastAnimationRotation = animationRotation;

        applyPendings();
    }

    private void applyPendings() {
        if (pendings.isEmpty()) {
            return;
        }

        Request winner = resolve(pendings);
        if (request == null || request.isExpired() || !winner.getPriority().isLowerThan(request.getPriority())) {
            request = winner;
            pendings.clear();
            apply(winner);
            dirty = true;
            return;
        }
        pendings.clear();
        strictMovementFix = null;
    }

    @EventTarget(priority = cc.nimbus.event.component.Priority.Low)
    public void onMovementInput(EventMoveInput event) {
        Rotation target = getTargetRotation();
        if (target == null || mc.player == null || getActiveMovementFix() != MovementFix.SILENT) {
            return;
        }

        float forward = event.getForward();
        float strafe = event.getStrafe();
        if (forward == 0.0F && strafe == 0.0F) {
            return;
        }

        float factor = Math.max(Math.abs(forward), Math.abs(strafe));
        double realYaw = mc.player.getYRot() - Math.toDegrees(Math.atan2(strafe, forward));
        double diff = Mth.wrapDegrees(realYaw - target.yaw());
        int section = (int) Math.round(diff / 45.0);
        double ang = Math.toRadians(section * 45.0);
        event.setForward((float) Math.round(Math.cos(ang)) * factor);
        event.setStrafe((float) Math.round(-Math.sin(ang)) * factor);
    }

    @EventTarget(priority = cc.nimbus.event.component.Priority.Lowest)
    public void onStrafe(EventStrafe event) {
        Rotation target = getTargetRotation();
        if (target != null && getActiveMovementFix() != MovementFix.NONE) {
            event.setYaw(target.yaw());
        }
    }

    @EventTarget(priority = cc.nimbus.event.component.Priority.Lowest)
    public void onLook(EventRayTrace event) {
        Rotation target = getTargetRotation();
        if (target != null && getActiveMovementFix() != MovementFix.NONE) {
            event.setYaw(target.yaw());
            event.setPitch(target.pitch());
        }
    }

    @EventTarget(priority = cc.nimbus.event.component.Priority.Lowest)
    public void onJump(EventJump event) {
        Rotation target = getTargetRotation();
        if (target != null && getActiveMovementFix() != MovementFix.NONE) {
            event.setYaw(target.yaw());
        }
    }

    @EventTarget(priority = cc.nimbus.event.component.Priority.Lowest)
    public void onPacket(PacketEvent event) {
        Packet<?> packet = event.getPacket();
        if (!(packet instanceof ServerboundMovePlayerPacket movePacket)) {
            return;
        }
        if (movePacket.hasRotation()) {
            animationRotation = serverRotation;
            serverRotation = new Rotation(movePacket.getYRot(0), movePacket.getXRot(0));
        }
    }

    @EventTarget(priority = cc.nimbus.event.component.Priority.Low)
    public void onMotionEvent(EventMotion event) {
        Rotation target = getTargetRotation();
        if (target != null) {
            animationRotation = target;
            event.setYaw(target.yaw());
            event.setPitch(target.pitch());
        }
    }

    @EventTarget
    public void onRenderRotation(EventRotationRender e) {
        if (animationRotation != null && lastAnimationRotation != null) {
            e.setYaw(animationRotation.yaw());
            e.setLastYaw(lastAnimationRotation.yaw());
            e.setPitch(animationRotation.pitch());
            e.setLastPitch(lastAnimationRotation.pitch());
        }
    }


    public static Rotation animationRotation;
    public static Rotation lastAnimationRotation;

    private void apply(Request request) {
        cachedTarget = request.isSmooth()
                ? request.getSmoothMode().apply(serverRotation, request.getRotation(), request.getRotationSpeed()).normalized()
                : request.getRotation().normalized();
    }

    private Request resolve(List<Request> requests) {
        Request best = null;
        for (Request request : requests) {
            if (best == null || !request.getPriority().isLowerThan(best.getPriority())) {
                best = request;
            }
        }
        return best;
    }

    public Rotation getEffectiveRotation() {
        if (mc.player == null) return new Rotation(0,0);

        return Core.INSTANCE.getRotationManager().getTargetRotation() != null ? Core.INSTANCE.getRotationManager().getTargetRotation() : new Rotation(mc.player.getYRot(),mc.player.getXRot());
    }

    public Rotation getServerRotation() {
        return serverRotation;
    }
}
