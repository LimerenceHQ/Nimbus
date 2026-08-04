package cc.nimbus.ui.clickgui.input;

import cc.nimbus.ui.clickgui.component.UiContext;
import cc.nimbus.ui.clickgui.component.UiRect;
import cc.nimbus.ui.clickgui.theme.ClickGuiTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.components.Whence;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.PreeditEvent;

import java.util.ArrayList;
import java.util.List;

public final class MultilineTextInput {
    private static final float PADDING = 7f;
    private static final float LINE_HEIGHT = 14f;
    private final MultilineTextField editor;
    private boolean focused;
    private String preedit = "";
    private float scrollY;

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
        scrollY = 0f;
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

    public void placeCursor(UiContext context, UiRect bounds, float size, float x, float y) {
        List<WrappedLine> lines = wrap(context, editor.value(), size, bounds.width() - PADDING * 2f);
        float lineHeight = lineHeight(context, size);
        int lineIndex = Math.clamp((int) ((y - bounds.y() - PADDING + scrollY) / lineHeight),
                0, lines.size() - 1);
        WrappedLine line = lines.get(lineIndex);
        float localX = Math.max(0f, x - bounds.x() - PADDING);
        int position = line.end();
        float previousWidth = 0f;
        for (int index = line.start(); index < line.end();) {
            int next = editor.value().offsetByCodePoints(index, 1);
            float nextWidth = context.width(UiContext.FONT, size,
                    editor.value().substring(line.start(), next));
            if (localX < (previousWidth + nextWidth) * .5f) {
                position = index;
                break;
            }
            previousWidth = nextWidth;
            index = next;
        }
        editor.seekCursor(Whence.ABSOLUTE, position);
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
            List<WrappedLine> lines = wrap(context, raw, size, bounds.width() - PADDING * 2f);
            float lineHeight = lineHeight(context, size);
            int cursorLine = cursorLine(lines, cursor);
            float visibleHeight = Math.max(lineHeight, bounds.height() - PADDING * 2f);
            if (focused) {
                float cursorTop = cursorLine * lineHeight;
                if (cursorTop < scrollY) scrollY = cursorTop;
                if (cursorTop + lineHeight > scrollY + visibleHeight) {
                    scrollY = cursorTop + lineHeight - visibleHeight;
                }
            }
            scrollY = Math.clamp(scrollY, 0f, Math.max(0f, lines.size() * lineHeight - visibleHeight));
            for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
                WrappedLine line = lines.get(lineIndex);
                float lineY = bounds.y() + PADDING + lineIndex * lineHeight - scrollY;
                if (lineY + lineHeight < bounds.y() || lineY > bounds.y() + bounds.height()) continue;
                String value = line.text();
                String display = secure ? "*".repeat(value.length()) : value;
                boolean cursorOnLine = containsCursor(lines, lineIndex, cursor);
                if (cursorOnLine && !preedit.isEmpty()) {
                    int local = cursor - line.start();
                    display = display.substring(0, local) + preedit + display.substring(local);
                }
                context.text(display, bounds.x() + PADDING, lineY, size, ClickGuiTheme.TEXT);
                if (focused && cursorOnLine
                        && (context.nowMillis() / 500L & 1L) == 0L) {
                    int local = Math.min(cursor - line.start(), value.length());
                    float cursorX = context.width(UiContext.FONT, size,
                            secure ? "*".repeat(local) : value.substring(0, local));
                    context.frame().draw().rect(bounds.x() + PADDING + cursorX, lineY - 1f,
                            1f, lineHeight, ClickGuiTheme.TEXT);
                }
            }
        } finally {
            context.restore(save);
        }
    }

    public static void renderPreview(UiContext context, UiRect bounds, String value,
                                     float size, boolean secure, int color) {
        String raw = value == null ? "" : value;
        List<WrappedLine> lines = wrap(context, raw, size, bounds.width() - PADDING * 2f);
        float lineHeight = lineHeight(context, size);
        int save = context.clip(new UiRect(bounds.x() + 2f, bounds.y() + 2f,
                bounds.width() - 4f, bounds.height() - 4f), 2f);
        try {
            for (int index = 0; index < lines.size(); index++) {
                float y = bounds.y() + PADDING + index * lineHeight;
                if (y > bounds.y() + bounds.height() - PADDING) break;
                String line = lines.get(index).text();
                context.text(secure ? "*".repeat(line.length()) : line,
                        bounds.x() + PADDING, y, size, color);
            }
        } finally {
            context.restore(save);
        }
    }

    private static List<WrappedLine> wrap(UiContext context, String value, float size, float width) {
        List<WrappedLine> lines = new ArrayList<>();
        int paragraphStart = 0;
        while (paragraphStart <= value.length()) {
            int newline = value.indexOf('\n', paragraphStart);
            int paragraphEnd = newline < 0 ? value.length() : newline;
            if (paragraphStart == paragraphEnd) {
                lines.add(new WrappedLine(paragraphStart, paragraphEnd, ""));
            } else {
                int start = paragraphStart;
                while (start < paragraphEnd) {
                    int end = start;
                    while (end < paragraphEnd) {
                        int next = value.offsetByCodePoints(end, 1);
                        if (context.width(UiContext.FONT, size, value.substring(start, next)) > width
                                && end > start) break;
                        end = next;
                    }
                    if (end == start) end = value.offsetByCodePoints(start, 1);
                    lines.add(new WrappedLine(start, end, value.substring(start, end)));
                    start = end;
                }
            }
            if (newline < 0) break;
            paragraphStart = newline + 1;
        }
        return lines.isEmpty() ? List.of(new WrappedLine(0, 0, "")) : lines;
    }

    private static int cursorLine(List<WrappedLine> lines, int cursor) {
        for (int index = 0; index < lines.size(); index++) {
            if (containsCursor(lines, index, cursor)) return index;
        }
        return lines.size() - 1;
    }

    private static boolean containsCursor(List<WrappedLine> lines, int index, int cursor) {
        WrappedLine line = lines.get(index);
        if (cursor < line.start() || cursor > line.end()) return false;
        return cursor != line.end() || index + 1 >= lines.size() || lines.get(index + 1).start() != cursor;
    }

    private static float lineHeight(UiContext context, float size) {
        return Math.max(LINE_HEIGHT, context.height(UiContext.FONT, size, "国") + 2f);
    }

    private record WrappedLine(int start, int end, String text) {}
}
