package cc.nimbus.features.rotation;

public enum RotationPriority {
    VERY_LOW(0),
    LOW(1),
    MEDIUM(2),
    HIGH(3),
    VERY_HIGH(4);

    private final int level;

    RotationPriority(int level) {
        this.level = level;
    }

    public int level() {
        return level;
    }
}
