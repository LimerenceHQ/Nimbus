package cc.nimbus.gateway;

import cc.nimbus.features.gateway.model.BujiIslandProfile;
import cc.nimbus.features.gateway.launch.GatewayLaunchServices;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

/** One white-client A ↔ Nimbus B bridge session. */
public final class GatewaySession implements AutoCloseable {
    private final WhiteClientProcess whiteClient;
    private final GatewayNativeHost host;
    private final int localPort;
    private final GatewayLaunchServices launchServices;

    private GatewaySession(WhiteClientProcess whiteClient, GatewayNativeHost host, int localPort,
                           GatewayLaunchServices launchServices) {
        this.whiteClient = whiteClient;
        this.host = host;
        this.localPort = localPort;
        this.launchServices = launchServices;
        if (host != null) host.ready().whenComplete((state, error) -> {
            if (error != null) whiteClient.close();
        });
        whiteClient.exited().thenRun(() -> {
            if (host != null) host.close();
            if (launchServices != null) launchServices.close();
        });
    }

    public static GatewaySession start(WhiteClientProcess whiteClient,
                                       Consumer<GatewayState> listener) throws IOException {
        return startInternal(whiteClient, null, listener);
    }

    /** Starts only the real white client for protocol/Mod validation; no injector or DLL is executed. */
    public static GatewaySession startWhiteOnly(WhiteClientProcess whiteClient,
                                                GatewayLaunchServices launchServices,
                                                Consumer<GatewayState> listener) throws IOException {
        Objects.requireNonNull(whiteClient, "whiteClient");
        if (!whiteClient.isAlive()) throw new IOException("white client exited before startup");
        if (listener != null) listener.accept(new GatewayState(GatewayStage.BRIDGE_READY, whiteClient.pid(), 0, "white-client-only"));
        return new GatewaySession(whiteClient, null, 0, launchServices);
    }

    private static GatewaySession startInternal(WhiteClientProcess whiteClient,
                                                GatewayLaunchServices launchServices,
                                                Consumer<GatewayState> listener) throws IOException {
        Objects.requireNonNull(whiteClient, "whiteClient");
        if (!whiteClient.isAlive()) throw new IOException("white client exited before Gateway startup");
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            port = socket.getLocalPort();
        }
        GatewayNativeHost host = GatewayNativeHost.start(whiteClient.pid(), port, listener);
        return new GatewaySession(whiteClient, host, port, launchServices);
    }

    /** AuthSock/RPC services are started before Java, then the bridge is attached. */
    public static GatewaySession start(WhiteClientProcess whiteClient,
                                       GatewayLaunchServices launchServices,
                                       Consumer<GatewayState> listener) throws IOException {
        Objects.requireNonNull(launchServices, "launchServices");
        return startInternal(whiteClient, launchServices, listener);
    }

    public int localPort() {
        return localPort;
    }

    /** True when this session intentionally runs only the authorised white client. */
    public boolean isWhiteOnly() {
        return host == null;
    }

    public CompletableFuture<GatewayState> ready() {
        return host == null
                ? CompletableFuture.completedFuture(new GatewayState(GatewayStage.BRIDGE_READY, whiteClient.pid(), 0, "white-client-only"))
                : host.ready();
    }

    /** Connects Nimbus B to the loopback endpoint after the native host is ready. */
    public CompletableFuture<GatewayState> connectMinecraft(Screen parent) {
        return ready().thenApply(state -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.execute(() -> ConnectScreen.startConnecting(
                    parent,
                    minecraft,
                    ServerAddress.parseString("127.0.0.1:" + localPort),
                    new ServerData(BujiIslandProfile.DISPLAY_NAME,
                            "127.0.0.1:" + localPort, false),
                    false));
            return state;
        });
    }

    public boolean isAlive() {
        return whiteClient.isAlive() && (host == null || !host.ready().isCompletedExceptionally());
    }

    @Override
    public void close() {
        if (host != null) host.close();
        whiteClient.close();
        if (launchServices != null) launchServices.close();
    }
}
