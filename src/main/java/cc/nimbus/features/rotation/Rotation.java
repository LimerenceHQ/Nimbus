package cc.nimbus.features.rotation;

import cc.nimbus.core.Core;
import cc.nimbus.utils.client.IMinecraft;
import net.minecraft.util.Mth;

public class Rotation implements IMinecraft {
    private float yaw;
    private final float pitch;
    public static final Rotation ZERO = new Rotation(0f, 0f);
    private boolean normalized;

    public Rotation(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public Rotation(float yaw, float pitch, boolean normalized) {
        this.yaw = yaw;
        this.pitch = pitch;
        this.normalized = normalized;
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public Rotation normalized() {
        if (normalized || mc.player == null) {
            return this;
        }

        double sensitivity = mc.options.sensitivity().get() * 0.6 + 0.2;
        double gcd = sensitivity * sensitivity * sensitivity * 8.0 * 0.15;

        Rotation currentRotation = Core.INSTANCE.getRotationManager().getTargetRotation() != null
                ? Core.INSTANCE.getRotationManager().getTargetRotation()
                : new Rotation(mc.player.getYRot(), mc.player.getXRot());

        RotationDelta diff = currentRotation.distanceTo(this);
        float snappedYaw = (float) (Math.round(diff.deltaYaw() / gcd) * gcd);
        float snappedPitch = (float) (Math.round(diff.deltaPitch() / gcd) * gcd);

        float newYaw = currentRotation.yaw + snappedYaw;
        float newPitch = Math.max(-90f, Math.min(90f, currentRotation.pitch + snappedPitch));
        return new Rotation(newYaw, newPitch, true);
    }

    public Rotation clamped() {
        return new Rotation(yaw, Mth.clamp(pitch, -90.0f, 90.0f));
    }

    public Rotation setPitch(float pitch) {
        return new Rotation(yaw, pitch);
    }


    public RotationDelta distanceTo(Rotation other) {
        return new RotationDelta(
                Mth.wrapDegrees(other.yaw - this.yaw),
                Mth.wrapDegrees(other.pitch - this.pitch)
        );
    }

    public record RotationDelta(float deltaYaw, float deltaPitch) {
        public float length() {
            return (float) Math.hypot(deltaYaw, deltaPitch);
        }
    }
}
