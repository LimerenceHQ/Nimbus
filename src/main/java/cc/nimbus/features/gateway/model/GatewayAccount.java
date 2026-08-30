package cc.nimbus.features.gateway.model;

import java.util.Objects;

/** Authenticated NetEase account. Secrets are persisted only through DPAPI. */
public record GatewayAccount(
        String userId,
        String userToken,
        String sdkUid,
        String sessionId,
        String displayName,
        String loginChannel,
        String sAuthJson
) {
    public GatewayAccount {
        userId = requireValue(userId, "userId");
        userToken = requireValue(userToken, "userToken");
        sdkUid = requireValue(sdkUid, "sdkUid");
        sessionId = requireValue(sessionId, "sessionId");
        displayName = displayName == null || displayName.isBlank() ? userId : displayName.trim();
        loginChannel = loginChannel == null || loginChannel.isBlank() ? "netease" : loginChannel.trim();
        sAuthJson = sAuthJson == null ? "" : sAuthJson.trim();
    }

    /** Retains compatibility with protected accounts written before SAuth import support. */
    public GatewayAccount(
            String userId,
            String userToken,
            String sdkUid,
            String sessionId,
            String displayName,
            String loginChannel
    ) {
        this(userId, userToken, sdkUid, sessionId, displayName, loginChannel, "");
    }

    private static String requireValue(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " must not be blank");
        return result;
    }
}
