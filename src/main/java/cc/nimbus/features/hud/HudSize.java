package cc.nimbus.features.hud;

public record HudSize(float width, float height) {
    public HudSize {
        if (!Float.isFinite(width) || !Float.isFinite(height) || width < 0f || height < 0f) {
            throw new IllegalArgumentException("HUD size must be finite and non-negative");
        }
    }
}
