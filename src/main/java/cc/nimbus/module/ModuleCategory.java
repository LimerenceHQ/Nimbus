package cc.nimbus.module;

public enum ModuleCategory {
    Combat("Combat"), Move("Move"), Player("Player"), Render("Render"), Exploit("Exploit"), Ghost("Ghost"), HUD("HUD");
    private final String name;
    ModuleCategory(String name) { this.name = name; }
    public String getName() { return name; }
    @Override public String toString() { return name; }
}
