package cc.nimbus.module;

import java.lang.annotation.Annotation;

public class RuntimeModuleInfo implements ModuleInfo {
    private final String name, displayName, description;
    private final ModuleCategory category;
    private final int keyBind;
    public RuntimeModuleInfo(String name, String displayName, String description, ModuleCategory category, int keyBind) {
        this.name = name; this.displayName = displayName; this.description = description; this.category = category; this.keyBind = keyBind;
    }
    public String name() { return name; }
    public String displayName() { return displayName; }
    public String[] alias() { return new String[0]; }
    public String description() { return description; }
    public ModuleCategory category() { return category; }
    public int keyBind() { return keyBind; }
    public boolean enableOnStartUp() { return false; }
    public Class<? extends Annotation> annotationType() { return ModuleInfo.class; }
}
