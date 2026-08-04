package cc.nimbus.ui.clickgui.input;

import cc.nimbus.ui.clickgui.component.UiContext;
import cc.nimbus.ui.clickgui.component.UiRect;
import cc.nimbus.ui.clickgui.theme.ClickGuiTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.network.chat.Component;

public final class TextInput {
    private final EditBox editor;
    private String preedit = "";
    private boolean focused;
    private float scrollX;

    public TextInput(Font font, int maximumLength) {
        editor = new EditBox(font, Component.empty());
        editor.setBordered(false);
        editor.setMaxLength(Math.max(1, maximumLength));
    }

    public String value() {
        return editor.getValue();
    }

    public void maximumLength(int value) {
        editor.setMaxLength(Math.max(1, value));
    }

    public void value(String value) {
        editor.setValue(value == null ? "" : value);
        editor.moveCursorToEnd(false);
        scrollX = 0f;
    }

    public boolean focused() {
        return focused;
    }

    void focused(boolean value) {
        focused = value;
        editor.setFocused(value);
        if (!value) preedit = "";
    }

    public boolean keyPressed(KeyEvent event) {
        return focused && editor.keyPressed(event);
    }

    public boolean charTyped(CharacterEvent event) {
        return focused && editor.charTyped(event);
    }

    public boolean preeditUpdated(PreeditEvent event) {
        if (!focused) return false;
        preedit = event == null ? "" : event.fullText();
        editor.preeditUpdated(event);
        return true;
    }

    public void placeCursor(UiContext context, UiRect bounds, float size, float mouseX) {
        String value = editor.getValue();
        float local = Math.max(0f, mouseX - bounds.x() - 8f + scrollX);
        int position = value.length();
        float previousWidth = 0f;
        for (int index = 0; index < value.length();) {
            int next = value.offsetByCodePoints(index, 1);
            float nextWidth = context.width(UiContext.FONT, size, value.substring(0, next));
            if (local < (previousWidth + nextWidth) * .5f) {
                position = index;
                break;
            }
            previousWidth = nextWidth;
            index = next;
        }
        editor.setCursorPosition(position);
        editor.setHighlightPos(position);
    }

    public void render(UiContext context, UiRect bounds, String placeholder, float size, boolean secure) {
        int background = focused ? context.theme().accent(0x42) : ClickGuiTheme.CONTROL;
        render(context, bounds, placeholder, size, secure, background);
    }

    public void render(UiContext context, UiRect bounds, String placeholder, float size, boolean secure,
                       int background) {
        render(context, bounds, placeholder, size, secure, background,
                focused ? context.theme().accent(0x55) : ClickGuiTheme.STROKE);
    }

    public void render(UiContext context, UiRect bounds, String placeholder, float size, boolean secure,
                       int background, int outline) {
        context.frame().draw().roundedRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 3f,
                background);
        context.frame().draw().outline(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 3f, 1f,
                outline);

        String raw = editor.getValue();
        String display = secure ? "*".repeat(raw.length()) : raw;
        int cursor = Math.min(editor.getCursorPosition(), display.length());
        String composed = display.substring(0, cursor) + preedit + display.substring(cursor);
        boolean empty = composed.isEmpty();
        float availableWidth = Math.max(1f, bounds.width() - 16f);
        float cursorWidth = context.width(UiContext.FONT, size, display.substring(0, cursor) + preedit);
        float contentWidth = context.width(UiContext.FONT, size, composed);
        if (focused) {
            if (cursorWidth - scrollX > availableWidth) scrollX = cursorWidth - availableWidth;
            if (cursorWidth - scrollX < 0f) scrollX = cursorWidth;
            scrollX = Math.clamp(scrollX, 0f, Math.max(0f, contentWidth - availableWidth));
        } else {
            scrollX = 0f;
        }
        float textX = bounds.x() + 8f - scrollX;
        String rendered = empty ? placeholder : composed;
        float textY = bounds.y() + (bounds.height() - context.height(UiContext.FONT, size, rendered)) * 0.5f;
        int save = context.clip(new UiRect(bounds.x() + 6f, bounds.y(), bounds.width() - 12f, bounds.height()), 2f);
        try {
            String selected = editor.getHighlighted();
            if (focused && !selected.isEmpty()) {
                int selectedLength = selected.length();
                int start = Math.max(0, cursor - selectedLength);
                if (cursor + selectedLength <= raw.length()
                        && raw.substring(cursor, cursor + selectedLength).equals(selected)) {
                    start = cursor;
                }
                float selectionX = textX + context.width(UiContext.FONT, size, display.substring(0, start));
                float selectionW = context.width(UiContext.FONT, size,
                        display.substring(start, Math.min(display.length(), start + selectedLength)));
                context.frame().draw().rect(selectionX, bounds.y() + 5f, selectionW, bounds.height() - 10f,
                        context.theme().accent(0x42));
            }
            context.text(empty ? placeholder : composed, textX, textY, size,
                    empty ? ClickGuiTheme.MUTED : ClickGuiTheme.TEXT);
            if (focused && (context.nowMillis() / 500L & 1L) == 0L) {
                float cursorX = textX + context.width(UiContext.FONT, size, display.substring(0, cursor));
                context.frame().draw().rect(cursorX, bounds.y() + 5f, 1f, bounds.height() - 10f,
                        ClickGuiTheme.TEXT);
            }
        } finally {
            context.restore(save);
        }
    }
}
