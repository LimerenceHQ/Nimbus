package cc.nimbus.features.hud;

public record HudViewport(float width, float height, float scale) {
    private static final float MIN_LOGICAL_WIDTH = 1000f;
    private static final float MIN_LOGICAL_HEIGHT = 620f;

    public HudViewport {
        if (!Float.isFinite(width) || !Float.isFinite(height) || width <= 0f || height <= 0f) {
            throw new IllegalArgumentException("HUD viewport dimensions must be finite and positive");
        }
        if (!Float.isFinite(scale) || scale <= 0f) {
            throw new IllegalArgumentException("HUD viewport scale must be finite and positive");
        }
    }

    public static HudViewport fit(float width, float height, float userScale) {
        float adaptive = Math.min(1f, Math.min(width / MIN_LOGICAL_WIDTH, height / MIN_LOGICAL_HEIGHT));
        return new HudViewport(width, height, adaptive * userScale);
    }
}
