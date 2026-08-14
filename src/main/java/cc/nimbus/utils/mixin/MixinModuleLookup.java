package cc.nimbus.utils.mixin;

import cc.nimbus.core.Core;
import cc.nimbus.features.module.api.BasicModule;

public final class MixinModuleLookup {
    private MixinModuleLookup() {
    }

    public static <T extends BasicModule> T enabled(Class<T> type) {
        if (!Core.INSTANCE.isInitialized()) return null;
        T module = Core.INSTANCE.getModuleManager().getModule(type);
        return module != null && module.isEnabled() ? module : null;
    }
}
