package cc.nimbus.utils.render.skija.api;

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

    public boolean blurEnabled() {
        return enabled && blurMode != BlurMode.OFF;
    }

    public enum BlurMode {
        OFF,
        GAUSSIAN,
        KAWASE
    }
}
