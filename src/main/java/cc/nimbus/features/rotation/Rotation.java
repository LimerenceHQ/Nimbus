package cc.nimbus.features.rotation;

import net.minecraft.util.Mth;

public class Rotation {
    private float yaw;
    private final float pitch;
    public static final Rotation ZERO = new Rotation(0f, 0f);

    public Rotation(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
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
        return new Rotation(Mth.wrapDegrees(yaw), Mth.clamp(pitch, -90.0f, 90.0f));
    }

    public Rotation clamped() {
        return new Rotation(yaw, Mth.clamp(pitch, -90.0f, 90.0f));
    }

    public float yawDelta(Rotation other) {
        return Mth.wrapDegrees(other.yaw - yaw);
    }

    public float pitchDelta(Rotation other) {
        return other.pitch - pitch;
    }

    public Rotation setPitch(float pitch) {
        return new Rotation(yaw, pitch);
    }

    public double distanceTo(Rotation other) {
        float yawDelta = yawDelta(other);
        float pitchDelta = pitchDelta(other);
        return Math.hypot(yawDelta, pitchDelta);
    }
}
