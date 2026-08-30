package cc.nimbus.features.gateway.net;

import cc.nimbus.features.gateway.model.BujiIslandProfile;
import cc.nimbus.features.gateway.model.BujiIslandSnapshot;
import cc.nimbus.features.gateway.model.GatewayAccount;
import cc.nimbus.features.gateway.model.GatewayDevice;
import cc.nimbus.features.gateway.model.GatewayRole;
import cc.nimbus.features.gateway.model.WhiteClientArtifact;
import cc.nimbus.features.gateway.model.WhiteClientComponent;
import cc.nimbus.features.gateway.model.WhiteClientManifest;
import cc.nimbus.features.gateway.model.WhiteClientPackage;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.math.BigInteger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** NetEase MPay/X19 client used only by Nimbus Gateway. */
public final class NeteaseGatewayService {
    private static final URI MPAY = URI.create("https://service.mkey.163.com");
    private static final URI OBT_CORE = URI.create("https://x19obtcore.nie.netease.com:8443");
    private static final URI API_GATEWAY = URI.create("https://x19apigatewayobt.nie.netease.com");
    private static final URI MINECRAFT_CLIENT = URI.create("https://x19mclobt.nie.netease.com");
    private static final URI MGB_SDK = URI.create("https://mgbsdk.matrix.netease.com");
    private static final URI PATCH_LIST = URI.create("https://x19.update.netease.com/pl/x19_java_patchlist");
    private static final String MPAY_GAME_ID = "aecfrxodyqaaaajp-g-x19";
    private static final String USER_AGENT = "WPFLauncher/0.0.0.0";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final HttpClient http;
    private volatile String launcherVersion;

