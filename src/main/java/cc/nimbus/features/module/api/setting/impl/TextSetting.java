package cc.nimbus.features.module.api.setting.impl;

import cc.nimbus.features.module.api.setting.Setting;
import java.util.function.Supplier;

public class TextSetting extends Setting<String> {
    private final String placeholder;
    private final boolean secure;
    private final boolean multiline;
    private final String description;
    private final int size;

    public TextSetting(String name, String value) { this(name, value, null, false, false, null, 0, () -> true); }
    public TextSetting(String name, String value, Supplier<Boolean> visibility) { this(name, value, null, false, false, null, 0, visibility); }
    public TextSetting(String name, String value, String placeholder, boolean secure, boolean multiline, String description, int size) {
        this(name, value, placeholder, secure, multiline, description, size, () -> true);
    }
    public TextSetting(String name, String value, String placeholder, boolean secure, boolean multiline, String description, int size, Supplier<Boolean> visibility) {
        super(name, value, visibility);
        this.placeholder = placeholder;
        this.secure = secure;
        this.multiline = multiline;
        this.description = description;
        this.size = size;
    }
    public String getPlaceholder() { return placeholder; }
    public boolean isSecure() { return secure; }
    public boolean isMultiline() { return multiline; }
    public String getDescription() { return description; }
    public int getSize() { return size; }
}
