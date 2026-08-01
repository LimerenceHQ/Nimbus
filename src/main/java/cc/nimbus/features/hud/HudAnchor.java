package cc.nimbus.features.hud;

public enum HudAnchor {
    TOP_LEFT(false, false),
    TOP_RIGHT(true, false),
    BOTTOM_LEFT(false, true),
    BOTTOM_RIGHT(true, true);

    private final boolean right;
    private final boolean bottom;

    HudAnchor(boolean right, boolean bottom) {
        this.right = right;
        this.bottom = bottom;
    }

    public boolean isRight() {
        return right;
    }

    public boolean isBottom() {
        return bottom;
    }

    public static HudAnchor from(double x, double y) {
        if (y >= 0.5) return x >= 0.5 ? BOTTOM_RIGHT : BOTTOM_LEFT;
        return x >= 0.5 ? TOP_RIGHT : TOP_LEFT;
    }
}
