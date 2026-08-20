package cc.nimbus.managers.rotation;

public interface IPriority {
    int getLevel();

    default boolean isLowerThan(IPriority other) {
        return getLevel() < other.getLevel();
    }
}
