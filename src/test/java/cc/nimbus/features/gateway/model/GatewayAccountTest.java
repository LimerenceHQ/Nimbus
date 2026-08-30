package cc.nimbus.features.gateway.model;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class GatewayAccountTest {
    @Test
    void protectedLegacyPayloadWithoutSAuthRemainsReadable() {
        GatewayAccount account = new Gson().fromJson("""
                {
                  "userId":"1",
                  "userToken":"token",
                  "sdkUid":"sdk",
                  "sessionId":"session",
                  "displayName":"legacy",
                  "loginChannel":"netease"
                }
                """, GatewayAccount.class);

        assertEquals("legacy", account.displayName());
        assertEquals("", account.sAuthJson());
    }
}
