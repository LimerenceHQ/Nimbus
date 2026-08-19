package cc.nimbus.network;

public final class PacketEventGuard {
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private PacketEventGuard() {
    }

    public static boolean isSuppressed() {
        return DEPTH.get() > 0;
    }

    public static void suppress() {
        DEPTH.set(DEPTH.get() + 1);
    }

    public static void restore() {
        int depth = DEPTH.get() - 1;
        if (depth <= 0) DEPTH.remove();
        else DEPTH.set(depth);
    }
}
