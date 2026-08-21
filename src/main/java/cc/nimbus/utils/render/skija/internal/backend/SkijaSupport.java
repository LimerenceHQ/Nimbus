package cc.nimbus.utils.render.skija.internal.backend;

import java.util.Locale;

public final class SkijaSupport {
    private static String platformUnsupportedReason;
    private static String backendUnsupportedReason;
    private static boolean backendValidated;

    private SkijaSupport() {
    }

    public static void initialize() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        platformUnsupportedReason = !os.contains("win") || !(arch.equals("amd64") || arch.equals("x86_64"))
                ? "Nimbus Skija requires Windows x64."
                : null;
        backendUnsupportedReason = null;
        backendValidated = false;
    }

    public static synchronized void validateBackend() {
        if (backendValidated) return;
        backendUnsupportedReason = null;
        backendValidated = true;
    }

    public static boolean isSupported() {
        return platformUnsupportedReason == null && backendUnsupportedReason == null;
    }

    public static String getUnsupportedReason() {
        return platformUnsupportedReason != null ? platformUnsupportedReason : backendUnsupportedReason;
    }

    public static synchronized void close() {
        backendValidated = false;
        backendUnsupportedReason = null;
    }
}
