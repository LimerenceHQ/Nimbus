package cc.nimbus.utils.render;

import cc.nimbus.utils.render.skija.api.SkijaFrame;

import java.util.Objects;

public final class RenderUtils {

    private RenderUtils() {
    }

    public static void drawLogo(SkijaFrame frame,
                                          float x, float y,
                                          float originX, float originY,
                                          float scale, int color) {
        drawLogo(frame, x, y, 24f,
                originX, originY, scale, color);
    }

    public static void drawLogo(SkijaFrame frame,
                                          float x, float y, float size,
                                          float originX, float originY,
                                          float scale, int color) {
        Objects.requireNonNull(frame, "frame");
        if (!Float.isFinite(size) || size <= 0f) {
            throw new IllegalArgumentException("size must be finite and positive");
        }
        if (!Float.isFinite(scale) || scale <= 0f) {
            throw new IllegalArgumentException("scale must be finite and positive");
        }

        float unit = size / 24f;
        x -= .5f * unit;
        logoRect(frame, originX, originY, scale, x + 9f * unit, y + 3f * unit, 7f * unit, 3f * unit, color);
        logoRect(frame, originX, originY, scale, x + 6f * unit, y + 6f * unit, 13f * unit, 3f * unit, color);
        logoRect(frame, originX, originY, scale, x + 3f * unit, y + 9f * unit, 19f * unit, 7f * unit, color);
        float rainX = x - unit;
        logoRain(frame, originX, originY, scale, rainX + 7f * unit, y + 18f * unit, unit, color);
        logoRain(frame, originX, originY, scale, rainX + 12f * unit, y + 18f * unit, unit, color);
        logoRain(frame, originX, originY, scale, rainX + 17f * unit, y + 18f * unit, unit, color);
    }

    private static void logoRain(SkijaFrame frame,
                                 float originX, float originY, float scale,
                                 float x, float y, float unit, int color) {
        logoRect(frame, originX, originY, scale, x, y, 3f * unit, 2f * unit, color);
        logoRect(frame, originX, originY, scale, x - unit, y + 2f * unit, 3f * unit, 2f * unit, color);
        logoRect(frame, originX, originY, scale, x - 2f * unit, y + 4f * unit, 3f * unit, unit, color);
    }

    private static void logoRect(SkijaFrame frame,
                                  float originX, float originY, float scale,
                                  float x, float y, float width, float height, int color) {
        float left = snapLocal(x, originX, scale);
        float top = snapLocal(y, originY, scale);
        float right = snapLocal(x + width, originX, scale);
        float bottom = snapLocal(y + height, originY, scale);
        frame.draw().rect(left, top, right - left, bottom - top, color);
    }

    private static float snapLocal(float value, float origin, float scale) {
        return (Math.round(origin + value * scale) - origin) / scale;
    }
}
