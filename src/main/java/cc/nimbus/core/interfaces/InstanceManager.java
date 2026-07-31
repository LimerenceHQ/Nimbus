package cc.nimbus.core.interfaces;

public interface InstanceManager extends InstanceAccess {
    void init();

    void shutdown();
}