    public NeteaseGatewayService() {
        http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public GatewayDevice initializeDevice(GatewayDevice saved) throws GatewayProtocolException {
        if (saved != null) return saved;
        String uniqueId = UUID.randomUUID().toString().replace("-", "");
        Map<String, String> parameters = baseMpayParameters();
        parameters.put("unique_id", uniqueId);
        parameters.put("brand", "Microsoft");
        parameters.put("device_model", "pc_mode");
        parameters.put("device_name", "PC-" + randomAlphaNumeric(12));
        parameters.put("device_type", "Computer");
        parameters.put("init_urs_device", "0");
        parameters.put("mac", randomMac());
        parameters.put("resolution", "1920x1080");
        parameters.put("system_name", "windows");
        parameters.put("system_version", "10.0.22621");

        JsonObject response = postForm(MPAY.resolve("/mpay/games/" + MPAY_GAME_ID + "/devices"), parameters);
        JsonObject device = requireObject(response, "device", "MPay device response");
        return new GatewayDevice(uniqueId, requireString(device, "id"), requireString(device, "key"));
    }

    public GatewayAccount loginWithEmail(GatewayDevice device, String email, char[] password)
            throws GatewayProtocolException {
        Objects.requireNonNull(device, "device");
        String normalizedEmail = Objects.requireNonNull(email, "email").trim();
        if (normalizedEmail.isEmpty()) throw new GatewayProtocolException("网易邮箱不能为空");
        if (password == null || password.length == 0) throw new GatewayProtocolException("网易密码不能为空");

        JsonObject loginParameters = new JsonObject();
        loginParameters.addProperty("password", GatewayCrypto.md5Hex(new String(password)));
        loginParameters.addProperty("unique_id", device.uniqueId());
        loginParameters.addProperty("username", normalizedEmail);
        String encrypted = GatewayCrypto.encryptMpayParameters(loginParameters.toString(), device.deviceKey());

        Map<String, String> parameters = baseMpayParameters();
        parameters.put("opt_fields",
                "nickname,avatar,realname_status,mobile_bind_status,mask_related_mobile,related_login_status");
        parameters.put("params", encrypted);
        parameters.put("un", Base64.getEncoder().encodeToString(normalizedEmail.getBytes(StandardCharsets.UTF_8)));

        URI endpoint = MPAY.resolve("/mpay/games/" + MPAY_GAME_ID + "/devices/" + device.deviceId() + "/users");
        JsonObject response = postFormAllowError(endpoint, parameters);
        if (!response.has("user") || !response.get("user").isJsonObject()) {
            int code = intValue(response, "code", 0);
            String reason = stringValue(response, "reason", "网易账号登录失败");
            throw new GatewayProtocolException(reason, code, stringValue(response, "verify_url", ""));
        }

        JsonObject user = response.getAsJsonObject("user");
        String sdkUid = requireString(user, "id");
        String sessionId = requireString(user, "token");
        String displayName = firstNonBlank(
                stringValue(user, "display_username", ""),
                stringValue(user, "nickname", ""),
                normalizedEmail
        );
        String udid = UUID.randomUUID().toString().replace("-", "").toUpperCase();
        return authenticate(GatewaySAuth.netease(sdkUid, sessionId, udid, device.deviceId(), displayName), displayName);
    }

    public GatewayAccount refresh(GatewayDevice device, GatewayAccount account) throws GatewayProtocolException {
        Objects.requireNonNull(account, "account");
        if (!account.sAuthJson().isBlank()) {
            return authenticate(GatewaySAuth.parse(account.sAuthJson()), account.displayName());
        }
        if (!"netease".equalsIgnoreCase(account.loginChannel())) {
            throw new GatewayProtocolException("该渠道账号缺少可刷新的 SAuth Cookie，请重新登录");
        }
        Objects.requireNonNull(device, "device");
        String udid = UUID.randomUUID().toString().replace("-", "").toUpperCase();
        return authenticate(
                GatewaySAuth.netease(
                        account.sdkUid(),
                        account.sessionId(),
                        udid,
                        device.deviceId(),
                        account.displayName()
                ),
                account.displayName()
        );
    }

    public GatewayAccount loginWithCookie(String cookie) throws GatewayProtocolException {
        GatewaySAuth sAuth = GatewaySAuth.parse(cookie);
        return authenticate(sAuth, sAuth.displayName());
    }

    public BujiIslandSnapshot loadBujiIsland(GatewayAccount account) throws GatewayProtocolException {
        Objects.requireNonNull(account, "account");

        JsonObject idRequest = new JsonObject();
        idRequest.addProperty("item_id", BujiIslandProfile.GAME_ID);
        JsonObject detailEnvelope = postSigned(API_GATEWAY, "/item-details/get_v2", idRequest, account);
        JsonObject detail = requireObject(detailEnvelope, "entity", "布吉岛详情");
        String returnedId = stringValue(detail, "entity_id", BujiIslandProfile.GAME_ID);
        if (!BujiIslandProfile.GAME_ID.equals(returnedId)) {
            throw new GatewayProtocolException("网易返回了错误的布吉岛服务器 ID: " + returnedId);
        }

        String version = readFirstVersion(detail);
        if (!BujiIslandProfile.NETEASE_VERSION.equals(version)) {
            throw new GatewayProtocolException("布吉岛白端版本已变化: " + version);
        }

        JsonObject addressEnvelope = postSigned(API_GATEWAY, "/item-address/get", idRequest, account);
        JsonObject address = requireObject(addressEnvelope, "entity", "布吉岛地址");
        String host = stringValue(address, "ip", stringValue(detail, "server_address", ""));
        int port = intValue(address, "port", intValue(detail, "server_port", 0));
        if (host.isBlank() || port < 1 || port > 65_535) {
            throw new GatewayProtocolException("网易返回的布吉岛地址无效");
        }

        JsonObject rolesRequest = gameCharactersRequest(BujiIslandProfile.GAME_ID, account.userId());
        JsonObject rolesEnvelope = postSigned(
                API_GATEWAY,
                "/game-character/query/user-game-characters",
                rolesRequest,
                account
        );
        List<GatewayRole> roles = readRoles(rolesEnvelope);
        return new BujiIslandSnapshot(
                BujiIslandProfile.GAME_ID,
                stringValue(detail, "name", BujiIslandProfile.DISPLAY_NAME),
                version,
                host,
                port,
                roles
        );
    }

    /** Mirrors NEL's POST /game-character endpoint. */
    public void createBujiIslandCharacter(GatewayAccount account, String roleName) throws GatewayProtocolException {
        Objects.requireNonNull(account, "account");
        String name = Objects.requireNonNullElse(roleName, "").trim();
        if (name.isBlank() || name.length() > 32) throw new GatewayProtocolException("角色名称不能为空且不能超过 32 个字符");
        JsonObject request = new JsonObject();
        request.addProperty("game_id", BujiIslandProfile.GAME_ID);
        request.addProperty("game_type", 2);
        request.addProperty("user_id", account.userId());
        request.addProperty("name", name);
        // These are the serialized defaults used by Fantnel's EntityGameCharacter.
        request.addProperty("create_time", 555555);
        request.addProperty("expire_time", 0);
        postSigned(API_GATEWAY, "/game-character", request, account);
    }

    /** Mirrors Fantnel EntityQueryGameCharacters including its serialized defaults. */
    static JsonObject gameCharactersRequest(String gameId, String userId) {
        JsonObject request = new JsonObject();
        request.addProperty("offset", 0);
        request.addProperty("length", 10);
        request.addProperty("user_id", userId);
        request.addProperty("game_id", gameId);
        request.addProperty("game_type", "2");
        return request;
    }

    public void notifyGameStart(GatewayAccount account) throws GatewayProtocolException {
        // Match Fantnel's InterConn.LoginStartAndGameStart sequence at the
        // moment the white client is launched (a persisted account may have
        // authenticated much earlier).
        JsonObject loginStart = new JsonObject();
        loginStart.addProperty("strict_mode", true);
        postSigned(OBT_CORE, "/interconn/web/game-play-v2/login-start", loginStart, account);
        JsonObject request = new JsonObject();
        request.addProperty("game_type", "2");
        request.addProperty("game_id", BujiIslandProfile.GAME_ID);
        request.addProperty("strict_mode", true);
        JsonArray items = new JsonArray();
        items.add("10000");
        request.add("item_list", items);
        postSigned(OBT_CORE, "/interconn/web/game-play-v2/start", request, account);
    }

    public WhiteClientManifest loadWhiteClientManifest(GatewayAccount account) throws GatewayProtocolException {
        Objects.requireNonNull(account, "account");
        WhiteClientPackage basePackage = loadClientPackage(account, 0);
        WhiteClientPackage versionPackage = loadClientPackage(account, BujiIslandProfile.NETEASE_VERSION_ID);

        JsonObject coreRequest = new JsonObject();
        coreRequest.addProperty("mc_version_id", BujiIslandProfile.NETEASE_VERSION_ID);
        coreRequest.addProperty("game_type", 2);
        JsonObject coreEnvelope = postSigned(
                API_GATEWAY,
                "/game-auth-item-list/query/search-by-game",
                coreRequest,
                account
        );
        JsonObject coreEntity = requireObject(coreEnvelope, "entity", "白端核心组件列表");
        JsonArray itemIds = coreEntity.has("iid_list") && coreEntity.get("iid_list").isJsonArray()
                ? coreEntity.getAsJsonArray("iid_list")
                : new JsonArray();
        List<WhiteClientComponent> coreComponents = List.of();
        if (!itemIds.isEmpty()) {
            JsonObject detailsRequest = new JsonObject();
            JsonArray numericIds = new JsonArray();
            for (JsonElement itemId : itemIds) {
                try {
                    numericIds.add(new BigInteger(itemId.getAsString()));
                } catch (RuntimeException error) {
                    throw new GatewayProtocolException("白端核心组件 ID 无效", error);
                }
            }
            detailsRequest.add("item_id_list", numericIds);
            JsonObject componentsEnvelope = postSigned(
                    API_GATEWAY,
                    "/user-item-download-v2/get-list",
                    detailsRequest,
                    account
            );
            coreComponents = readComponents(componentsEnvelope, "entities");
        }

        JsonObject serverRequest = new JsonObject();
        serverRequest.addProperty("item_id", BujiIslandProfile.GAME_ID);
        serverRequest.addProperty("length", 0);
        serverRequest.addProperty("offset", 0);
        JsonObject serverEnvelope = postSigned(
                MINECRAFT_CLIENT,
                "/user-item-download-v2",
                serverRequest,
                account
        );
        WhiteClientComponent serverComponent = readComponent(
                requireObject(serverEnvelope, "entity", "布吉岛白端组件")
        );
        return new WhiteClientManifest(basePackage, versionPackage, coreComponents, serverComponent);
    }

    private WhiteClientPackage loadClientPackage(GatewayAccount account, int minecraftVersionId)
            throws GatewayProtocolException {
        JsonObject request = new JsonObject();
        request.addProperty("mc_version", minecraftVersionId);
        JsonObject envelope = postSigned(MINECRAFT_CLIENT, "/game-patch-info", request, account);
        JsonObject entity = requireObject(envelope, "entity", "白端游戏包");
        return new WhiteClientPackage(
                stringValue(entity, "name", ""),
                stringValue(entity, "version", ""),
                intValue(entity, "mc_version", minecraftVersionId),
                requireUri(entity, "url"),
                requireString(entity, "md5"),
                longValue(entity, "size", 0),
                optionalUri(entity, "core_lib_url"),
                stringValue(entity, "core_lib_md5", ""),
                stringValue(entity, "core_lib_name", ""),
                longValue(entity, "core_lib_size", 0)
        );
    }

    public GatewayAccount loginWithSAuth(GatewaySAuth sAuth) throws GatewayProtocolException {
        Objects.requireNonNull(sAuth, "sAuth");
        return authenticate(sAuth, sAuth.displayName());
    }

    private GatewayAccount authenticate(GatewaySAuth sAuth, String displayName) throws GatewayProtocolException {
        if (!sAuth.isNetease()) authenticateChannelSession(sAuth);

        JsonObject loginOtpEnvelope = postJson(OBT_CORE.resolve("/login-otp"), sAuth.wrapper());
        checkEnvelope(loginOtpEnvelope, "网易 login-otp");
        JsonObject otp = requireObject(loginOtpEnvelope, "entity", "网易 login-otp");

        String version = launcherVersion();
        JsonObject detail = new JsonObject();
        detail.addProperty("os_name", "windows");
        detail.addProperty("os_ver", "Microsoft Windows 11 专业版");
        detail.addProperty("mac_addr", "");
        detail.addProperty("udid", sAuth.udid());
        detail.addProperty("app_ver", version);
        detail.addProperty("sdk_ver", "");
        detail.addProperty("network", "");
        detail.addProperty("disk", randomHex(4));
        detail.addProperty("is64bit", "1");
        detail.addProperty("launcher_type", "PC_java");
        detail.addProperty("pay_channel", sAuth.appChannel());

        JsonObject versionObject = new JsonObject();
        versionObject.addProperty("version", version);
        versionObject.addProperty("launcher_md5", "");
        versionObject.addProperty("updater_md5", "");

        JsonObject authentication = new JsonObject();
        authentication.addProperty("sa_data", detail.toString());
        authentication.addProperty("sauth_json", sAuth.json());
        authentication.add("version", versionObject);
        authentication.add("sdkuid", JsonNull.INSTANCE);
        authentication.addProperty("aid", Integer.toString(intValue(otp, "aid", 0)));
        authentication.addProperty("hasMessage", false);
        authentication.addProperty("hasGmail", false);
        authentication.addProperty("otp_token", requireString(otp, "otp_token"));
        authentication.add("otp_pwd", JsonNull.INSTANCE);
        authentication.addProperty("lock_time", intValue(otp, "lock_time", 0));
        authentication.add("env", JsonNull.INSTANCE);
        authentication.add("min_engine_version", JsonNull.INSTANCE);
        authentication.add("min_patch_version", JsonNull.INSTANCE);
        authentication.addProperty("verify_status", 0);
        authentication.add("unisdk_login_json", JsonNull.INSTANCE);
        authentication.add("token", JsonNull.INSTANCE);
        authentication.addProperty("is_register", true);
        authentication.add("entity_id", JsonNull.INSTANCE);

        byte[] encrypted = GatewayCrypto.encryptAuthentication(authentication.toString().getBytes(StandardCharsets.UTF_8));
        byte[] response = postBytes(OBT_CORE.resolve("/authentication-otp"), encrypted, "application/octet-stream");
        byte[] decrypted = GatewayCrypto.decryptAuthentication(response);
        JsonObject envelope = parseObject(new String(decrypted, StandardCharsets.UTF_8), "网易 authentication-otp");
        checkEnvelope(envelope, "网易 authentication-otp");
        JsonObject entity = requireObject(envelope, "entity", "网易 authentication-otp");

        GatewayAccount account = new GatewayAccount(
                requireString(entity, "entity_id"),
                requireString(entity, "token"),
                sAuth.sdkUid(),
                sAuth.sessionId(),
                displayName,
                sAuth.loginChannel(),
                sAuth.json()
        );
        JsonObject loginStart = new JsonObject();
        loginStart.addProperty("strict_mode", true);
        postSigned(OBT_CORE, "/interconn/web/game-play-v2/login-start", loginStart, account);
        return account;
    }

    private void authenticateChannelSession(GatewaySAuth sAuth) throws GatewayProtocolException {
        URI endpoint = MGB_SDK.resolve("/x19/sdk/uni_sauth");
        JsonObject response = parseObject(
                postText(endpoint, sAuth.json(), "application/json", Map.of()),
                "网易渠道会话认证"
        );
        if (intValue(response, "code", 0) != 200) {
            throw new GatewayProtocolException(
                    "网易渠道会话认证失败: " + stringValue(response, "status", "未知错误")
            );
        }
    }

    private Map<String, String> baseMpayParameters() throws GatewayProtocolException {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("app_channel", "netease");
        result.put("app_mode", "2");
        result.put("app_type", "games");
        result.put("arch", "win_x64");
        result.put("cv", "c4.2.0");
        result.put("mcount_app_key", "EEkEEXLymcNjM42yLY3Bn6AO15aGy4yq");
        result.put("mcount_transaction_id", "0");
        result.put("process_id", Long.toString(ProcessHandle.current().pid()));
        result.put("sv", "10.0.22621");
        result.put("updater_cv", "c1.0.0");
        result.put("game_id", MPAY_GAME_ID);
        result.put("gv", launcherVersion());
        return result;
    }

    public String launcherVersion() throws GatewayProtocolException {
        String cached = launcherVersion;
        if (cached != null) return cached;
        synchronized (this) {
            if (launcherVersion != null) return launcherVersion;
            JsonObject versions = parsePatchVersions(getText(PATCH_LIST));
            String latest = "";
            for (Map.Entry<String, JsonElement> entry : versions.entrySet()) latest = entry.getKey();
            if (latest.isBlank()) throw new GatewayProtocolException("网易白端版本列表为空");
            launcherVersion = latest;
            return latest;
        }
    }

    /** Mirrors Codexus X19.GetPatchVersionsAsync: content before the last comma, wrapped in braces. */
    static JsonObject parsePatchVersions(String response) throws GatewayProtocolException {
        String versions = Objects.requireNonNullElse(response, "");
        if (!versions.isEmpty() && versions.charAt(0) == '\uFEFF') versions = versions.substring(1);
        int lastComma = versions.lastIndexOf(',');
        if (lastComma < 0) {
            throw new GatewayProtocolException("网易白端版本列表缺少结尾分隔符");
        }
        return parseObject("{" + versions.substring(0, lastComma) + "}", "网易白端版本列表");
    }

    private JsonObject postSigned(URI base, String path, JsonObject request, GatewayAccount account)
            throws GatewayProtocolException {
        String body = request.toString();
        Map<String, String> headers = GatewayCrypto.dynamicHeaders(path, body, account.userId(), account.userToken());
        JsonObject response = postJson(base.resolve(path), body, headers);
        checkEnvelope(response, path);
        return response;
    }

    private JsonObject postForm(URI uri, Map<String, String> parameters) throws GatewayProtocolException {
        JsonObject response = postFormAllowError(uri, parameters);
        if (response.has("code") && intValue(response, "code", 0) != 0 && !response.has("device")) {
            throw new GatewayProtocolException(stringValue(response, "reason", "MPay 请求失败"),
                    intValue(response, "code", 0), stringValue(response, "verify_url", ""));
        }
        return response;
    }

    private JsonObject postFormAllowError(URI uri, Map<String, String> parameters) throws GatewayProtocolException {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            if (!body.isEmpty()) body.append('&');
            body.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            body.append('=');
            body.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        HttpRequest request = request(uri)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = http.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            return parseObject(response.body(), "MPay");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new GatewayProtocolException("网易请求已取消", interrupted);
        } catch (IOException error) {
            throw new GatewayProtocolException("无法连接网易服务: " + uri.getHost(), error);
        }
    }

