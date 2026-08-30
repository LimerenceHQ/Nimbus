package cc.nimbus.features.gateway.net;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/** Parsed X19 SAuth credential, commonly called a "cookie" by NEL launchers. */
public record GatewaySAuth(
        String json,
        String sdkUid,
        String sessionId,
        String loginChannel,
        String appChannel,
        String udid,
        String deviceId,
        String displayName
) {
    public GatewaySAuth {
        json = require(json, "SAuth JSON");
        sdkUid = require(sdkUid, "sdkuid");
        sessionId = require(sessionId, "sessionid");
        loginChannel = require(loginChannel, "login_channel").toLowerCase(Locale.ROOT);
        appChannel = require(appChannel, "app_channel").toLowerCase(Locale.ROOT);
        udid = require(udid, "udid");
        deviceId = require(deviceId, "deviceid");
        displayName = displayName == null || displayName.isBlank() ? sdkUid : displayName.trim();
    }

    public static GatewaySAuth parse(String cookie) throws GatewayProtocolException {
        try {
            String normalized = normalizeCookie(Objects.requireNonNull(cookie, "cookie"));
            JsonElement input = JsonParser.parseString(normalized);
            if (input.isJsonPrimitive() && input.getAsJsonPrimitive().isString()) {
                input = JsonParser.parseString(input.getAsString());
            }
            if (input.isJsonArray()) input = findExportedCookie(input);
            if (!input.isJsonObject()) throw new GatewayProtocolException("Cookie 必须是 X19 SAuth JSON");
            JsonObject object = input.getAsJsonObject();
            if (object.has("name") && "sauth_json".equalsIgnoreCase(string(object, "name", ""))
                    && object.has("value")) {
                object = parseNested(object.get("value"));
            }
            if (object.has("sauth_json")) {
                JsonElement wrapped = object.get("sauth_json");
                object = wrapped.isJsonObject() ? wrapped.getAsJsonObject() : parseNested(wrapped);
            }
            String gameId = string(object, "gameid", "x19");
            if (!"x19".equalsIgnoreCase(gameId)) {
                throw new GatewayProtocolException("Cookie 不是 Minecraft X19 登录凭据");
            }
            String loginChannel = requiredString(object, "login_channel");
            return new GatewaySAuth(
                    object.toString(),
                    requiredString(object, "sdkuid"),
                    requiredString(object, "sessionid"),
                    loginChannel,
                    string(object, "app_channel", loginChannel),
                    requiredString(object, "udid"),
                    requiredString(object, "deviceid"),
                    firstNonBlank(string(object, "userid", ""), string(object, "username", ""))
            );
        } catch (GatewayProtocolException error) {
            throw error;
        } catch (RuntimeException error) {
            throw new GatewayProtocolException(
                    "Cookie 不是完整的 X19 SAuth JSON（长度 "
                            + (cookie == null ? 0 : cookie.length()) + "）",
                    error
            );
        }
    }

    private static String normalizeCookie(String input) throws GatewayProtocolException {
        String value = input.trim();
        if (value.regionMatches(true, 0, "Cookie:", 0, 7)) value = value.substring(7).trim();
        if (!value.startsWith("{") && !value.startsWith("[") && !value.startsWith("\"")) {
            for (String part : value.split(";")) {
                int separator = part.indexOf('=');
                if (separator < 0) continue;
                String name = part.substring(0, separator).trim();
                if (!"sauth_json".equalsIgnoreCase(name)) continue;
                value = URLDecoder.decode(part.substring(separator + 1).trim(), StandardCharsets.UTF_8);
                break;
            }
        }
        if (value.isBlank()) throw new GatewayProtocolException("Cookie 不能为空");
        return value;
    }

    private static JsonObject parseNested(JsonElement value) {
        if (value.isJsonObject()) return value.getAsJsonObject();
        String text = value.getAsString().trim();
        try {
            return JsonParser.parseString(text).getAsJsonObject();
        } catch (RuntimeException first) {
            return JsonParser.parseString(URLDecoder.decode(text, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static JsonElement findExportedCookie(JsonElement input) throws GatewayProtocolException {
        for (JsonElement item : input.getAsJsonArray()) {
            if (!item.isJsonObject()) continue;
            JsonObject object = item.getAsJsonObject();
            if ("sauth_json".equalsIgnoreCase(string(object, "name", "")) && object.has("value")) return object;
        }
        throw new GatewayProtocolException("浏览器 Cookie 导出中没有 sauth_json");
    }

    public static GatewaySAuth netease(
            String sdkUid,
            String sessionId,
            String udid,
            String deviceId,
            String displayName
    ) throws GatewayProtocolException {
        JsonObject value = base("netease", "pc", sdkUid, sessionId, udid, deviceId);
        value.addProperty("sdk_version", "4.2.0");
        return parse(value.toString()).withDisplayName(displayName);
    }

    public static GatewaySAuth channel4399(String userName, String sdkUid, String token, String timestamp)
            throws GatewayProtocolException {
        String uniqueId = UUID.randomUUID().toString().replace("-", "");
        JsonObject value = base("4399pc", "pc", sdkUid, token, uniqueId, uniqueId);
        value.addProperty("client_login_sn", uniqueId);
        value.addProperty("gas_token", "");
        value.addProperty("ip", "127.0.0.1");
        value.addProperty("realname", "{\"realname_type\":\"0\"}");
        value.addProperty("sdk_version", "1.0.0");
        value.addProperty("source_platform", "pc");
        value.addProperty("timestamp", require(timestamp, "timestamp"));
        value.addProperty("userid", require(userName, "userid"));
        return parse(value.toString()).withDisplayName(userName);
    }

    public static GatewaySAuth channel4399Common(String userName, String sdkUid, String sessionId)
            throws GatewayProtocolException {
        // Match the Nimbus Gateway PE SAuth payload. The X19 uni_sauth
        // endpoint validates more than uid/state, so these channel values are
        // deliberately not shared with the older 4399 PC credential shape.
        String udid = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        JsonObject value = base("4399com", "ad", sdkUid, sessionId, udid, "4399_Gen");
        value.addProperty("client_login_sn", "4399_Gen");
        value.addProperty("realname", "{\"realname_type\":2}");
        value.addProperty("sdk_version", "3.12.2");
        return parse(value.toString()).withDisplayName(userName);
    }

    public JsonObject wrapper() {
        JsonObject wrapper = new JsonObject();
        wrapper.addProperty("sauth_json", json);
        return wrapper;
    }

    public boolean isNetease() {
        return "netease".equals(loginChannel);
    }

    private GatewaySAuth withDisplayName(String value) {
        return new GatewaySAuth(json, sdkUid, sessionId, loginChannel, appChannel, udid, deviceId, value);
    }

    private static JsonObject base(
            String channel,
            String platform,
            String sdkUid,
            String sessionId,
            String udid,
            String deviceId
    ) {
        JsonObject value = new JsonObject();
        value.addProperty("aim_info", "{\"aim\":\"127.0.0.1\",\"country\":\"CN\",\"tz\":\"+0800\",\"tzid\":\"\"}");
        value.addProperty("app_channel", channel);
        value.addProperty("deviceid", deviceId);
        value.addProperty("gameid", "x19");
        value.addProperty("login_channel", channel);
        value.addProperty("platform", platform);
        value.addProperty("sdkuid", sdkUid);
        value.addProperty("sessionid", sessionId);
        value.addProperty("udid", udid);
        return value;
    }

    private static String requiredString(JsonObject object, String name) throws GatewayProtocolException {
        String value = string(object, name, "");
        if (value.isBlank()) throw new GatewayProtocolException("Cookie 缺少 " + name);
        return value;
    }

    private static String string(JsonObject object, String name, String fallback) {
        if (!object.has(name) || object.get(name).isJsonNull()) return fallback;
        try {
            return object.get(name).getAsString();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static String require(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " must not be blank");
        return result;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return "";
    }
}
