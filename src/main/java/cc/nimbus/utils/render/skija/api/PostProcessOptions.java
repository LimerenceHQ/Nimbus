package cc.nimbus.utils.render.skija.api;

import java.util.Objects;

public record PostProcessOptions(
        boolean enabled,
        boolean splitRegions,
        BlurMode blurMode,
        float blurRadius,
        int blurPasses,
        float downSample,
        float upSample,
        int blurRefreshRate,
        boolean shadow,
        float shadowRadius,
        int shadowOpacity,
        int shadowRefreshRate,
        boolean texture,
        int textureOpacity
) {
    public static final PostProcessOptions DISABLED = new PostProcessOptions(
            false, false, BlurMode.OFF, 0, 1, 1, 1, 60,
            false, 0, 0, 30, false, 0
    );

    public PostProcessOptions {
        blurMode = Objects.requireNonNull(blurMode, "blurMode");
        requireNonNegative("blurRadius", blurRadius);
        requirePositive("blurPasses", blurPasses);
        requirePositive("downSample", downSample);
        requirePositive("upSample", upSample);
        requirePositive("blurRefreshRate", blurRefreshRate);
        requireNonNegative("shadowRadius", shadowRadius);
        requireOpacity("shadowOpacity", shadowOpacity);
        requirePositive("shadowRefreshRate", shadowRefreshRate);
        requireOpacity("textureOpacity", textureOpacity);
    }

    public boolean blurEnabled() {
        return enabled && blurMode != BlurMode.OFF;
    }

    public enum BlurMode {
        OFF,
        GAUSSIAN,
        KAWASE
    }

    private static void requireNonNegative(String name, float value) {
        if (!Float.isFinite(value) || value < 0f) {
            throw new IllegalArgumentException(name + " must be finite and non-negative: " + value);
        }
    }

    private static void requirePositive(String name, float value) {
        if (!Float.isFinite(value) || value <= 0f) {
            throw new IllegalArgumentException(name + " must be finite and positive: " + value);
        }
    }

    private static void requirePositive(String name, int value) {
        if (value <= 0) throw new IllegalArgumentException(name + " must be positive: " + value);
    }

    private static void requireOpacity(String name, int value) {
        if (value < 0 || value > 255) {
            throw new IllegalArgumentException(name + " must be between 0 and 255: " + value);
        }
    }
}
