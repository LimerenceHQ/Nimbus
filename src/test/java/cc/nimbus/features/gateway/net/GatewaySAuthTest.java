package cc.nimbus.features.gateway.net;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class GatewaySAuthTest {
    @Test
    void generatesCommon4399Credential() throws Exception {
        GatewaySAuth credential = GatewaySAuth.channel4399Common("Nimbus", "42", "state");

        assertEquals("4399com", credential.loginChannel());
        assertEquals("4399com", credential.appChannel());
        assertEquals("42", credential.sdkUid());
        assertEquals("state", credential.sessionId());
        assertEquals("4399_Gen", credential.deviceId());
        assertEquals("4399_Gen", com.google.gson.JsonParser.parseString(credential.json())
                .getAsJsonObject().get("client_login_sn").getAsString());
        assertEquals("3.12.2", com.google.gson.JsonParser.parseString(credential.json())
                .getAsJsonObject().get("sdk_version").getAsString());
    }

    private static final String RAW = """
            {
              "gameid":"x19",
              "login_channel":"4399pc",
              "app_channel":"4399pc",
              "platform":"pc",
              "sdkuid":"4399-uid",
              "sessionid":"4399-token",
              "udid":"device-a",
              "deviceid":"device-a",
              "userid":"player4399"
            }
            """;

    @Test
    void acceptsRawAndWrappedNelCookieFormats() throws Exception {
        GatewaySAuth raw = GatewaySAuth.parse(RAW);
        JsonObject wrapper = new JsonObject();
        wrapper.addProperty("sauth_json", RAW);
        GatewaySAuth wrapped = GatewaySAuth.parse(wrapper.toString());

        assertEquals("4399pc", raw.loginChannel());
        assertEquals("player4399", raw.displayName());
        assertEquals(raw.sdkUid(), wrapped.sdkUid());
        assertEquals(raw.sessionId(), wrapped.sessionId());
        assertFalse(raw.isNetease());
    }

    @Test
    void acceptsCookieHeaderUrlEncodingAndBrowserExport() throws Exception {
        String encoded = java.net.URLEncoder.encode(RAW, java.nio.charset.StandardCharsets.UTF_8);
        GatewaySAuth header = GatewaySAuth.parse("Cookie: other=1; sauth_json=" + encoded + "; tail=2");
        JsonObject exported = new JsonObject();
        exported.addProperty("name", "sauth_json");
        exported.addProperty("value", RAW);
        com.google.gson.JsonArray cookies = new com.google.gson.JsonArray();
        cookies.add(exported);
        GatewaySAuth browser = GatewaySAuth.parse(cookies.toString());

        assertEquals("4399-uid", header.sdkUid());
        assertEquals("4399-uid", browser.sdkUid());
    }

    @Test
    void rejectsNonX19AndIncompleteCookies() {
        assertThrows(GatewayProtocolException.class, () -> GatewaySAuth.parse("{\"gameid\":\"other\"}"));
        assertThrows(GatewayProtocolException.class, () -> GatewaySAuth.parse("{\"gameid\":\"x19\"}"));
    }

    @Test
    void builds4399PcSAuthWithExpectedNelFields() throws Exception {
        GatewaySAuth value = GatewaySAuth.channel4399("nimbus-user", "uid", "token", "1700000000");
        JsonObject json = JsonParser.parseString(value.json()).getAsJsonObject();

        assertEquals("x19", json.get("gameid").getAsString());
        assertEquals("4399pc", json.get("login_channel").getAsString());
        assertEquals("uid", json.get("sdkuid").getAsString());
        assertEquals("token", json.get("sessionid").getAsString());
        assertEquals("1700000000", json.get("timestamp").getAsString());
        assertEquals("nimbus-user", json.get("userid").getAsString());
    }
}
