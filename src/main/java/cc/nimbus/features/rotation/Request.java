package cc.nimbus.features.rotation;


public class Request {
    private Rotation rotation;
    private final IPriority priority;
    private final MovementFix movementFix;
    private final boolean smooth;
    private final float rotationSpeed;
    private final int keepLength;
    private final SmoothMode smoothMode;
    private final int revTicks;
    private int ticksRemaining;

    public Request(Rotation rotation, IPriority priority, MovementFix movementFix, boolean smooth, SmoothMode smoothMode, float rotationSpeed, int keepLength, int revTicks) {
        this.rotation = rotation;
        this.priority = priority;
        this.movementFix = movementFix;
        this.smooth = smooth;
        this.rotationSpeed = rotationSpeed;
        this.keepLength = keepLength;
        this.smoothMode = smoothMode;
        this.revTicks = revTicks;
        this.ticksRemaining = keepLength;
    }

    public void decrement() {
        ticksRemaining--;
    }

    public boolean isExpired() {
        return ticksRemaining < 0;
    }

    public SmoothMode getSmoothMode() {
        return smoothMode;
    }

    public int getRevTicks() {
        return revTicks;
    }

    public int getKeepLength() {
        return keepLength;
    }

    public float getRotationSpeed() {
        return rotationSpeed;
    }

    public boolean isSmooth() {
        return smooth;
    }

    public MovementFix getMovementFix() {
        return movementFix;
    }

    public IPriority getPriority() {
        return priority;
    }

    public Rotation getRotation() {
        return rotation;
    }
}
