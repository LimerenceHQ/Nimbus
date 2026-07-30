package cc.nimbus.config;

public final class ChangeTracker {
    private static volatile Runnable listener = () -> {};
    private ChangeTracker() {}
    public static void setListener(Runnable listener) { ChangeTracker.listener = listener == null ? () -> {} : listener; }
    public static void changed() { listener.run(); }
}