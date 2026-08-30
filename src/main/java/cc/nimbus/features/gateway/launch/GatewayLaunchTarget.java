package cc.nimbus.features.gateway.launch;

import cc.nimbus.features.gateway.model.BujiIslandProfile;
import cc.nimbus.features.gateway.model.BujiIslandSnapshot;
import cc.nimbus.features.gateway.model.GatewayAccount;
import cc.nimbus.features.gateway.model.GatewayRole;

import java.util.Objects;

/** Immutable white-client launch selection shared by RPC, AuthSock and the command builder. */
public record GatewayLaunchTarget(
        GatewayAccount account,
        BujiIslandSnapshot server,
        GatewayRole role
) {
    public GatewayLaunchTarget {
        account = Objects.requireNonNull(account, "account");
        server = Objects.requireNonNull(server, "server");
        role = Objects.requireNonNull(role, "role");
        if (!BujiIslandProfile.GAME_ID.equals(server.gameId())) {
            throw new IllegalArgumentException("Nimbus Gateway only supports 布吉岛");
        }
        if (!server.gameId().equals(role.gameId())) {
            throw new IllegalArgumentException("Selected role does not belong to 布吉岛");
        }
        if (!role.userId().isBlank() && !account.userId().equals(role.userId())) {
            throw new IllegalArgumentException("Selected role does not belong to the NetEase account");
        }
        String selectedRoleName = role.name();
        boolean returnedByServer = server.roles().stream()
                .anyMatch(candidate -> candidate.name().equals(selectedRoleName));
        if (!returnedByServer) throw new IllegalArgumentException("Selected role is not in the latest server snapshot");
    }
}
