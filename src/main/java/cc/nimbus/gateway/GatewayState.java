package cc.nimbus.gateway;

/** Immutable state snapshot emitted by nimbus-gateway-host. */
public record GatewayState(GatewayStage stage, long pid, int port, String detail) {
    public boolean isReady() {
        return stage == GatewayStage.BRIDGE_READY;
    }

    public boolean isFailure() {
        return stage == GatewayStage.FAILED;
    }
}
