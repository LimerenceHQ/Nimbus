package cc.nimbus.features.music.internal.net;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public final class NeteaseHttp implements AutoCloseable {
    private static final String WEB = "https://music.163.com";
    private static final String API = "https://interfacepc.music.163.com";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36";
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public Response request(String uri, Object data, Crypto crypto, String cookieString) {
        try {
            Map<String, String> cookies = parseCookies(cookieString);
            defaults(cookies);
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("User-Agent", USER_AGENT);

            String url;
            String body;
            if (crypto == Crypto.WEAPI) {
                headers.put("Referer", WEB);
                Map<String, Object> payload = map(data);
                payload.put("csrf_token", cookies.getOrDefault("__csrf", ""));
                NeteaseCrypto.Weapi encrypted = NeteaseCrypto.weapi(payload);
                url = WEB + "/weapi/" + uri.substring(5);
                body = form(Map.of("params", encrypted.params(), "encSecKey", encrypted.encSecKey()));
                headers.put("Cookie", cookieHeader(cookies));
            } else {
                Map<String, String> eapiHeader = eapiHeader(cookies);
                Map<String, Object> payload = map(data);
                payload.put("header", eapiHeader);
                url = API + "/eapi/" + uri.substring(5);
                body = form(Map.of("params", NeteaseCrypto.eapi(uri, payload)));
                headers.put("Cookie", cookieHeader(eapiHeader));
            }

            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            headers.forEach(request::header);
            HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonObject json = parseObject(response.body());
            return new Response(response.statusCode(), json, response.headers().allValues("set-cookie"));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Netease request interrupted", interrupted);
        } catch (Exception error) {
            throw new IllegalStateException("Netease request failed: " + message(error), error);
        }
    }

    private static Map<String, Object> map(Object data) {
        Map<String, Object> value = new HashMap<>();
        if (data instanceof Map<?, ?> source) source.forEach((key, item) -> value.put(String.valueOf(key), item));
        return value;
    }

    private static JsonObject parseObject(String value) {
        if (value == null || value.isBlank()) return new JsonObject();
        var parsed = JsonParser.parseString(value);
        if (parsed.isJsonObject()) return parsed.getAsJsonObject();
        JsonObject wrapper = new JsonObject();
        wrapper.add("data", parsed);
        return wrapper;
    }

    private static Map<String, String> parseCookies(String source) {
        Map<String, String> cookies = new LinkedHashMap<>();
        if (source == null || source.isBlank()) return cookies;
        for (String part : source.split(";")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length == 2 && !pair[0].isBlank()) cookies.put(pair[0], pair[1]);
        }
        return cookies;
    }

    private static void defaults(Map<String, String> cookies) {
        String id = random(32);
        cookies.putIfAbsent("__remember_me", "true");
        cookies.putIfAbsent("_ntes_nuid", id);
        cookies.putIfAbsent("_ntes_nnid", id + "," + System.currentTimeMillis());
        cookies.putIfAbsent("WNMCID", random(6) + "." + System.currentTimeMillis() + ".01.0");
        cookies.putIfAbsent("WEVNSM", "1.0.0");
        cookies.putIfAbsent("os", "pc");
        cookies.putIfAbsent("osver", "Microsoft-Windows-11-Professional-build-26100-64bit");
        cookies.putIfAbsent("appver", "3.1.12.204072");
        cookies.putIfAbsent("channel", "netease");
        cookies.putIfAbsent("deviceId", random(26).toUpperCase());
    }

    private static Map<String, String> eapiHeader(Map<String, String> cookies) {
        Map<String, String> header = new LinkedHashMap<>();
        List.of("osver", "deviceId", "os", "appver", "channel").forEach(key -> header.put(key, cookies.getOrDefault(key, "")));
        header.put("versioncode", cookies.getOrDefault("versioncode", "140"));
        header.put("mobilename", cookies.getOrDefault("mobilename", ""));
        header.put("buildver", String.valueOf(System.currentTimeMillis()).substring(0, 10));
        header.put("resolution", cookies.getOrDefault("resolution", "1920x1080"));
        header.put("__csrf", cookies.getOrDefault("__csrf", ""));
        header.put("requestId", System.currentTimeMillis() + "_" + String.format("%04d", ThreadLocalRandom.current().nextInt(1000)));
        if (cookies.containsKey("MUSIC_U")) header.put("MUSIC_U", cookies.get("MUSIC_U"));
        if (cookies.containsKey("MUSIC_A")) header.put("MUSIC_A", cookies.get("MUSIC_A"));
        return header;
    }

    private static String cookieHeader(Map<String, String> cookies) {
        return cookies.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue()).collect(Collectors.joining("; "));
    }
    private static String form(Map<String, String> values) {
        return values.entrySet().stream().map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue())).collect(Collectors.joining("&"));
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static String random(int length) {
        String alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder value = new StringBuilder(length);
        for (int index = 0; index < length; index++) value.append(alphabet.charAt(ThreadLocalRandom.current().nextInt(alphabet.length())));
        return value.toString();
    }
    private static String message(Throwable error) { return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(); }

    @Override public void close() { client.close(); }

    public enum Crypto { EAPI, WEAPI }
    public record Response(int status, JsonObject body, List<String> cookies) {
        public Response { cookies = List.copyOf(cookies == null ? List.of() : cookies); }
        public boolean successful() {
            if (status < 200 || status >= 300) return false;
            return !body.has("code") || body.get("code").getAsInt() == 200;
        }
    }
}
