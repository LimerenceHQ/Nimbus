package cc.nimbus.core.interfaces;

import cc.nimbus.core.Core;

public interface InstanceAccess {
    default Core core() {
        return Core.INSTANCE;
    }
}
