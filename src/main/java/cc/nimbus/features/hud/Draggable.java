package cc.nimbus.features.hud;

import cc.nimbus.utils.render.skija.api.SkijaFrame;

import java.util.Objects;
import java.util.function.Consumer;

public abstract class Draggable {
    public static final double MIN_SCALE = 0.5;
    public static final double MAX_SCALE = 3.0;
    public static final double SCALE_STEP = 0.1;

    private final String name;
    private double x;
    private double y;
    private double scale = 1.0;
    private Runnable changeListener = () -> {};

    public Draggable(String name, double defaultX, double defaultY) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Draggable name cannot be blank");
        requireFinite(defaultX, "x");
        requireFinite(defaultY, "y");
        this.name = name.trim();
        this.x = clamp01(defaultX);
        this.y = clamp01(defaultY);
    }

    public abstract HudSize getSize();

    public final HudRect getRect(float screenWidth, float screenHeight) {
        requireScreenSize(screenWidth, screenHeight);
        HudSize size = Objects.requireNonNull(getSize(), "getSize()");
        float scaledWidth = (float) (size.width() * scale);
        float scaledHeight = (float) (size.height() * scale);
        float anchorX = (float) (x * screenWidth);
        float anchorY = (float) (y * screenHeight);
        HudAnchor anchor = getAnchor();
        float left = anchor.isRight() ? anchorX - scaledWidth : anchorX;
        float top = anchor.isBottom() ? anchorY - scaledHeight : anchorY;
        return new HudRect(left, top, scaledWidth, scaledHeight);
    }

    public final void render(SkijaFrame frame, Consumer<SkijaFrame> renderer) {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(renderer, "renderer");
        HudRect rect = getRect(frame.width(), frame.height());
        frame.canvas().save();
        try {
            frame.canvas().translate(rect.x(), rect.y());
            frame.canvas().scale((float) scale, (float) scale);
            renderer.accept(frame);
        } finally {
            frame.canvas().restore();
        }
    }

    public final void setPosition(double x, double y) {
        requireFinite(x, "x");
        requireFinite(y, "y");
        updatePosition(clamp01(x), clamp01(y));
    }

    public final void moveTo(float left, float top, float screenWidth, float screenHeight) {
        requireScreenSize(screenWidth, screenHeight);
        HudRect current = getRect(screenWidth, screenHeight);
        float clampedLeft = clamp(left, 0f, Math.max(0f, screenWidth - current.width()));
        float clampedTop = clamp(top, 0f, Math.max(0f, screenHeight - current.height()));
        boolean right = clampedLeft + current.width() * 0.5f >= screenWidth * 0.5f;
        boolean bottom = clampedTop + current.height() * 0.5f >= screenHeight * 0.5f;
        double nextX = (right ? clampedLeft + current.width() : clampedLeft) / screenWidth;
        double nextY = (bottom ? clampedTop + current.height() : clampedTop) / screenHeight;
        updatePosition(clamp01(nextX), clamp01(nextY));
    }

    public final void setScale(double scale) {
        requireFinite(scale, "scale");
        double next = clamp(scale, MIN_SCALE, MAX_SCALE);
        if (Double.compare(this.scale, next) == 0) return;
        this.scale = next;
        changeListener.run();
    }

    public final String getName() {
        return name;
    }

    public final double getX() {
        return x;
    }

    public final double getY() {
        return y;
    }

    public final double getScale() {
        return scale;
    }

    public final HudAnchor getAnchor() {
        return HudAnchor.from(x, y);
    }

    public final void setChangeListener(Runnable listener) {
        changeListener = listener == null ? () -> {} : listener;
    }

    private void updatePosition(double x, double y) {
        if (Double.compare(this.x, x) == 0 && Double.compare(this.y, y) == 0) return;
        this.x = x;
        this.y = y;
        changeListener.run();
    }

    private static void requireScreenSize(float width, float height) {
        if (!Float.isFinite(width) || !Float.isFinite(height) || width <= 0f || height <= 0f) {
            throw new IllegalArgumentException("Screen size must be finite and positive");
        }
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
    }

    private static double clamp01(double value) {
        return clamp(value, 0.0, 1.0);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
