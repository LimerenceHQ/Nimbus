package cc.nimbus.ui.clickgui.input;

import cc.nimbus.ui.clickgui.component.UiContext;
import cc.nimbus.ui.clickgui.component.UiRect;
import cc.nimbus.ui.clickgui.theme.ClickGuiTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.PreeditEvent;

public final class MultilineTextInput {
    private static final float PADDING = 7f;
    private static final float LINE_HEIGHT = 14f;
    private final MultilineTextField editor;
    private boolean focused;
    private String preedit = "";

    public MultilineTextInput(Font font, int maximumLength) {
        editor = new MultilineTextField(font, 480);
        editor.setCharacterLimit(maximumLength > 0 ? maximumLength : MultilineTextField.NO_LIMIT);
    }

    public String value() {
        return editor.value();
    }

    public void maximumLength(int value) {
        editor.setCharacterLimit(value > 0 ? value : MultilineTextField.NO_LIMIT);
    }

    public void value(String value) {
        editor.setValue(value == null ? "" : value);
    }

    public void focused(boolean value) {
        focused = value;
        if (!value) preedit = "";
    }

    public boolean focused() {
        return focused;
    }

    public boolean keyPressed(KeyEvent event) {
        return focused && editor.keyPressed(event);
    }

    public boolean charTyped(CharacterEvent event) {
        if (!focused || !event.isAllowedChatCharacter()) return false;
        editor.insertText(event.codepointAsString());
        return true;
    }

    public boolean preeditUpdated(PreeditEvent event) {
        if (!focused) return false;
        preedit = event == null ? "" : event.fullText();
        return true;
    }

    public void placeCursor(UiRect bounds, float x, float y) {
        editor.seekCursorToPoint(Math.max(0f, x - bounds.x() - PADDING),
                Math.max(0f, y - bounds.y() - PADDING));
    }

    public void render(UiContext context, UiRect bounds, String placeholder, float size, boolean secure) {
        context.frame().draw().roundedRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 3f,
                focused ? context.theme().accent(0x42) : ClickGuiTheme.CONTROL);
        context.frame().draw().outline(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 3f, 1f,
                focused ? context.theme().accent(0x55) : ClickGuiTheme.STROKE);
        int save = context.clip(new UiRect(bounds.x() + 2f, bounds.y() + 2f,
                bounds.width() - 4f, bounds.height() - 4f), 2f);
        try {
            String raw = editor.value();
            if (raw.isEmpty() && preedit.isEmpty()) {
                context.text(placeholder, bounds.x() + PADDING, bounds.y() + PADDING, size, ClickGuiTheme.MUTED);
                return;
            }
            int cursor = editor.cursor();
            int lineIndex = 0;
            int begin = 0;
            for (String value : raw.split("\\n", -1)) {
                float lineY = bounds.y() + PADDING + lineIndex * LINE_HEIGHT;
                if (lineY > bounds.y() + bounds.height()) break;
                int end = begin + value.length();
                String display = secure ? "*".repeat(value.length()) : value;
                if (cursor >= begin && cursor <= end && !preedit.isEmpty()) {
                    int local = cursor - begin;
                    display = display.substring(0, local) + preedit + display.substring(local);
                }
                context.text(display, bounds.x() + PADDING, lineY, size, ClickGuiTheme.TEXT);
                if (focused && cursor >= begin && cursor <= end
                        && (context.nowMillis() / 500L & 1L) == 0L) {
                    int local = Math.min(cursor - begin, value.length());
                    float cursorX = context.width(UiContext.FONT, size,
                            secure ? "*".repeat(local) : value.substring(0, local));
                    context.frame().draw().rect(bounds.x() + PADDING + cursorX, lineY - 1f,
                            1f, LINE_HEIGHT, ClickGuiTheme.TEXT);
                }
                lineIndex++;
                begin = end + 1;
            }
        } finally {
            context.restore(save);
        }
    }
}
