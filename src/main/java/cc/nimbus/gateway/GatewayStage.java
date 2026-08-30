package cc.nimbus.gateway;

public enum GatewayStage {
    CREATED,
    INJECTED,
    JVM_ATTACHED,
    HOOK_INSTALLED,
    BSERVER_READY,
    A_CONNECTION_CAPTURED,
    BRIDGE_READY,
    FAILED;

    static GatewayStage parse(String value) {
        try {
            return value == null ? CREATED : valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return CREATED;
        }
    }
}
