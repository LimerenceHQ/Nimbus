package cc.nimbus.ui.clickgui.component;

import cc.nimbus.core.Core;
import cc.nimbus.ui.clickgui.theme.ClickGuiTheme;
import cc.nimbus.utils.render.font.NimbusFonts;
import cc.nimbus.utils.render.skija.api.SkijaFrame;
import io.github.humbleui.skija.Canvas;

public record UiContext(
        SkijaFrame frame,
        ClickGuiTheme theme,
        float mouseX,
        float mouseY,
        float deltaSeconds,
        long nowMillis
) {
    private static final float FONT_SCALE = 1.18f;
    public static final String FONT = NimbusFonts.BODY;
    public static final String FONT_MEDIUM = NimbusFonts.BODY_MEDIUM;

    public void text(String value, float x, float y, float size, int color) {
        draw(FONT, size, value, x, y, color);
    }

    public void medium(String value, float x, float y, float size, int color) {
        draw(FONT_MEDIUM, size, value, x, y, color);
    }

    public void draw(String family, float size, String value, float x, float y, int color) {
        frame.text().draw(family, fontSize(size), value, x, y, color);
    }

    public void center(String family, float size, String value, float centerX, float y, int color) {
        frame.text().center(family, fontSize(size), value, centerX, y, color);
    }

    public float width(String family, float size, String value) {
        return frame.text().width(family, fontSize(size), value);
    }

    public float height(String family, float size) {
        return frame.text().height(family, fontSize(size));
    }

    public float height(String family, float size, String value) {
        return frame.text().height(family, fontSize(size), value);
    }

    public void visualLeft(String family, float size, String value, float x, float y, int color) {
        float scaledSize = fontSize(size);
        frame.text().draw(family, scaledSize, value,
                x - frame.text().visualLeft(family, scaledSize, value), y, color);
    }

    public float visualCenterY(String family, float size, String value) {
        return frame.text().visualCenterY(family, fontSize(size), value);
    }

    public void textIn(String value, UiRect bounds, float size, int color) {
        float textY = bounds.y() + (bounds.height() - height(FONT, size, value)) * 0.5f;
        text(value, bounds.x(), textY, size, color);
    }

    public void visualTextIn(String value, UiRect bounds, float size, int color) {
        visualTextIn(FONT, value, bounds, size, color);
    }

    public void visualTextIn(String family, String value, UiRect bounds, float size, int color) {
        float scaledSize = fontSize(size);
        float textY = bounds.y() + bounds.height() * .5f
                - frame.text().visualCenterY(family, scaledSize, value);
        frame.text().draw(family, scaledSize, value, bounds.x(), textY, color);
    }

    public void centered(String value, UiRect bounds, float size, int color) {
        float textY = bounds.y() + (bounds.height() - height(FONT, size, value)) * 0.5f;
        center(FONT, size, value, bounds.x() + bounds.width() * 0.5f, textY, color);
    }

    public void visualCentered(String value, UiRect bounds, float size, int color) {
        float scaledSize = fontSize(size);
        float textY = bounds.y() + bounds.height() * .5f
                - frame.text().visualCenterY(FONT, scaledSize, value);
        frame.text().center(FONT, scaledSize, value, bounds.x() + bounds.width() * .5f, textY, color);
    }

    public String translate(String key, String fallback, Object... arguments) {
        if (!Core.INSTANCE.isInitialized()) return fallback;
        return Core.INSTANCE.getI18nManager().get(key, fallback, arguments);
    }

    public UiContext withoutPointer() {
        return new UiContext(frame, theme, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY,
                deltaSeconds, nowMillis);
    }

    public int clip(UiRect bounds, float radius) {
        Canvas canvas = frame.canvas();
        int save = canvas.save();
        frame.draw().clipSmoothRoundedRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), radius);
        return save;
    }

    public void restore(int save) {
        frame.canvas().restoreToCount(save);
    }

    private static float fontSize(float size) {
        return size * FONT_SCALE;
    }
}
