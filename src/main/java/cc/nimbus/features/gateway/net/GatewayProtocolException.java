package cc.nimbus.features.gateway.net;

public final class GatewayProtocolException extends Exception {
    private final int code;
    private final String verificationUrl;

    public GatewayProtocolException(String message) {
        this(message, 0, "", null);
    }

    public GatewayProtocolException(String message, Throwable cause) {
        this(message, 0, "", cause);
    }

    public GatewayProtocolException(String message, int code, String verificationUrl) {
        this(message, code, verificationUrl, null);
    }

    private GatewayProtocolException(String message, int code, String verificationUrl, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.verificationUrl = verificationUrl == null ? "" : verificationUrl;
    }

    public int code() {
        return code;
    }

    public String verificationUrl() {
        return verificationUrl;
    }
}
