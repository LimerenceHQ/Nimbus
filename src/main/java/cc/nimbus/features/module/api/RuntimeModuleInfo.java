package cc.nimbus.features.module.api;

import java.lang.annotation.Annotation;
import java.util.Objects;

public final class RuntimeModuleInfo implements ModuleInfo {
    private final String name;
    private final String displayName;
    private final String description;
    private final ModuleCategory category;
    private final int keyBind;

    public RuntimeModuleInfo(
            String name,
            String displayName,
            String description,
            ModuleCategory category,
            int keyBind
    ) {
        this.name = Objects.requireNonNull(name, "name");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.description = Objects.requireNonNull(description, "description");
        this.category = Objects.requireNonNull(category, "category");
        this.keyBind = keyBind;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public String[] alias() {
        return new String[0];
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public ModuleCategory category() {
        return category;
    }

    @Override
    public int keyBind() {
        return keyBind;
    }

    @Override
    public boolean enableOnStartUp() {
        return false;
    }

    @Override
    public Class<? extends Annotation> annotationType() {
        return ModuleInfo.class;
    }
}
