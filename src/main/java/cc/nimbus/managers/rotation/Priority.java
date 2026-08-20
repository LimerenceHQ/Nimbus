package cc.nimbus.managers.rotation;

public enum Priority implements IPriority {
    VERY_LOW(0),
    LOW(1),
    MEDIUM(2),
    HIGH(3),
    VERY_HIGH(4);

    private final int level;

    Priority(int level) {
        this.level = level;
    }

    @Override
    public int getLevel() {
        return level;
    }

    public static IPriority of(int custom) {
        return () -> custom;
    }
}
