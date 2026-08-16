package cc.nimbus.utils.render.skija.internal.backend;

import com.mojang.blaze3d.systems.RenderSystem;

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
        try {
            String backendName = RenderSystem.getDevice().getBackendName();
            backendUnsupportedReason = backendName == null || !backendName.toLowerCase(Locale.ROOT).contains("opengl")
                    ? "Nimbus Skija requires the OpenGL backend; detected: " + backendName
                    : null;
        } catch (RuntimeException error) {
            backendUnsupportedReason = "Nimbus could not inspect the active graphics backend: " + error.getMessage();
        } finally {
            backendValidated = true;
        }
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
