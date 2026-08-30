package cc.nimbus.features.gateway.net;

import cc.nimbus.features.gateway.model.GatewayCaptcha;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** 4399 common/PC channel login used to obtain the X19 SAuth credential consumed by Nimbus Gateway. */
public final class Channel4399Service {
    private static final URI LOGIN = URI.create("https://ptlogin.4399.com");
    private static final URI SERVICE = URI.create("https://microgame.5054399.net");
    private static final URI COMMON_API = URI.create("https://m.4399api.com");
    private static final URI COMMON_CALLBACK = URI.create(
            "https://m.4399api.com/openapi/oauth-callback.html?gamekey=44770&game_key=115716"
    );
    private static final String APP_ID = "kid_wdsj";
    private static final String GAME_URL = "https://cdn.h5wan.4399sj.com/microterminal-h5-frame?game_id=500352";
    private static final String BIZ_ID = "2201001794";
    private static final String COMMON_SDK_VERSION = "3.18.0.682";
    private static final String COMMON_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/129.0.0.0 Safari/537.36";
    private static final int COMMON_MAX_RETRIES = 20;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final HttpClient captchaClient = newClient();
    private final HttpClient commonApiClient = newClient();

    public GatewayCaptcha requestCaptcha() throws GatewayProtocolException {
        // Nimbus Gateway's PE flow always creates a 32-character,
        // uppercase captcha id before obtaining the OAuth parameters.
        String sessionId = newCommonCaptchaId();
        URI uri = LOGIN.resolve("/ptlogin/captcha.do?captchaId=" + formEncode(sessionId));
        HttpRequest request = request(uri)
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", COMMON_USER_AGENT)
                .GET()
                .build();
        byte[] image = send(captchaClient, request, HttpResponse.BodyHandlers.ofByteArray()).body();
        if (image.length == 0) throw new GatewayProtocolException("4399 返回了空验证码图片");
        return new GatewayCaptcha(sessionId, image);
    }

    public GatewaySAuth login(
            String username,
            char[] password,
            String captchaSessionId,
            String captcha
    ) throws GatewayProtocolException {
        String normalizedUser = Objects.requireNonNull(username, "username").trim();
        if (normalizedUser.isEmpty()) throw new GatewayProtocolException("4399 用户名不能为空");
        if (password == null || password.length == 0) throw new GatewayProtocolException("4399 密码不能为空");
        String sessionId = Objects.requireNonNullElse(captchaSessionId, "").trim();
        String captchaText = Objects.requireNonNullElse(captcha, "").trim();
        if (sessionId.isEmpty() != captchaText.isEmpty()) {
            throw new GatewayProtocolException("4399 验证码 ID 和验证码必须同时提供");
        }
        if (sessionId.isEmpty()) {
            throw new GatewayProtocolException("4399 PE 登录必须先获取并填写验证码");
        }

        String passwordText = new String(password);
        return loginCommon(normalizedUser, passwordText, sessionId, captchaText);
    }

    private GatewaySAuth loginPc(String normalizedUser, String password, String sessionId, String captchaText)
            throws GatewayProtocolException {
        // Fantnel N4399 creates a fresh client for every password login.
        HttpClient loginClient = newClient();
        Map<String, String> parameters = loginParameters();
        parameters.put("username", normalizedUser);
        parameters.put("password", password);
        if (!sessionId.isEmpty()) {
            parameters.put("sessionId", sessionId);
            parameters.put("inputCaptcha", captchaText);
        }

        if (sessionId.isEmpty()) {
            String verification = postForm(loginClient, LOGIN.resolve("/ptlogin/loginFrame.do?v=1"), parameters);
            if (verification.contains("账号异常，请输入验证码")) {
                throw new GatewayProtocolException("4399 登录需要验证码，请获取验证码后重试");
            }
        }

        String loginBody = postForm(loginClient, LOGIN.resolve("/ptlogin/login.do?v=1"), parameters);
        String loginError = extractLoginError(loginBody);
        if (!loginError.isBlank()) throw new GatewayProtocolException("4399 登录失败: " + loginError);
        return generateSAuth(loginClient, normalizedUser);
    }

