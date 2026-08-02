package cc.nimbus.ui.clickgui.component;

public record UiRect(float x, float y, float width, float height) {
    public boolean contains(float px, float py) {
        return px >= x && px <= x + width && py >= y && py <= y + height;
    }

    public boolean intersects(UiRect other) {
        return other != null && x < other.x + other.width && x + width > other.x
                && y < other.y + other.height && y + height > other.y;
    }
}
