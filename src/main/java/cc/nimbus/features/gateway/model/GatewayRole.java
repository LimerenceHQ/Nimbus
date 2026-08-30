package cc.nimbus.features.gateway.model;

import java.util.Objects;

public record GatewayRole(String gameId, String userId, String name, long createdAt, long expiresAt) {
    public GatewayRole {
        gameId = Objects.requireNonNullElse(gameId, "");
        userId = Objects.requireNonNullElse(userId, "");
        name = Objects.requireNonNullElse(name, "");
    }
}
