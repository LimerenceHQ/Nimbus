package cc.nimbus.managers.rotation;

import cc.nimbus.utils.math.MathUtils;
import net.minecraft.util.Mth;

public enum SmoothMode {
    LINEAR {
        @Override
        public Rotation apply(Rotation current, Rotation target, float rotationSpeed) {
            float deltaYaw = Mth.wrapDegrees(target.yaw() - current.yaw());
            float deltaPitch = target.pitch() - current.pitch();
            float factor = Math.min(1.0f, rotationSpeed / 180f);
            float newYaw = current.yaw() + deltaYaw * factor;
            float newPitch = Mth.clamp(current.pitch() + deltaPitch * factor, -90f, 90f);
            return new Rotation(newYaw, newPitch);
        }
    },
    ADVANCED {
        @Override
        public Rotation apply(Rotation current, Rotation target, float rotationSpeed) {
            int yawSpeed = (int) rotationSpeed;
            int pitchSpeed = (int) (rotationSpeed / 2f);
            if (yawSpeed <= 0 || pitchSpeed <= 0) {
                return target;
            }

            float deltaYaw = Mth.wrapDegrees(target.yaw() - current.yaw());
            float deltaPitch = target.pitch() - current.pitch();

            if (deltaYaw != 0) {
                float absYaw = Math.abs(deltaYaw);
                deltaYaw *= Math.min(absYaw, yawSpeed) / absYaw;
            }

            if (deltaPitch != 0) {
                float absPitch = Math.abs(deltaPitch);
                deltaPitch *= Math.min(absPitch, pitchSpeed) / absPitch;
            }

            return new Rotation(
                    current.yaw() + deltaYaw,
                    MathUtils.clamp(current.pitch() + deltaPitch, -90, 90)
            );
        }
    };

    public abstract Rotation apply(Rotation current, Rotation target, float rotationSpeed);
}
