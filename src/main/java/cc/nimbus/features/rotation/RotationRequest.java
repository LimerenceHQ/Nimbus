package cc.nimbus.features.rotation;

import java.util.Objects;

public record RotationRequest(
        Rotation rotation,
        RotationPriority priority,
        MovementFix movementFix,
        boolean smooth,
        SmoothMode smoothMode,
        float speed,
        int keepTicks,
        int returnTicks
) {
    public RotationRequest {
        rotation = Objects.requireNonNull(rotation, "rotation").normalized();
        priority = Objects.requireNonNull(priority, "priority");
        movementFix = Objects.requireNonNull(movementFix, "movementFix");
        smoothMode = Objects.requireNonNull(smoothMode, "smoothMode");
        speed = Math.max(0.0f, speed);
        keepTicks = Math.max(0, keepTicks);
        returnTicks = Math.max(0, returnTicks);
    }

    public static RotationRequest immediate(Rotation rotation, MovementFix movementFix) {
        return new RotationRequest(rotation, RotationPriority.MEDIUM, movementFix,
                false, SmoothMode.LINEAR, 180.0f, 0, 2);
    }
}