    private GatewaySAuth loginCommon(String username, String password, String sessionId, String captcha)
            throws GatewayProtocolException {
        CommonOAuthParameters oauth = loadCommonOAuthParameters();
        Map<String, String> parameters = commonLoginParameters(
                oauth.clientId(), oauth.state(), oauth.ref(), username, password, captcha, sessionId
        );

        // Open the complete OAuth URL first and reuse
        // the resulting cookies for loginAndAuthorize. This is the material step
        // missing from the previous Nimbus implementation.
        HttpClient loginClient = newClient(HttpClient.Redirect.NEVER);
        HttpRequest warmup = commonRequest(oauth.oauthUrl()).GET().build();
        send(loginClient, warmup, HttpResponse.BodyHandlers.discarding());

        URI endpoint = LOGIN.resolve(
                "/oauth2/loginAndAuthorize.do?channel=&sdk=op&sdk_version=" + COMMON_SDK_VERSION
        );
        HttpResponse<String> loginResponse = null;
        for (int attempt = 0; attempt < COMMON_MAX_RETRIES; attempt++) {
            loginResponse = postCommonLogin(loginClient, endpoint, parameters);
            if (loginResponse.statusCode() != 202) break;
            if (attempt + 1 < COMMON_MAX_RETRIES) waitBeforeCommonRetry();
        }
        if (loginResponse == null) throw new GatewayProtocolException("4399 PE 登录没有返回结果");
        String loginBody = loginResponse.body();
        if (loginResponse.statusCode() == 202) {
            throw new GatewayProtocolException("4399 服务持续返回 HTTP 202，请稍后重新获取验证码再试");
        }
        if (loginBody.contains("验证码错误")) {
            throw new GatewayProtocolException("4399 验证码错误，请重新获取验证码后再试");
        }

        String location = loginResponse.headers().firstValue("Location").orElse("");
        if (location.isBlank()) {
            String htmlError = extractCommonLoginError(loginBody);
            if (htmlError.isBlank()) htmlError = summarizeHtml(loginBody);
            throw new GatewayProtocolException(
                    "4399 通用渠道登录未返回授权地址" + (htmlError.isBlank() ? "" : ": " + htmlError)
            );
        }

        URI authorizationUri;
        try {
            authorizationUri = loginResponse.uri().resolve(location);
        } catch (IllegalArgumentException error) {
            throw new GatewayProtocolException("4399 通用渠道返回了无效授权地址", error);
        }
        HttpClient authorizationClient = newClient(HttpClient.Redirect.NEVER);
        HttpRequest authorizationRequest = commonRequest(authorizationUri).GET().build();
        String authorizationBody = send(
                authorizationClient,
                authorizationRequest,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        ).body();
        JsonObject response = parseObject(authorizationBody, "4399 通用渠道授权");
        if (!"100".equals(string(response, "code", ""))) {
            throw new GatewayProtocolException(
                    "4399 通用渠道登录失败: " + string(response, "message", "未知错误")
            );
        }
        JsonObject result = object(response, "result", "4399 通用渠道授权");
        String uid = requiredString(result, "uid", "4399 通用渠道授权");
        String sessionState = requiredString(result, "state", "4399 通用渠道授权");
        return GatewaySAuth.channel4399Common(username, uid, sessionState);
    }

    private CommonOAuthParameters loadCommonOAuthParameters() throws GatewayProtocolException {
        HttpRequest request = commonRequest(COMMON_CALLBACK).GET().build();
        String body = send(
                commonApiClient,
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        ).body();
        JsonObject response = parseObject(body, "4399 PE OAuth 参数");
        if (!response.has("result") || response.get("result").isJsonNull()) {
            throw new GatewayProtocolException("4399 PE OAuth 参数缺少 result");
        }
        String oauthUrl;
        try {
            if (response.get("result").isJsonPrimitive()) {
                oauthUrl = response.get("result").getAsString();
            } else {
                oauthUrl = requiredString(response.getAsJsonObject("result"), "login_url", "4399 PE OAuth 参数");
            }
        } catch (RuntimeException error) {
            throw new GatewayProtocolException("4399 PE OAuth 参数返回了无效 result", error);
        }
        try {
            URI uri = URI.create(oauthUrl);
            Map<String, String> query = parseForm(uri.getRawQuery());
            return new CommonOAuthParameters(
                    uri,
                    required(query, "client_id"),
                    required(query, "state"),
                    Objects.requireNonNullElse(query.get("ref"), "")
            );
        } catch (IllegalArgumentException error) {
            throw new GatewayProtocolException("4399 PE OAuth 参数返回了无效地址", error);
        }
    }

