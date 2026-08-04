package cc.nimbus.features.module.api;

import cc.nimbus.utils.i18n.I18nKeys;

public enum ModuleCategory {
    Combat("Combat"),
    Move("Move"),
    Player("Player"),
    Render("Render"),
    Exploit("Exploit"),
    HUD("HUD");

    private final String name;
    ModuleCategory(String name) { this.name = name; }
    public String getName() { return name; }
    public String getTranslationKey() { return "nimbus.category." + I18nKeys.part(name); }
    @Override public String toString() { return name; }
}
