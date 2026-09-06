package cc.nimbus.ui.clickgui.component;

import cc.nimbus.core.Core;
import cc.nimbus.ui.clickgui.theme.ClickGuiTheme;
import cc.nimbus.utils.render.font.FontSpec;
import cc.nimbus.utils.render.font.NimbusFonts;
import cc.nimbus.utils.render.skija.api.SkijaFrame;
import io.github.humbleui.skija.Canvas;

public record UiContext(
        SkijaFrame frame,
        ClickGuiTheme theme,
        float mouseX,
        float mouseY,
        float deltaSeconds,
        long nowMillis,
        float logicalWidth,
        float logicalHeight
) {
    private static final float FONT_SCALE = 1.18f;
    public static final FontSpec FONT = NimbusFonts.BODY;
    public static final FontSpec FONT_MEDIUM = NimbusFonts.BODY_MEDIUM;

    public void text(String value, float x, float y, float size, int color) {
        draw(FONT, size, value, x, y, color);
    }

    public void medium(String value, float x, float y, float size, int color) {
        draw(FONT_MEDIUM, size, value, x, y, color);
    }

    public void draw(FontSpec font, float size, String value, float x, float y, int color) {
        frame.text().draw(font, fontSize(size), value, x, y, color);
    }

    public void center(FontSpec font, float size, String value, float centerX, float y, int color) {
        frame.text().center(font, fontSize(size), value, centerX, y, color);
    }

    public float width(FontSpec font, float size, String value) {
        return frame.text().width(font, fontSize(size), value);
    }

    public float height(FontSpec font, float size) {
        return frame.text().height(font, fontSize(size));
    }

    public float height(FontSpec font, float size, String value) {
        return frame.text().height(font, fontSize(size), value);
    }

    public void visualLeft(FontSpec font, float size, String value, float x, float y, int color) {
        float scaledSize = fontSize(size);
        frame.text().draw(font, scaledSize, value,
                x - frame.text().visualLeft(font, scaledSize, value), y, color);
    }

    public float visualCenterY(FontSpec font, float size, String value) {
        return frame.text().visualCenterY(font, fontSize(size), value);
    }

    public void textIn(String value, UiRect bounds, float size, int color) {
        float textY = bounds.y() + (bounds.height() - height(FONT, size, value)) * 0.5f;
        text(value, bounds.x(), textY, size, color);
    }

    public void visualTextIn(String value, UiRect bounds, float size, int color) {
        visualTextIn(FONT, value, bounds, size, color);
    }

    public void visualTextIn(FontSpec font, String value, UiRect bounds, float size, int color) {
        float scaledSize = fontSize(size);
        float textY = bounds.y() + bounds.height() * .5f
                - frame.text().visualCenterY(font, scaledSize, value);
        frame.text().draw(font, scaledSize, value, bounds.x(), textY, color);
    }

    public void stableTextIn(String value, UiRect bounds, float size, int color) {
        float scaledSize = fontSize(size);
        String reference = containsCjk(value) ? "\u4E2D\u6587" : "Aa";
        float textY = bounds.y() + bounds.height() * .5f
                - frame.text().visualCenterY(FONT, scaledSize, reference);
        frame.text().draw(FONT, scaledSize, value, bounds.x(), textY, color);
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

    public int accentAt(float x, int maximumAlpha) {
        float position = logicalWidth <= 0f ? 0f : Math.clamp(x / logicalWidth, 0f, 1f);
        return theme.accentAt(position, maximumAlpha);
    }

    public int accentIn(UiRect bounds, int maximumAlpha) {
        return accentAt(bounds.x() + bounds.width() * .5f, maximumAlpha);
    }

    public UiContext withoutPointer() {
        return new UiContext(frame, theme, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY,
                deltaSeconds, nowMillis, logicalWidth, logicalHeight);
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

    private static boolean containsCjk(String value) {
        if (value == null) return false;
        return value.codePoints().anyMatch(codePoint -> {
            Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
            return script == Character.UnicodeScript.HAN
                    || script == Character.UnicodeScript.HIRAGANA
                    || script == Character.UnicodeScript.KATAKANA
                    || script == Character.UnicodeScript.HANGUL;
        });
    }
}
