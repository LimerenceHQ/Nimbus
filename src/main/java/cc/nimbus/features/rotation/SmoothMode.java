package cc.nimbus.features.rotation;

import net.minecraft.util.Mth;

public enum SmoothMode {
    LINEAR {
        @Override
        public Rotation apply(Rotation current, Rotation target, float speed) {
            float factor = Math.min(1.0f, Math.max(0.0f, speed) / 180.0f);
            return new Rotation(
                    current.yaw() + Mth.wrapDegrees(target.yaw() - current.yaw()) * factor,
                    Mth.clamp(current.pitch() + (target.pitch() - current.pitch()) * factor, -90.0f, 90.0f)
            );
        }
    },
    ADVANCED {
        @Override
        public Rotation apply(Rotation current, Rotation target, float speed) {
            float yawSpeed = Math.max(0.0f, speed);
            float pitchSpeed = yawSpeed * 0.5f;
            float yawDelta = Mth.wrapDegrees(target.yaw() - current.yaw());
            float pitchDelta = target.pitch() - current.pitch();
            return new Rotation(
                    current.yaw() + Mth.clamp(yawDelta, -yawSpeed, yawSpeed),
                    Mth.clamp(current.pitch() + Mth.clamp(pitchDelta, -pitchSpeed, pitchSpeed), -90.0f, 90.0f)
            );
        }
    };

    public abstract Rotation apply(Rotation current, Rotation target, float speed);
}
