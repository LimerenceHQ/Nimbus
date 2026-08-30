package cc.nimbus.features.gateway.launch;

import java.io.IOException;
import java.util.Objects;

/** Owns the two loopback launcher services that must exist before white-client Java starts. */
public final class GatewayLaunchServices implements AutoCloseable {
    private final GatewayRpcServer rpc;
    private final GatewayAuthServer auth;
    private boolean closed;

    private GatewayLaunchServices(GatewayRpcServer rpc, GatewayAuthServer auth) {
        this.rpc = rpc;
        this.auth = auth;
    }

    public static GatewayLaunchServices start(
            GatewayLaunchTarget target,
            GatewayJoinAuthenticator authenticator
    ) throws IOException {
        Objects.requireNonNull(target, "target");
        GatewayRpcServer rpc = GatewayRpcServer.start(target);
        try {
            GatewayAuthServer auth = GatewayAuthServer.start(
                    target.server().gameId(),
                    target.account().userId(),
                    authenticator
            );
            System.out.println("[Nimbus Gateway] launcher services ready: AuthSock="
                    + auth.port() + ", RPC=" + rpc.port());
            return new GatewayLaunchServices(rpc, auth);
        } catch (IOException | RuntimeException error) {
            rpc.close();
            throw error;
        }
    }

    public int rpcPort() {
        return rpc.port();
    }

    public int authPort() {
        return auth.port();
    }

    public GatewayRpcServer rpc() {
        return rpc;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        auth.close();
        rpc.close();
    }
}
