package cc.nimbus.features.alt.microsoft;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class MicrosoftAuth implements Closeable {
    private static final String CLIENT_ID = "67b74668-ef33-49c3-a75c-18cbb2481e0c";
    private static final String REDIRECT_URI = "http://localhost:9743/sad";
    private static final String SCOPE = "XboxLive.signin offline_access";
    private static final Gson GSON = new Gson();

    private final HttpServer server;
    private final AtomicBoolean closed = new AtomicBoolean();

    public MicrosoftAuth(Consumer<Data> callback, Consumer<String> statusCallback) throws IOException {
        Consumer<Data> success = Objects.requireNonNull(callback, "callback");
        Consumer<String> status = statusCallback == null ? ignored -> {} : statusCallback;
        String state = UUID.randomUUID().toString();

        server = HttpServer.create(new InetSocketAddress("localhost", 9743), 0);
        try {
            server.createContext("/sad", exchange -> handleCallback(exchange, state, success, status));
            server.start();
            openBrowser(authorizationUrl(state));
        } catch (Exception error) {
            close();
            throw new IOException("Unable to start Microsoft login", error);
        }
    }

    public static Data authenticate(String refreshToken, Consumer<String> statusCallback) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("Microsoft account does not contain a refresh token");
        }
        Consumer<String> status = statusCallback == null ? ignored -> {} : statusCallback;
        Tokens tokens = refreshMicrosoftToken(refreshToken, status);
        return authenticateMinecraft(tokens, status);
    }

    public static Profile fetchProfile(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("Minecraft access token is empty");
        }
        JsonObject profile = requestJson(
                "GET",
                "https://api.minecraftservices.com/minecraft/profile",
                null,
                Map.of("Authorization", "Bearer " + accessToken)
        );
        return profile(profile);
    }

    private void handleCallback(
            HttpExchange exchange,
            String expectedState,
            Consumer<Data> callback,
            Consumer<String> status
    ) {
        int responseCode = 200;
        String response = "Microsoft login successful. You can close this page.";
        try {
            Map<String, String> params = query(exchange.getRequestURI().getRawQuery());
            if (!expectedState.equals(params.get("state"))) {
                throw new IllegalStateException("Invalid OAuth state");
            }
            String code = params.get("code");
            if (code == null || code.isBlank()) {
                throw new IllegalStateException(params.getOrDefault(
                        "error_description",
                        "Microsoft did not return an authorization code"
                ));
            }

            Tokens tokens = exchangeAuthorizationCode(code, status);
            Data data = authenticateMinecraft(tokens, status);
            status.accept("Applying Minecraft session");
            callback.accept(data);
        } catch (Exception error) {
            responseCode = 400;
            response = "Microsoft login failed: " + message(error);
            status.accept(response);
        } finally {
            writeResponse(exchange, responseCode, response);
            close();
        }
    }

    private static Data authenticateMinecraft(Tokens tokens, Consumer<String> status) {
        status.accept("Getting Xbox Live token");
        String xboxLiveToken = xboxLiveToken(tokens.accessToken());
        status.accept("Getting XSTS token");
        Xsts xsts = xstsToken(xboxLiveToken);
        status.accept("Getting Minecraft access token");
        String accessToken = minecraftAccessToken(xsts);
        Profile profile = fetchProfile(accessToken);
        return new Data(profile.userName(), profile.uuid(), accessToken, tokens.refreshToken());
    }

    private static Tokens exchangeAuthorizationCode(String code, Consumer<String> status) {
        status.accept("Getting Microsoft access token");
        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_id", CLIENT_ID);
        form.put("code", code);
        form.put("grant_type", "authorization_code");
        form.put("redirect_uri", REDIRECT_URI);
        form.put("scope", SCOPE);
        JsonObject json = postForm("https://login.live.com/oauth20_token.srf", form);
        return tokens(json, null);
    }

    private static Tokens refreshMicrosoftToken(String refreshToken, Consumer<String> status) {
        status.accept("Refreshing Microsoft access token");
        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_id", CLIENT_ID);
        form.put("refresh_token", refreshToken);
        form.put("grant_type", "refresh_token");
        form.put("scope", SCOPE);
        JsonObject json = postForm("https://login.live.com/oauth20_token.srf", form);
        return tokens(json, refreshToken);
    }

    private static String xboxLiveToken(String microsoftToken) {
        JsonObject properties = new JsonObject();
        properties.addProperty("AuthMethod", "RPS");
        properties.addProperty("SiteName", "user.auth.xboxlive.com");
        properties.addProperty("RpsTicket", "d=" + microsoftToken);

        JsonObject body = new JsonObject();
        body.add("Properties", properties);
        body.addProperty("RelyingParty", "http://auth.xboxlive.com");
        body.addProperty("TokenType", "JWT");

        return required(requestJson(
                "POST",
                "https://user.auth.xboxlive.com/user/authenticate",
                GSON.toJson(body),
                Map.of("Content-Type", "application/json", "Accept", "application/json")
        ), "Token");
    }

    private static Xsts xstsToken(String xboxLiveToken) {
        JsonObject properties = new JsonObject();
        properties.addProperty("SandboxId", "RETAIL");
        properties.add("UserTokens", GSON.toJsonTree(new String[]{xboxLiveToken}));

        JsonObject body = new JsonObject();
        body.add("Properties", properties);
        body.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        body.addProperty("TokenType", "JWT");

        JsonObject json = requestJson(
                "POST",
                "https://xsts.auth.xboxlive.com/xsts/authorize",
                GSON.toJson(body),
                Map.of("Content-Type", "application/json", "Accept", "application/json")
        );
        String token = required(json, "Token");
        try {
            String userHash = json.getAsJsonObject("DisplayClaims")
                    .getAsJsonArray("xui")
                    .get(0)
                    .getAsJsonObject()
                    .get("uhs")
                    .getAsString();
            return new Xsts(token, userHash);
        } catch (RuntimeException error) {
            throw new IllegalStateException("XSTS response did not contain a user hash", error);
        }
    }

    private static String minecraftAccessToken(Xsts xsts) {
        JsonObject body = new JsonObject();
        body.addProperty("identityToken", "XBL3.0 x=" + xsts.userHash() + ";" + xsts.token());
        return required(requestJson(
                "POST",
                "https://api.minecraftservices.com/authentication/login_with_xbox",
                GSON.toJson(body),
                Map.of("Content-Type", "application/json", "Accept", "application/json")
        ), "access_token");
    }

    private static JsonObject postForm(String url, Map<String, String> values) {
        return requestJson(
                "POST",
                url,
                form(values),
                Map.of("Content-Type", "application/x-www-form-urlencoded", "Accept", "application/json")
        );
    }

    private static JsonObject requestJson(
            String method,
            String url,
            String body,
            Map<String, String> headers
    ) {
        String response = request(method, url, body, headers);
        try {
            JsonElement json = JsonParser.parseString(response);
            if (!json.isJsonObject()) throw new IllegalStateException("response is not a JSON object");
            return json.getAsJsonObject();
        } catch (RuntimeException error) {
            throw new IllegalStateException("Invalid JSON response from " + endpoint(url), error);
        }
    }

    private static String request(
            String method,
            String url,
            String body,
            Map<String, String> headers
    ) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(20_000);
            connection.setReadTimeout(20_000);
            connection.setDoInput(true);
            headers.forEach(connection::setRequestProperty);

            if (body != null) {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(bytes);
                }
            }

            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String response = stream == null ? "" : read(stream);
            if (status < 200 || status >= 300) {
                throw new IllegalStateException(
                        "HTTP " + status + " from " + endpoint(url) + ": " + errorMessage(response)
                );
            }
            return response;
        } catch (IOException error) {
            throw new IllegalStateException("Request failed for " + endpoint(url) + ": " + message(error), error);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String read(InputStream stream) throws IOException {
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Tokens tokens(JsonObject json, String fallbackRefreshToken) {
        String accessToken = required(json, "access_token");
        String refreshToken = optional(json, "refresh_token");
        if (refreshToken.isBlank()) refreshToken = fallbackRefreshToken;
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalStateException("Microsoft response did not contain a refresh token");
        }
        return new Tokens(accessToken, refreshToken);
    }

    private static Profile profile(JsonObject json) {
        return new Profile(required(json, "name"), required(json, "id"));
    }

    private static String required(JsonObject json, String key) {
        String value = optional(json, key);
        if (value.isBlank()) throw new IllegalStateException("Response did not contain " + key);
        return value;
    }

    private static String optional(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : "";
    }

    private static String form(Map<String, String> values) {
        return values.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(java.util.stream.Collectors.joining("&"));
    }

    private static String authorizationUrl(String state) {
        return "https://login.live.com/oauth20_authorize.srf?client_id=" + encode(CLIENT_ID)
                + "&redirect_uri=" + encode(REDIRECT_URI)
                + "&response_type=code&display=touch&scope=" + encode(SCOPE)
                + "&prompt=select_account&state=" + encode(state);
    }

    private static void openBrowser(String url) throws IOException {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        if (os.contains("win")) {
            new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
        } else if (os.contains("mac")) {
            new ProcessBuilder("open", url).start();
        } else {
            new ProcessBuilder("xdg-open", url).start();
        }
    }

    private static void writeResponse(HttpExchange exchange, int status, String response) {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        try {
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        } catch (IOException ignored) {
        } finally {
            exchange.close();
        }
    }

    private static Map<String, String> query(String rawQuery) {
        Map<String, String> result = new java.util.HashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) return result;
        for (String part : rawQuery.split("&")) {
            int separator = part.indexOf('=');
            if (separator <= 0) continue;
            result.put(
                    URLDecoder.decode(part.substring(0, separator), StandardCharsets.UTF_8),
                    URLDecoder.decode(part.substring(separator + 1), StandardCharsets.UTF_8)
            );
        }
        return result;
    }

    private static String errorMessage(String body) {
        if (body == null || body.isBlank()) return "empty error response";
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            for (String key : new String[]{"error_description", "message", "error"}) {
                String value = optional(json, key);
                if (!value.isBlank()) return value;
            }
        } catch (RuntimeException ignored) {
        }
        return body.length() <= 300 ? body : body.substring(0, 300) + "...";
    }

    private static String endpoint(String url) {
        try {
            URI parsed = URI.create(url);
            return parsed.getHost() + parsed.getPath();
        } catch (Exception ignored) {
            return url;
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String message(Throwable error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) server.stop(0);
    }

    private record Tokens(String accessToken, String refreshToken) {}
    private record Xsts(String token, String userHash) {}

    public record Profile(String userName, String uuid) {}

    public static final class Data {
        public final String userName;
        public final String uuid;
        public final String accessToken;
        public final String refreshToken;

        private Data(String userName, String uuid, String accessToken, String refreshToken) {
            this.userName = userName;
            this.uuid = uuid;
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
        }
    }
}
