package cc.nimbus.utils.render.skija.post;

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
        KAWASE;

        public static BlurMode fromName(String name) {
            if (name == null) return OFF;
            return switch (name) {
                case "Gaussian" -> GAUSSIAN;
                case "Kawase" -> KAWASE;
                default -> OFF;
            };
        }
    }
}
