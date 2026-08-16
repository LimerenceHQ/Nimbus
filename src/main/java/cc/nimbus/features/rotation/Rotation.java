package cc.nimbus.features.rotation;

import net.minecraft.util.Mth;

public record Rotation(float yaw, float pitch) {
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

    public double distanceTo(Rotation other) {
        float yawDelta = yawDelta(other);
        float pitchDelta = pitchDelta(other);
        return Math.hypot(yawDelta, pitchDelta);
    }
}
