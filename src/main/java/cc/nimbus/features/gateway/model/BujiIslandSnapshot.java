package cc.nimbus.features.gateway.model;

import java.util.List;
import java.util.Objects;

/** Server data revalidated from NetEase immediately before launch. */
public record BujiIslandSnapshot(
        String gameId,
        String name,
        String gameVersion,
        String serverHost,
        int serverPort,
        List<GatewayRole> roles
) {
    public BujiIslandSnapshot {
        gameId = Objects.requireNonNull(gameId, "gameId");
        name = Objects.requireNonNull(name, "name");
        gameVersion = Objects.requireNonNull(gameVersion, "gameVersion");
        serverHost = Objects.requireNonNull(serverHost, "serverHost");
        if (serverPort < 1 || serverPort > 65_535) {
            throw new IllegalArgumentException("invalid serverPort: " + serverPort);
        }
        roles = List.copyOf(roles);
    }
}