    static Map<String, String> commonLoginParameters(
            String clientId,
            String state,
            String ref,
            String username,
            String password,
            String captcha,
            String captchaId
    ) {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("auth_action", "ORILOGIN");
        parameters.put("bizId", "2100001792");
        parameters.put("captcha", captcha);
        parameters.put("captcha_id", captchaId);
        parameters.put("client_id", clientId);
        parameters.put("isInputRealname", "false");
        // This spelling is part of the 4399 PE wire contract.
        parameters.put("isVaildRealname", "false");
        parameters.put("password", password);
        parameters.put("redirect_uri", COMMON_CALLBACK.toString());
        parameters.put("ref", ref);
        parameters.put("response_type", "TOKEN");
        parameters.put("scope", "basic");
        parameters.put("sec", "0");
        parameters.put("state", state);
        parameters.put("username", username);
        return parameters;
    }

    private GatewaySAuth generateSAuth(HttpClient client, String fallbackUser) throws GatewayProtocolException {
        long timestamp = Instant.now().getEpochSecond();
        Map<String, String> query = new LinkedHashMap<>();
        query.put("appId", APP_ID);
        query.put("gameUrl", GAME_URL);
        query.put("isCrossDomain", "1");
        query.put("nick", "null");
        query.put("onLineStart", "false");
        query.put("ptLogin", "true");
        query.put("rand_time", "$randTime");
        query.put("retUrl", redirectUrl(timestamp));
        query.put("show", "1");

        URI check = URI.create(LOGIN + "/ptlogin/checkKidLoginUserCookie.do?" + form(query));
        HttpRequest request = request(check).timeout(Duration.ofSeconds(30)).GET().build();
        HttpResponse<String> response = sendAllowingRedirectStop(
                client,
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        URI authenticationRedirect = redirectTarget(
                response.uri(),
                response.statusCode(),
                response.headers().firstValue("Location").orElse("")
        );
        String finalQuery = authenticationRedirect.getRawQuery();
        if (finalQuery == null || finalQuery.isBlank()) {
            throw new GatewayProtocolException("4399 未返回统一认证参数");
        }
        Map<String, String> redirect = parseForm(finalQuery);
        if (!redirect.containsKey("sig") || !redirect.containsKey("uid") || !redirect.containsKey("time")) {
            throw new GatewayProtocolException(
                    "4399 登录未建立有效会话（重定向缺少 sig/uid/time，账号密码或验证码可能未通过）"
            );
        }

        String callback = "jQuery1830" + randomDigits(16) + "_" + timestamp;
        Map<String, String> serviceQuery = new LinkedHashMap<>();
        serviceQuery.put("callback", callback);
        serviceQuery.put("queryStr", finalQuery);
        URI serviceUri = URI.create(SERVICE + "/v2/service/sdk/info?" + form(serviceQuery));
        HttpRequest serviceRequest = request(serviceUri).timeout(Duration.ofSeconds(30)).GET().build();
        String jsonp = send(client, serviceRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
        JsonObject envelope = parseJsonp(jsonp, callback);
        if (intValue(envelope, "code", 0) != 0) {
            throw new GatewayProtocolException("4399 统一认证失败: " + string(envelope, "msg", "未知错误"));
        }
        JsonObject data = object(envelope, "data", "4399 统一认证");
        Map<String, String> sdk = parseForm(string(data, "sdk_login_data", ""));
        return GatewaySAuth.channel4399(
                firstNonBlank(sdk.get("username"), string(data, "username", ""), fallbackUser),
                required(sdk, "uid"),
                required(sdk, "token"),
                required(sdk, "time")
        );
    }

    static JsonObject parseJsonp(String content, String callback) throws GatewayProtocolException {
        String value = Objects.requireNonNullElse(content, "").trim();
        String prefix = callback + "(";
        if (!value.startsWith(prefix) || !value.endsWith(")")) {
            throw new GatewayProtocolException("4399 统一认证返回了无效 JSONP");
        }
        try {
            return JsonParser.parseString(value.substring(prefix.length(), value.length() - 1)).getAsJsonObject();
        } catch (RuntimeException error) {
            throw new GatewayProtocolException("4399 统一认证返回了无效 JSON", error);
        }
    }

    static Map<String, String> parseForm(String value) throws GatewayProtocolException {
        if (value == null || value.isBlank()) throw new GatewayProtocolException("4399 统一认证缺少 sdk_login_data");
        Map<String, String> result = new LinkedHashMap<>();
        for (String item : value.split("&")) {
            int separator = item.indexOf('=');
            if (separator < 0) continue;
            result.put(
                    URLDecoder.decode(item.substring(0, separator), StandardCharsets.UTF_8),
                    URLDecoder.decode(item.substring(separator + 1), StandardCharsets.UTF_8)
            );
        }
        return result;
    }

    static String extractLoginError(String html) {
        String value = Objects.requireNonNullElse(html, "");
        String marker = "login_err_tip\">";
        // Fantnel N4399 treats a non-HTML response as the actual endpoint error.
        // Without this branch Nimbus continued to the cookie check and obscured it
        // behind a misleading "missing sig/uid/time" message.
        if (!value.contains(marker) && !value.toLowerCase(java.util.Locale.ROOT).contains("<html")) {
            return value.trim();
        }
        int start = value.indexOf(marker);
        if (start < 0) return "";
        start += marker.length();
        int end = value.indexOf("</div>", start);
        if (end < 0) return "";
        return value.substring(start, end).replaceAll("<[^>]+>", "").trim();
    }

    static String extractCommonLoginError(String html) {
        String value = Objects.requireNonNullElse(html, "");
        String marker = "login_err_msg\">";
        int start = value.indexOf(marker);
        if (start < 0) return "";
        start += marker.length();
        int end = value.indexOf("</p>", start);
        if (end < 0) return "";
        return value.substring(start, end).replaceAll("<[^>]+>", "").trim();
    }

    private static String postForm(HttpClient client, URI uri, Map<String, String> parameters)
            throws GatewayProtocolException {
        return postForm(client, uri, parameters, "http://ptlogin.4399.com/ptlogin/loginFrame.do");
    }

    private static String postForm(
            HttpClient client,
            URI uri,
            Map<String, String> parameters,
            String referer
    ) throws GatewayProtocolException {
        return postForm(client, uri, parameters, referer, false);
    }

    private static String postForm(
            HttpClient client,
            URI uri,
            Map<String, String> parameters,
            String referer,
            boolean includeEmpty
    ) throws GatewayProtocolException {
        return postFormResponse(client, uri, parameters, includeEmpty, referer).body();
    }

    private static HttpResponse<String> postFormResponse(
            HttpClient client,
            URI uri,
            Map<String, String> parameters,
            boolean includeEmpty
    ) throws GatewayProtocolException {
        return postFormResponse(client, uri, parameters, includeEmpty, "");
    }

    private static HttpResponse<String> postFormResponse(
            HttpClient client,
            URI uri,
            Map<String, String> parameters,
            boolean includeEmpty,
            String referer
    ) throws GatewayProtocolException {
        HttpRequest.Builder builder = request(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(
                        includeEmpty ? formIncludingEmpty(parameters) : form(parameters),
                        StandardCharsets.UTF_8
                ));
        if (referer != null && !referer.isBlank()) builder.header("Referer", referer);
        return sendAllowingRedirectStop(
                client,
                builder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
    }

    private static HttpResponse<String> postCommonLogin(
            HttpClient client,
            URI uri,
            Map<String, String> parameters
    ) throws GatewayProtocolException {
        HttpRequest request = commonRequest(uri)
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(commonForm(parameters), StandardCharsets.UTF_8))
                .build();
        return sendAllowingRedirectStop(
                client,
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
    }

    private static void waitBeforeCommonRetry() throws GatewayProtocolException {
        try {
            Thread.sleep(1_000L);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new GatewayProtocolException("4399 登录已取消", interrupted);
        }
    }

    private static HttpClient newClient() {
        return newClient(HttpClient.Redirect.NORMAL);
    }

    private static HttpClient newClient(HttpClient.Redirect redirects) {
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        return HttpClient.newBuilder()
                .cookieHandler(cookies)
                .connectTimeout(Duration.ofSeconds(15))
                // .NET HttpClient requests used by both NEL implementations default
                // to HTTP/1.1, so avoid transport-level differences in ptlogin.
                .version(HttpClient.Version.HTTP_1_1)
                // .NET HttpClientHandler used by NEL follows ordinary redirects but
                // stops an HTTPS -> HTTP downgrade. The stopped Location carries the
                // sig/uid/time values required by the next 4399 request.
                .followRedirects(redirects)
                .build();
    }

    private static HttpRequest.Builder request(URI uri) {
        // Current NEL Com4399/Pc4399 HttpWrapper does not configure User-Agent.
        return HttpRequest.newBuilder(uri);
    }

    private static HttpRequest.Builder commonRequest(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", COMMON_USER_AGENT);
    }

    private static <T> HttpResponse<T> sendAllowingRedirectStop(
            HttpClient client,
            HttpRequest request,
            HttpResponse.BodyHandler<T> handler
    ) throws GatewayProtocolException {
        try {
            HttpResponse<T> response = client.send(request, handler);
            if ((response.statusCode() >= 200 && response.statusCode() < 300)
                    || isRedirect(response.statusCode())) {
                return response;
            }
            throw new GatewayProtocolException(
                    "4399 请求失败: " + request.uri().getPath() + " (HTTP " + response.statusCode() + ")"
            );
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new GatewayProtocolException("4399 登录已取消", interrupted);
        } catch (IOException error) {
            throw new GatewayProtocolException("无法连接 4399 登录服务", error);
        }
    }

    static URI redirectTarget(URI responseUri, int statusCode, String location) throws GatewayProtocolException {
        if (!isRedirect(statusCode)) return responseUri;
        if (location == null || location.isBlank()) {
            throw new GatewayProtocolException("4399 登录重定向缺少 Location");
        }
        try {
            return responseUri.resolve(location);
        } catch (IllegalArgumentException error) {
            throw new GatewayProtocolException("4399 登录返回了无效重定向地址", error);
        }
    }

    private static boolean isRedirect(int statusCode) {
        return statusCode == 301 || statusCode == 302 || statusCode == 303
                || statusCode == 307 || statusCode == 308;
    }

    private static <T> HttpResponse<T> send(
            HttpClient client,
            HttpRequest request,
            HttpResponse.BodyHandler<T> handler
    ) throws GatewayProtocolException {
        try {
            HttpResponse<T> response = client.send(request, handler);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String location = response.headers().firstValue("Location").orElse("");
                throw new GatewayProtocolException(
                        "4399 请求失败: " + request.uri().getPath() + " (HTTP " + response.statusCode() + ")"
                                + (location.isBlank() ? "" : " -> " + location)
                );
            }
            return response;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new GatewayProtocolException("4399 登录已取消", interrupted);
        } catch (IOException error) {
            throw new GatewayProtocolException("无法连接 4399 登录服务", error);
        }
    }

    private static Map<String, String> loginParameters() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("appId", APP_ID);
        result.put("autoLogin", "on");
        result.put("bizId", BIZ_ID);
        result.put("css", "https://microgame.5054399.net/v2/resource/cssSdk/default/login.css");
        result.put("displayMode", "popup");
        result.put("externalLogin", "qq");
        result.put("gameId", "wd");
        result.put("iframeId", "popup_login_frame");
        result.put("includeFcmInfo", "false");
        result.put("layout", "vertical");
        result.put("layoutSelfAdapting", "true");
        result.put("level", "8");
        result.put("loginFrom", "uframe");
        result.put("mainDivId", "popup_login_div");
        result.put("postLoginHandler", "default");
        result.put("redirectUrl", "");
        result.put("regLevel", "8");
        result.put("sec", "1");
        result.put("sessionId", "");
        result.put("userNameLabel", "4399用户名");
        result.put("userNameTip", "请输入4399用户名");
        result.put("welcomeTip", "欢迎回到4399");
        return result;
    }

    static String newCaptchaId() {
        // Current EastSideNEL/Pc4399 uses GUID-N plus eight more GUID-N chars.
        return UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    static String newCommonCaptchaId() {
        return UUID.randomUUID().toString().replace("-", "").toUpperCase(java.util.Locale.ROOT);
    }

    private static String redirectUrl(long timestamp) {
        return "https://ptlogin.4399.com/resource/ucenter.html?action=login&appId=" + APP_ID
                + "&loginLevel=8&regLevel=8&bizId=" + BIZ_ID
                + "&externalLogin=qq&qrLogin=true&layout=vertical&level=101"
                + "&css=https://microgame.5054399.net/v2/resource/cssSdk/default/login.css"
                + "&v=2018_11_26_16&postLoginHandler=redirect&checkLoginUserCookie=true"
                + "&redirectUrl=http%3A%2F%2Fcdn.h5wan.4399sj.com%2Fmicroterminal-h5-frame%3Fgame_id%3D500352%26rand_time%3D"
                + timestamp;
    }

    private static String form(Map<String, String> values) {
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            // Codexus QueryBuilder.BuildQueryString() omits empty values.
            if (entry.getValue() == null || entry.getValue().isEmpty()) continue;
            if (!result.isEmpty()) result.append('&');
            result.append(formEncode(entry.getKey())).append('=').append(formEncode(entry.getValue()));
        }
        return result.toString();
    }

    static String formIncludingEmpty(Map<String, String> values) {
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!result.isEmpty()) result.append('&');
            result.append(formEncode(entry.getKey())).append('=');
            if (entry.getValue() != null) result.append(formEncode(entry.getValue()));
        }
        return result.toString();
    }

    static String commonForm(Map<String, String> values) {
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!result.isEmpty()) result.append('&');
            result.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)).append('=');
            if (entry.getValue() != null) {
                result.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            }
        }
        return result.toString();
    }

    static String formEncode(String value) {
        // Match Uri.EscapeDataString used by NEL instead of HTML form '+' encoding.
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
    }

    private static JsonObject object(JsonObject parent, String name, String operation) throws GatewayProtocolException {
        if (!parent.has(name) || !parent.get(name).isJsonObject()) {
            throw new GatewayProtocolException(operation + " 缺少 " + name);
        }
        return parent.getAsJsonObject(name);
    }

    private static JsonObject parseObject(String content, String operation) throws GatewayProtocolException {
        try {
            return JsonParser.parseString(Objects.requireNonNullElse(content, "")).getAsJsonObject();
        } catch (RuntimeException error) {
            throw new GatewayProtocolException(operation + " 返回了无效 JSON", error);
        }
    }

    private static String requiredString(JsonObject object, String name, String operation)
            throws GatewayProtocolException {
        String value = string(object, name, "");
        if (value.isBlank()) throw new GatewayProtocolException(operation + " 缺少 " + name);
        return value;
    }

    private static String required(Map<String, String> values, String name) throws GatewayProtocolException {
        String value = values.get(name);
        if (value == null || value.isBlank()) throw new GatewayProtocolException("4399 统一认证缺少 " + name);
        return value;
    }

    private static String string(JsonObject value, String name, String fallback) {
        if (!value.has(name) || value.get(name).isJsonNull()) return fallback;
        try {
            return value.get(name).getAsString();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static int intValue(JsonObject value, String name, int fallback) {
        if (!value.has(name) || value.get(name).isJsonNull()) return fallback;
        try {
            return value.get(name).getAsInt();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return "4399";
    }

    private static String concise(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    private static String summarizeHtml(String html) {
        String value = Objects.requireNonNullElse(html, "")
                .replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?s)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replaceAll("\\s+", " ")
                .trim();
        return value.length() <= 200 ? value : value.substring(0, 200) + "…";
    }

    private static String randomDigits(int length) {
        StringBuilder result = new StringBuilder(length);
        for (int index = 0; index < length; index++) result.append((char) ('0' + RANDOM.nextInt(10)));
        return result.toString();
    }

    private record CommonOAuthParameters(URI oauthUrl, String clientId, String state, String ref) {}
}
