package cc.nimbus.features.hud;

public record HudRect(float x, float y, float width, float height) {
    public HudRect {
        if (!Float.isFinite(x) || !Float.isFinite(y)
                || !Float.isFinite(width) || !Float.isFinite(height)
                || width < 0f || height < 0f) {
            throw new IllegalArgumentException("HUD rectangle must be finite and non-negative");
        }
    }

    public float right() {
        return x + width;
    }

    public float bottom() {
        return y + height;
    }

    public float centerX() {
        return x + width * 0.5f;
    }

    public float centerY() {
        return y + height * 0.5f;
    }

    public float area() {
        return width * height;
    }

    public boolean contains(double pointX, double pointY) {
        return pointX >= x && pointX <= right() && pointY >= y && pointY <= bottom();
    }
}
