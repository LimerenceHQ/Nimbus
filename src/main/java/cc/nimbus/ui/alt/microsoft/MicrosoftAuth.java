package cc.nimbus.ui.alt.microsoft;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Map;
import java.util.UUID;

public class MicrosoftAuth implements Closeable {
    private static final String CLIENT_ID = "67b74668-ef33-49c3-a75c-18cbb2481e0c";
    private static final String REDIRECT_URI = "http://localhost:9743/sad";
    private static final String SCOPE = "XboxLive.signin%20offline_access";

    private static final GsonBuilder gson = new GsonBuilder();
    private static final JsonParser parser = new JsonParser();

    private final Callback<String> statusCallback;
    private final Runnable closeRunnable;
    private final String state;

    private boolean closed = false;

    public MicrosoftAuth(Callback<Data> callback, Callback<String> statusCallback) throws IOException {
        this.statusCallback = statusCallback;
        this.state = UUID.randomUUID().toString();

        HttpServer redirectServer = HttpServer.create(new InetSocketAddress("localhost",9743),0);

        closeRunnable = () -> redirectServer.stop(0);

        redirectServer.createContext("/sad", exchange -> {
            Map<String, String> params = query(exchange.getRequestURI().getRawQuery());
            String response;
            int code = 200;
            try {
                if (!state.equals(params.get("state"))) throw new IOException("Invalid OAuth state");
                String authCode = params.get("code");
                if (authCode == null || authCode.isBlank()) throw new IOException(params.getOrDefault("error_description", "Missing authorization code"));
                String[] tokens = getMicrosoftTokenAndRefreshToken(authCode);
                String xBoxLiveToken = getXBoxLiveToken(tokens[0]);
                String[] xsts = getXSTSTokenAndUserHash(xBoxLiveToken);
                String accessToken = getAccessToken(xsts[0], xsts[1]);
                JsonObject profile = parser.parse(get("https://api.minecraftservices.com/minecraft/profile", Map.of("Authorization", "Bearer " + accessToken))).getAsJsonObject();
                Data data = new Data(profile.get("name").getAsString(), profile.get("id").getAsString(), accessToken, tokens[1]);
                callback.call(data);
                setStatus("登录完成");
                response = "Microsoft login successful. You can close this page.";
                close();
            } catch (Exception e) {
                code = 400; response = "Microsoft login failed: " + e.getMessage(); setStatus(response);
            }
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(code, body.length);
            try (java.io.OutputStream os = exchange.getResponseBody()) { os.write(body); }
            exchange.close();
        });

        redirectServer.start();

        String authorizationUrl = "https://login.live.com/oauth20_authorize.srf?client_id="
                + URLEncoder.encode(CLIENT_ID, StandardCharsets.UTF_8)
                + "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, StandardCharsets.UTF_8)
                + "&response_type=code&display=touch&scope=" + SCOPE
                + "&prompt=select_account&state=" + URLEncoder.encode(state, StandardCharsets.UTF_8);

        try {
            // 获取操作系统的名字
            String osName = System.getProperty("os.name", "");
            if (osName.startsWith("Mac OS")) {
                // 苹果的打开方式
                Class<?> fileMgr = Class.forName("com.apple.eio.FileManager");
                Method openURL = fileMgr.getDeclaredMethod("openURL",
                        String.class);
                openURL.invoke(null, authorizationUrl);
            } else if (osName.startsWith("Windows")) {
                // windows的打开方式。
                Runtime.getRuntime().exec("rundll32 url.dll,FileProtocolHandler " + authorizationUrl);
            } else {
                // Unix or Linux的打开方式
                String[] browsers = {"firefox", "opera", "konqueror", "epiphany",
                        "mozilla", "netscape"};
                String browser = null;
                for (int count = 0; count < browsers.length && browser == null; count++)
                    // 执行代码，在brower有值后跳出，
                    // 这里是如果进程创建成功了，==0是表示正常结束。
                    if (Runtime.getRuntime()
                            .exec(new String[]{"which", browsers[count]})
                            .waitFor() == 0)
                        browser = browsers[count];
                if (browser == null)
                    throw new Exception("Could not find web browser");
                else
                    // 这个值在上面已经成功的得到了一个进程。
                    Runtime.getRuntime().exec(new String[]{browser, authorizationUrl});
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public MicrosoftAuth(Callback<Data> callback, Callback<String> statusCallback, String refreshToken) {
        this.statusCallback = statusCallback;
        this.closeRunnable = null;
        this.state = "";

        String microsoftToken = getMicrosoftTokenFromRefreshToken(refreshToken);
        String xBoxLiveToken = getXBoxLiveToken(microsoftToken);
        String[] xstsTokenAndHash = getXSTSTokenAndUserHash(xBoxLiveToken);
        String accessToken = getAccessToken(xstsTokenAndHash[0], xstsTokenAndHash[1]);

        JsonObject jsonObject = parser.parse(
                get(
                        "https://api.minecraftservices.com/minecraft/profile",
                                Map.of("Authorization", "Bearer " + accessToken))
        ).getAsJsonObject();

        callback.call(
                new Data(
                        jsonObject.get("name").getAsString(),
                        jsonObject.get("id").getAsString(),
                        accessToken,
                        refreshToken
                )
        );

        setStatus("登录完成");
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }

        System.out.println("Close");

        if (closeRunnable != null) {
            closeRunnable.run();
        }
        closed = true;
    }

    private void setStatus(String status) {
        statusCallback.call(status);
    }

    private String getMicrosoftTokenFromRefreshToken(String refreshToken) {
        setStatus("Get microsoft token from refreshToken");

        JsonObject jsonObject = parser.parse(
                post(
                        "https://login.live.com/oauth20_token.srf",
                        "client_id=" + CLIENT_ID + "&refresh_token=" + refreshToken + "&grant_type=refresh_token",
                        Map.of("Content-Type", "application/x-www-form-urlencoded"))
        ).getAsJsonObject();

        return jsonObject.get("access_token").getAsString();
    }

    private String[] getMicrosoftTokenAndRefreshToken(String code) {
        setStatus("Get microsoft token and refreshToken");

        JsonObject jsonObject = parser.parse(
                post(
                        "https://login.live.com/oauth20_token.srf",
                        "client_id=" + CLIENT_ID + "&code=" + code + "&grant_type=authorization_code&redirect_uri=" + REDIRECT_URI + "&scope=" + SCOPE,
                        Map.of("Content-Type", "application/x-www-form-urlencoded"))
        ).getAsJsonObject();

        return new String[]{
                jsonObject.get("access_token").getAsString(),
                jsonObject.get("refresh_token").getAsString()
        };
    }

    private String getXBoxLiveToken(String microsoftToken) {
        setStatus("Get XBox live token");

        JsonObject paramObj = new JsonObject();
        JsonObject propertiesObj = new JsonObject();

        propertiesObj.addProperty("AuthMethod", "RPS");
        propertiesObj.addProperty("SiteName", "user.auth.xboxlive.com");
        propertiesObj.addProperty("RpsTicket", "d=" + microsoftToken);
        paramObj.add("Properties", propertiesObj);
        paramObj.addProperty("RelyingParty", "http://auth.xboxlive.com");
        paramObj.addProperty("TokenType", "JWT");

        JsonObject jsonObject = parser.parse(
                post(
                        "https://user.auth.xboxlive.com/user/authenticate",
                        gson.create().toJson(paramObj),
                        Map.of(
                                "Content-Type", "application/json",
                                "Accept", "application/json"
                        ))
        ).getAsJsonObject();

        return jsonObject.get("Token").getAsString();
    }

    private String[] getXSTSTokenAndUserHash(String xboxLiveToken) {
        setStatus("Get xsts token and user hash");

        JsonObject paramObj = new JsonObject();
        JsonObject propertiesObj = new JsonObject();

        propertiesObj.addProperty("SandboxId", "RETAIL");
        propertiesObj.add("UserTokens", parser.parse(gson.create().toJson(java.util.Arrays.asList(xboxLiveToken))));
        paramObj.add("Properties", propertiesObj);
        paramObj.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        paramObj.addProperty("TokenType", "JWT");

        JsonObject jsonObject = parser.parse(
                post("https://xsts.auth.xboxlive.com/xsts/authorize",
                        gson.create().toJson(paramObj),
                        Map.of("Content-Type", "application/json"))
        ).getAsJsonObject();

        return new String[]{
                jsonObject.get("Token").getAsString(),
                jsonObject.get("DisplayClaims").getAsJsonObject().get("xui").getAsJsonArray().get(0).getAsJsonObject().get("uhs").getAsString()
        };
    }

    private String getAccessToken(String xstsToken, String uhs) {
        setStatus("Get access token");

        JsonObject paramObj = new JsonObject();
        paramObj.addProperty("identityToken", "XBL3.0 x=" + uhs + ";" + xstsToken);

        JsonObject jsonObject = parser.parse(
                post("https://api.minecraftservices.com/authentication/login_with_xbox",
                        gson.create().toJson(paramObj),
                        Map.of("Content-Type", "application/json", "Accept", "application/json"))).getAsJsonObject();

        return jsonObject.get("access_token").getAsString();
    }

    private String post(String urlString, String param, java.util.Map<String,String> requestProperty) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            connection.setDoInput(true);
            connection.setDoOutput(true);

            connection.setConnectTimeout(20000);
            connection.setReadTimeout(20000);

            connection.setRequestMethod("POST");

            requestProperty.forEach(connection::setRequestProperty);

            connection.connect();

            connection.getOutputStream().write(param.getBytes(StandardCharsets.UTF_8));

            String readText;

            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                readText = reader.lines().collect(java.util.stream.Collectors.joining());
            }

            connection.disconnect();

            return readText;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String get(String urlString, java.util.Map<String,String> requestProperty) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            connection.setDoInput(true);
            connection.setRequestMethod("GET");

            connection.setConnectTimeout(20000);
            connection.setReadTimeout(20000);

            requestProperty.forEach(connection::setRequestProperty);

            connection.connect();

            String readText;

            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                readText = reader.lines().collect(java.util.stream.Collectors.joining());
            }

            connection.disconnect();

            return readText;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<String, String> query(String rawQuery) {
        Map<String, String> result = new java.util.HashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) return result;
        for (String part : rawQuery.split("&")) {
            int separator = part.indexOf('=');
            if (separator <= 0) continue;
            result.put(URLDecoder.decode(part.substring(0, separator), StandardCharsets.UTF_8),
                    URLDecoder.decode(part.substring(separator + 1), StandardCharsets.UTF_8));
        }
        return result;
    }

    public interface Callback<T> {
        void call(T obj);
    }

    public static class Data {
        public final String userName;
        public final String uuid;
        public final String accessToken;
        public final String refreshToken;

        public Data(String userName, String uuid, String accessToken, String refreshToken) {
            this.userName = userName;
            this.uuid = uuid;
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
        }
    }
}