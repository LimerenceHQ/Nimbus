package cc.nimbus.features.gateway.net;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class NeteaseGatewayServiceTest {
    @Test
    void includesNelRoleQueryDefaults() {
        JsonObject request = NeteaseGatewayService.gameCharactersRequest("4661334467366178884", "user");

        assertEquals(0, request.get("offset").getAsInt());
        assertEquals(10, request.get("length").getAsInt());
        assertEquals("2", request.get("game_type").getAsString());
        assertEquals("user", request.get("user_id").getAsString());
        assertEquals("4661334467366178884", request.get("game_id").getAsString());
    }

    @Test
    void parsesNelPatchListFormatAndUtf8Bom() throws Exception {
        var versions = NeteaseGatewayService.parsePatchVersions(
                "\uFEFF\"3.2.1\":{\"url\":\"a\"},\r\n\"3.2.2\":{\"url\":\"b\"},\r\n"
        );

        assertEquals(2, versions.size());
        assertEquals("b", versions.getAsJsonObject("3.2.2").get("url").getAsString());
    }
}
