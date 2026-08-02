package cc.nimbus.ui.clickgui.input;

public final class FocusManager {
    private TextInput focused;

    public void focus(TextInput input) {
        if (focused == input) return;
        if (focused != null) focused.focused(false);
        focused = input;
        if (focused != null) focused.focused(true);
    }

    public TextInput focused() {
        return focused;
    }

    public boolean hasFocus() {
        return focused != null;
    }

    public void clear() {
        focus(null);
    }
}
