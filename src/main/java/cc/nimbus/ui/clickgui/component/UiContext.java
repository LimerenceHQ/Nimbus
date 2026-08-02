package cc.nimbus.ui.clickgui.component;

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
    public static final String FONT = NimbusFonts.BODY;
    public static final String FONT_MEDIUM = NimbusFonts.BODY_MEDIUM;

    public void text(String value, float x, float y, float size, int color) {
        frame.text().draw(FONT, size, value, x, y, color);
    }

    public void medium(String value, float x, float y, float size, int color) {
        frame.text().draw(FONT_MEDIUM, size, value, x, y, color);
    }

    public void textIn(String value, UiRect bounds, float size, int color) {
        float textY = bounds.y() + (bounds.height() - frame.text().height(FONT, size)) * 0.5f;
        text(value, bounds.x(), textY, size, color);
    }

    public void centered(String value, UiRect bounds, float size, int color) {
        float textY = bounds.y() + (bounds.height() - frame.text().height(FONT, size)) * 0.5f;
        frame.text().center(FONT, size, value, bounds.x() + bounds.width() * 0.5f, textY, color);
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
}
