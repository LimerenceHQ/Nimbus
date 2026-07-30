package cc.nimbus.ui.clickgui.neutron;

import cc.nimbus.module.BasicModule;
import cc.nimbus.module.ModuleCategory;
import cc.nimbus.settings.Setting;
import cc.nimbus.settings.impl.NumberSetting;

public class NeutronState {
    public enum Page {
        MODULES,
        CONFIG
    }

    public ModuleCategory selectedCategory = ModuleCategory.Combat;
    public Page page = Page.MODULES;

    public final StringBuilder searchText = new StringBuilder();
    public boolean searchFocused;
    public boolean caretVisible = true;
    public long lastCaretMs;

    public NumberSetting editingNumber;
    public final StringBuilder numberInput = new StringBuilder();
    public boolean numberFocused;
    public boolean numberCaretVisible = true;
    public long lastNumberCaretMs;

    public Setting<String> editingText;
    public final StringBuilder textInput = new StringBuilder();
    public boolean textFocused;
    public boolean textCaretVisible = true;
    public long lastTextCaretMs;
    public int textMaxLen = 256;

    public boolean rightDown;
    public boolean rightPressed;

    public Page pageFrom = Page.MODULES;
    public long pageAnimStartMs;

    public BasicModule bindingModule;
    public String toast;
    public long toastUntilMs;
}
