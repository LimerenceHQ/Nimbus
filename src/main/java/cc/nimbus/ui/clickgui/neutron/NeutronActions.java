package cc.nimbus.ui.clickgui.neutron;

import cc.nimbus.config.ConfigManager;
import cc.nimbus.module.ModuleCategory;
import cc.nimbus.settings.Setting;
import cc.nimbus.settings.impl.NumberSetting;
import cc.nimbus.settings.impl.StringSetting;
import cc.nimbus.settings.impl.TextSetting;
import cc.nimbus.ui.clickgui.neutron.NeutronState.Page;

public class NeutronActions {
    private final NeutronState state;

    public NeutronActions(NeutronState state) {
        this.state = state;
    }

    public void setCategory(ModuleCategory category) {
        if (category == null) return;
        state.pageFrom = state.page;
        state.page = Page.MODULES;
        state.pageAnimStartMs = System.currentTimeMillis();
        state.selectedCategory = category;
    }

    public void setPage(Page page) {
        if (page == null) return;
        state.pageFrom = state.page;
        state.page = page;
        state.pageAnimStartMs = System.currentTimeMillis();
    }

    public void setSearchFocused(boolean focused) {
        state.searchFocused = focused;
    }

    public void backspaceSearch() {
        if (!state.searchText.isEmpty()) {
            state.searchText.deleteCharAt(state.searchText.length() - 1);
        }
    }

    public void appendSearchChar(char c) {
        state.searchText.append(c);
    }

    public void updateCaret() {
        long now = System.currentTimeMillis();
        if (now - state.lastCaretMs > 500) {
            state.caretVisible = !state.caretVisible;
            state.lastCaretMs = now;
        }
    }

    public void updateNumberCaret() {
        long now = System.currentTimeMillis();
        if (now - state.lastNumberCaretMs > 500) {
            state.numberCaretVisible = !state.numberCaretVisible;
            state.lastNumberCaretMs = now;
        }
    }

    public void updateTextCaret() {
        long now = System.currentTimeMillis();
        if (now - state.lastTextCaretMs > 500) {
            state.textCaretVisible = !state.textCaretVisible;
            state.lastTextCaretMs = now;
        }
    }

    public void startNumberInput(NumberSetting setting) {
        if (setting == null) return;
        cancelText();
        state.editingNumber = setting;
        state.numberFocused = true;
        state.numberInput.setLength(0);
        Number value = setting.getValue();
        state.numberInput.append(value == null ? "" : value.toString());
        state.numberCaretVisible = true;
        state.lastNumberCaretMs = 0L;
    }

    public void appendNumberChar(char c) {
        if (!state.numberFocused) return;
        if ((c >= '0' && c <= '9') || c == '-' || c == '.') {
            state.numberInput.append(c);
        }
    }

    public void backspaceNumber() {
        if (!state.numberFocused) return;
        int len = state.numberInput.length();
        if (len > 0) {
            state.numberInput.deleteCharAt(len - 1);
        }
    }

    public void cancelNumber() {
        state.numberFocused = false;
        state.editingNumber = null;
        state.numberInput.setLength(0);
    }

    public void commitNumber() {
        if (!state.numberFocused || state.editingNumber == null) return;
        try {
            double v = Double.parseDouble(state.numberInput.toString().trim());
            double min = state.editingNumber.getMinimum().doubleValue();
            double max = state.editingNumber.getMaximum().doubleValue();
            double inc = state.editingNumber.getIncrement().doubleValue();
            if (v < min) v = min;
            if (v > max) v = max;
            if (inc > 0d) {
                v = Math.round(v / inc) * inc;
            }
            state.editingNumber.setValue(v);
            ConfigManager.markDirty();
        } catch (Exception ignored) {
        }
        cancelNumber();
    }

    public void startTextInput(Setting<String> setting) {
        if (setting == null) return;
        cancelNumber();
        state.editingText = setting;
        state.textFocused = true;
        state.textInput.setLength(0);
        String value = setting.getValue();
        if (value != null) {
            state.textInput.append(value);
        }
        state.textMaxLen = getTextMaxLen(setting);
        state.textCaretVisible = true;
        state.lastTextCaretMs = 0L;
    }

    public void appendTextChar(char c) {
        if (!state.textFocused) return;
        if (Character.isISOControl(c)) return;
        if (state.textInput.length() >= state.textMaxLen) return;
        state.textInput.append(c);
    }

    public void backspaceText() {
        if (!state.textFocused) return;
        String s = state.textInput.toString();
        if (s.isEmpty()) return;
        int end = s.length();
        int start = s.offsetByCodePoints(end, -1);
        state.textInput.setLength(0);
        state.textInput.append(s, 0, start);
    }

    public void cancelText() {
        state.textFocused = false;
        state.editingText = null;
        state.textInput.setLength(0);
    }

    public void commitText() {
        if (!state.textFocused || state.editingText == null) return;
        state.editingText.setValue(state.textInput.toString());
        ConfigManager.markDirty();
        cancelText();
    }

    private int getTextMaxLen(Setting<String> setting) {
        if (setting instanceof TextSetting textSetting) {
            int max = textSetting.getSize();
            return max <= 0 ? 256 : max;
        }
        if (setting instanceof StringSetting) {
            return 256;
        }
        return 256;
    }

    public void quickSave() {
        String cfg = ConfigManager.getCurrentConfig();
        java.util.concurrent.CompletableFuture.supplyAsync(() -> ConfigManager.saveConfig(cfg)).thenAccept(ok -> {
            showToast(ok ? "已保存：" + cfg : "保存失败");
        });
    }

    public void showToast(String message) {
        state.toast = message;
        state.toastUntilMs = System.currentTimeMillis() + 2200L;
    }
}