    private JsonObject postJson(URI uri, JsonObject body) throws GatewayProtocolException {
        return postJson(uri, body.toString(), Map.of());
    }

    private JsonObject postJson(URI uri, String body, Map<String, String> headers) throws GatewayProtocolException {
        return parseObject(postText(uri, body, "application/json", headers), uri.getPath());
    }

    private String getText(URI uri) throws GatewayProtocolException {
        HttpRequest request = request(uri).GET().build();
        return send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
    }

    private String postText(URI uri, String body, String contentType, Map<String, String> headers)
            throws GatewayProtocolException {
        HttpRequest.Builder builder = request(uri)
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        headers.forEach(builder::header);
        return send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
    }

    private byte[] postBytes(URI uri, byte[] body, String contentType) throws GatewayProtocolException {
        HttpRequest request = request(uri)
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        return send(request, HttpResponse.BodyHandlers.ofByteArray()).body();
    }

    private HttpRequest.Builder request(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", USER_AGENT);
    }

    private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
            throws GatewayProtocolException {
        try {
            HttpResponse<T> response = http.send(request, handler);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new GatewayProtocolException(
                        "网易请求失败: " + request.uri().getPath() + " (HTTP " + response.statusCode() + ")"
                );
            }
            return response;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new GatewayProtocolException("网易请求已取消", interrupted);
        } catch (IOException error) {
            throw new GatewayProtocolException("无法连接网易服务: " + request.uri().getHost(), error);
        }
    }

    private static JsonObject parseObject(String json, String operation) throws GatewayProtocolException {
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) throw new GatewayProtocolException(operation + " 返回格式无效");
            return parsed.getAsJsonObject();
        } catch (RuntimeException error) {
            throw new GatewayProtocolException(operation + " 返回了无效 JSON", error);
        }
    }

    private static void checkEnvelope(JsonObject response, String operation) throws GatewayProtocolException {
        int code = intValue(response, "code", 0);
        if (code != 0) {
            String message = stringValue(response, "message", "失败");
            throw new GatewayProtocolException(
                    operation + ": " + message,
                    code,
                    stringValue(response, "verify_url", "")
            );
        }
    }

    private static JsonObject requireObject(JsonObject parent, String name, String operation)
            throws GatewayProtocolException {
        if (!parent.has(name) || !parent.get(name).isJsonObject()) {
            throw new GatewayProtocolException(operation + " 缺少 " + name);
        }
        return parent.getAsJsonObject(name);
    }

    private static String requireString(JsonObject object, String name) throws GatewayProtocolException {
        String result = stringValue(object, name, "");
        if (result.isBlank()) throw new GatewayProtocolException("网易响应缺少 " + name);
        return result;
    }

    private static String stringValue(JsonObject object, String name, String fallback) {
        if (!object.has(name) || object.get(name).isJsonNull()) return fallback;
        try {
            return object.get(name).getAsString();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static int intValue(JsonObject object, String name, int fallback) {
        if (!object.has(name) || object.get(name).isJsonNull()) return fallback;
        try {
            return object.get(name).getAsInt();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static long longValue(JsonObject object, String name, long fallback) {
        if (!object.has(name) || object.get(name).isJsonNull()) return fallback;
        try {
            return object.get(name).getAsLong();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static URI requireUri(JsonObject object, String name) throws GatewayProtocolException {
        String value = requireString(object, name);
        try {
            URI uri = URI.create(value);
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))) {
                throw new IllegalArgumentException("unsupported URI scheme");
            }
            return uri;
        } catch (IllegalArgumentException error) {
            throw new GatewayProtocolException("网易响应中的 " + name + " 地址无效", error);
        }
    }

    private static URI optionalUri(JsonObject object, String name) throws GatewayProtocolException {
        String value = stringValue(object, name, "");
        if (value.isBlank()) return null;
        try {
            URI uri = URI.create(value);
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))) {
                throw new IllegalArgumentException("unsupported URI scheme");
            }
            return uri;
        } catch (IllegalArgumentException error) {
            throw new GatewayProtocolException("网易响应中的 " + name + " 地址无效", error);
        }
    }

    private static String readFirstVersion(JsonObject detail) throws GatewayProtocolException {
        if (!detail.has("mc_version_list") || !detail.get("mc_version_list").isJsonArray()) {
            throw new GatewayProtocolException("布吉岛详情缺少白端版本");
        }
        JsonArray versions = detail.getAsJsonArray("mc_version_list");
        if (versions.isEmpty() || !versions.get(0).isJsonObject()) {
            throw new GatewayProtocolException("布吉岛没有可用白端版本");
        }
        return requireString(versions.get(0).getAsJsonObject(), "name");
    }

    private static List<GatewayRole> readRoles(JsonObject envelope) throws GatewayProtocolException {
        if (!envelope.has("entities") || !envelope.get("entities").isJsonArray()) {
            throw new GatewayProtocolException("网易角色响应缺少 entities");
        }
        List<GatewayRole> roles = new ArrayList<>();
        for (JsonElement element : envelope.getAsJsonArray("entities")) {
            if (!element.isJsonObject()) continue;
            JsonObject role = element.getAsJsonObject();
            String name = stringValue(role, "name", "");
            if (name.isBlank()) continue;
            roles.add(new GatewayRole(
                    stringValue(role, "game_id", BujiIslandProfile.GAME_ID),
                    stringValue(role, "user_id", ""),
                    name,
                    intValue(role, "create_time", 0),
                    intValue(role, "expire_time", 0)
            ));
        }
        return List.copyOf(roles);
    }

    private static List<WhiteClientComponent> readComponents(JsonObject envelope, String field)
            throws GatewayProtocolException {
        if (!envelope.has(field) || !envelope.get(field).isJsonArray()) {
            throw new GatewayProtocolException("白端组件响应缺少 " + field);
        }
        List<WhiteClientComponent> components = new ArrayList<>();
        for (JsonElement element : envelope.getAsJsonArray(field)) {
            if (element.isJsonObject()) components.add(readComponent(element.getAsJsonObject()));
        }
        return List.copyOf(components);
    }

    private static WhiteClientComponent readComponent(JsonObject component) throws GatewayProtocolException {
        List<WhiteClientArtifact> artifacts = new ArrayList<>();
        if (component.has("sub_entities") && component.get("sub_entities").isJsonArray()) {
            for (JsonElement element : component.getAsJsonArray("sub_entities")) {
                if (!element.isJsonObject()) continue;
                JsonObject artifact = element.getAsJsonObject();
                artifacts.add(new WhiteClientArtifact(
                        requireUri(artifact, "res_url"),
                        requireString(artifact, "res_name"),
                        stringValue(artifact, "res_md5", ""),
                        stringValue(artifact, "jar_md5", ""),
                        longValue(artifact, "res_size", 0),
                        stringValue(artifact, "mc_version_name", ""),
                        intValue(artifact, "java_version", 0)
                ));
            }
        }
        if (artifacts.isEmpty()) throw new GatewayProtocolException("白端组件没有可下载资源");
        return new WhiteClientComponent(
                requireString(component, "item_id"),
                stringValue(component, "entity_id", ""),
                intValue(component, "mtypeid", 0),
                artifacts
        );
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return "网易账号";
    }

    private static String randomAlphaNumeric(int length) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder result = new StringBuilder(length);
        for (int index = 0; index < length; index++) result.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        return result.toString();
    }

    private static String randomHex(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        StringBuilder result = new StringBuilder(bytes * 2);
        for (byte item : value) result.append(String.format("%02X", item & 0xFF));
        return result.toString();
    }

    private static String randomMac() {
        byte[] bytes = new byte[6];
        RANDOM.nextBytes(bytes);
        bytes[0] = (byte) ((bytes[0] & 0xFC) | 0x02);
        StringBuilder result = new StringBuilder(17);
        for (int index = 0; index < bytes.length; index++) {
            if (index > 0) result.append(':');
            result.append(String.format("%02X", bytes[index] & 0xFF));
        }
        return result.toString();
    }
}
