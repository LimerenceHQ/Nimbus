package cc.nimbus.gateway;

public final class GatewayFailure extends Exception {
    public GatewayFailure(String message) {
        super(message);
    }

    public GatewayFailure(String message, Throwable cause) {
        super(message, cause);
    }
}
