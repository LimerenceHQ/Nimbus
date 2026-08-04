package cc.nimbus.ui.clickgui.tool;

import cc.nimbus.ui.clickgui.component.UiContext;
import cc.nimbus.ui.clickgui.component.UiRect;
import cc.nimbus.ui.clickgui.theme.ClickGuiTheme;

final class ToolHeader {
    static final float HEIGHT = 62f;

    private ToolHeader() {
    }

    static void render(UiContext context, UiRect bounds, String title, String description) {
        float x = bounds.x() + 22f;
        context.visualLeft(UiContext.FONT_MEDIUM, 19f, title, x, bounds.y() + 18f,
                ClickGuiTheme.TEXT);
        context.visualLeft(UiContext.FONT, 10.5f, description, x, bounds.y() + 43f,
                ClickGuiTheme.MUTED);
    }
}
