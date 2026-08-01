package cc.nimbus.features.music.provider;

import cc.nimbus.features.music.Quality;
import cc.nimbus.features.music.internal.net.NeteaseHttp;
import cc.nimbus.features.music.lyrics.Lyrics;
import cc.nimbus.features.music.model.Music;
import cc.nimbus.features.music.model.Playlist;
import cc.nimbus.features.music.model.User;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class NeteaseApi implements AutoCloseable {
    private static final Gson GSON = new Gson();
    private final NeteaseHttp http;
    private volatile String cookie = "";

    public NeteaseApi(NeteaseHttp http) { this.http = http; }
    public void cookie(String value) { cookie = value == null ? "" : value.trim(); }
    public String cookie() { return cookie; }

    public User profile() {
        JsonObject body = request("/api/w/nuser/account/get", Map.of(), NeteaseHttp.Crypto.WEAPI).body();
        JsonObject wrapped = object(body, "data");
        if (wrapped != null) body = wrapped;
        JsonObject profile = object(body, "profile");
        return profile == null ? null : GSON.fromJson(profile, User.class);
    }

    public List<Music> searchSongs(String keyword, int limit) {
        return mapArray(search(keyword, 1, limit), "songs", Music.class);
    }
    public List<Playlist> searchPlaylists(String keyword, int limit) {
        return mapArray(search(keyword, 1000, limit), "playlists", Playlist.class);
    }
    private JsonObject search(String keyword, int type, int limit) {
        Map<String, Object> data = new HashMap<>();
        data.put("s", keyword);
        data.put("type", type);
        data.put("limit", Math.max(1, Math.min(100, limit)));
        data.put("offset", 0);
        data.put("total", true);
        JsonObject result = object(request("/api/cloudsearch/pc", data, NeteaseHttp.Crypto.EAPI).body(), "result");
        return result == null ? new JsonObject() : result;
    }

    public Music song(long id) {
        List<Music> songs = songs(List.of(id));
        return songs.isEmpty() ? null : songs.getFirst();
    }
    public List<Music> songs(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        List<Music> result = new ArrayList<>();
        for (int start = 0; start < ids.size(); start += 500) {
            List<Long> page = ids.subList(start, Math.min(ids.size(), start + 500));
            JsonArray entries = new JsonArray(page.size());
            for (long id : page) {
                JsonObject entry = new JsonObject();
                entry.addProperty("id", id);
                entries.add(entry);
            }
            JsonObject body = request("/api/v3/song/detail", Map.of("c", GSON.toJson(entries)), NeteaseHttp.Crypto.WEAPI).body();
            result.addAll(mapArray(body, "songs", Music.class));
        }
        return List.copyOf(result);
    }

    public Playlist playlist(long id) {
        JsonObject value = object(request("/api/v6/playlist/detail", playlistDetail(id), NeteaseHttp.Crypto.EAPI).body(), "playlist");
        return value == null ? null : GSON.fromJson(value, Playlist.class);
    }
    public List<Music> playlistSongs(long id) {
        JsonObject playlist = object(request("/api/v6/playlist/detail", playlistDetail(id), NeteaseHttp.Crypto.EAPI).body(), "playlist");
        if (playlist == null) return List.of();
        JsonArray trackIds = array(playlist, "trackIds");
        if (trackIds == null) return List.of();
        List<Long> ids = new ArrayList<>(trackIds.size());
        for (JsonElement element : trackIds) {
            if (element.isJsonObject() && element.getAsJsonObject().has("id")) ids.add(element.getAsJsonObject().get("id").getAsLong());
        }
        return songs(ids);
    }
    public List<Playlist> userPlaylists(long userId, int limit) {
        JsonObject body = request("/api/user/playlist", Map.of("uid", userId, "limit", limit, "offset", 0, "includeVideo", true), NeteaseHttp.Crypto.WEAPI).body();
        return mapArray(body, "playlist", Playlist.class);
    }

    public List<Long> likedIds(long userId) {
        JsonArray ids = array(request("/api/song/like/get", Map.of("uid", userId), NeteaseHttp.Crypto.EAPI).body(), "ids");
        if (ids == null) return List.of();
        List<Long> result = new ArrayList<>(ids.size());
        ids.forEach(value -> result.add(value.getAsLong()));
        return List.copyOf(result);
    }
    public boolean like(long id, boolean liked) {
        return request("/api/radio/like", Map.of("alg", "itembased", "trackId", id, "like", liked, "time", "3"), NeteaseHttp.Crypto.WEAPI).successful();
    }
    public boolean editPlaylist(long playlistId, long musicId, boolean add) {
        Map<String, Object> data = Map.of("op", add ? "add" : "del", "pid", playlistId,
                "trackIds", "[\"" + musicId + "\"]", "imme", "true");
        NeteaseHttp.Response response = request("/api/playlist/manipulate/tracks", data, NeteaseHttp.Crypto.EAPI);
        if (response.body().has("code") && response.body().get("code").getAsInt() == 512) {
            data = Map.of("op", add ? "add" : "del", "pid", playlistId,
                    "trackIds", "[\"" + musicId + "\",\"" + musicId + "\"]", "imme", "true");
            response = request("/api/playlist/manipulate/tracks", data, NeteaseHttp.Crypto.EAPI);
        }
        return response.successful();
    }

    public List<Playlist> dailyRecommendedPlaylists() {
        return mapArray(request("/api/v1/discovery/recommend/resource", Map.of(), NeteaseHttp.Crypto.WEAPI).body(), "recommend", Playlist.class);
    }
    public List<Music> dailyRecommendedSongs() {
        JsonObject data = object(request("/api/v3/discovery/recommend/songs", Map.of(), NeteaseHttp.Crypto.WEAPI).body(), "data");
        return data == null ? List.of() : mapArray(data, "dailySongs", Music.class);
    }
    public List<Music> intelligentSongs(long songId, long playlistId, long startMusicId, int count) {
        Map<String, Object> data = Map.of(
                "songId", songId,
                "type", "fromPlayOne",
                "playlistId", playlistId,
                "startMusicId", startMusicId,
                "count", Math.max(1, Math.min(100, count))
        );
        JsonArray values = array(request("/api/playmode/intelligence/list", data, NeteaseHttp.Crypto.EAPI).body(), "data");
        if (values == null) return List.of();
        Map<Long, Music> songs = new LinkedHashMap<>();
        for (JsonElement value : values) {
            if (!value.isJsonObject()) continue;
            JsonObject item = value.getAsJsonObject();
            JsonElement song = item.has("songInfo") ? item.get("songInfo") : item;
            if (!song.isJsonObject()) continue;
            Music music = GSON.fromJson(song, Music.class);
            if (music.id() > 0L) songs.putIfAbsent(music.id(), music);
        }
        return List.copyOf(songs.values());
    }

    public Lyrics lyrics(long id) {
        Map<String, Object> data = Map.of("id", id, "cp", false, "tv", 0, "lv", 0, "rv", 0,
                "kv", 0, "yv", 0, "ytv", 0, "yrv", 0);
        JsonObject body = request("/api/song/lyric/v1", data, NeteaseHttp.Crypto.EAPI).body();
        return new Lyrics(lyric(body, "lrc"), lyric(body, "yrc"), lyric(body, "tlyric"), lyric(body, "romalrc"));
    }

    public AudioUrl audioUrl(long id, Quality quality) {
        Map<String, Object> data = new HashMap<>();
        data.put("ids", "[" + id + "]");
        data.put("level", quality.apiValue());
        data.put("encodeType", "flac");
        if (quality == Quality.SKY) data.put("immerseType", "c51");
        JsonArray values = array(request("/api/song/enhance/player/url/v1", data, NeteaseHttp.Crypto.EAPI).body(), "data");
        if (values == null || values.isEmpty()) return null;
        JsonObject value = values.get(0).getAsJsonObject();
        if (!value.has("url") || value.get("url").isJsonNull()) return null;
        int code = value.has("code") ? value.get("code").getAsInt() : 200;
        if (code != 200) return null;
        return new AudioUrl(value.get("url").getAsString(), string(value, "type"), number(value, "size"), number(value, "time"));
    }

    public QrLogin createQrLogin() {
        NeteaseHttp.Response response = request("/api/login/qrcode/unikey", Map.of("type", 3), NeteaseHttp.Crypto.EAPI);
        JsonObject data = object(response.body(), "data");
        if (data == null) data = response.body();
        if (data == null || !data.has("unikey")) return null;
        String key = data.get("unikey").getAsString();
        return new QrLogin(key, "https://music.163.com/login?codekey=" + key);
    }
    public QrStatus checkQrLogin(String key) {
        NeteaseHttp.Response response = request("/api/login/qrcode/client/login", Map.of("key", key, "type", 3), NeteaseHttp.Crypto.EAPI);
        int code = response.body().has("code") ? response.body().get("code").getAsInt() : -1;
        return new QrStatus(code, response.cookie());
    }

    public SmsStatus sendSms(String phone, String countryCode) {
        Map<String, Object> data = Map.of(
                "cellphone", phone,
                "ctcode", countryCode,
                "secrete", "music_middleuser_pclogin"
        );
        JsonObject body = request("/api/sms/captcha/sent", data, NeteaseHttp.Crypto.WEAPI).body();
        return new SmsStatus(code(body), message(body));
    }
    public SmsLogin loginWithSms(String phone, String countryCode, String captcha) {
        Map<String, Object> data = Map.of(
                "type", "1",
                "https", "true",
                "phone", phone,
                "countrycode", countryCode,
                "captcha", captcha,
                "remember", "true",
                "secureCaptcha", ""
        );
        NeteaseHttp.Response response = request("/api/w/login/cellphone", data, NeteaseHttp.Crypto.WEAPI);
        return new SmsLogin(code(response.body()), message(response.body()), response.cookie());
    }

    private NeteaseHttp.Response request(String uri, Object data, NeteaseHttp.Crypto crypto) {
        return http.request(uri, data, crypto, cookie);
    }
    private static String lyric(JsonObject body, String key) {
        JsonObject value = object(body, key);
        return value == null || !value.has("lyric") || value.get("lyric").isJsonNull() ? "" : value.get("lyric").getAsString();
    }
    private static Map<String, Object> playlistDetail(long id) { return Map.of("id", id, "n", 100000, "s", 8); }
    private static int code(JsonObject body) {
        return body != null && body.has("code") && !body.get("code").isJsonNull() ? body.get("code").getAsInt() : -1;
    }
    private static String message(JsonObject body) {
        if (body == null) return "";
        for (String key : List.of("message", "msg")) {
            if (body.has(key) && !body.get(key).isJsonNull()) return body.get(key).getAsString();
        }
        return "";
    }
    private static JsonObject object(JsonObject source, String key) {
        return source != null && source.has(key) && source.get(key).isJsonObject() ? source.getAsJsonObject(key) : null;
    }
    private static JsonArray array(JsonObject source, String key) {
        return source != null && source.has(key) && source.get(key).isJsonArray() ? source.getAsJsonArray(key) : null;
    }
    private static <T> List<T> mapArray(JsonObject source, String key, Class<T> type) {
        JsonArray values = array(source, key);
        if (values == null) return List.of();
        List<T> result = new ArrayList<>(values.size());
        for (JsonElement value : values) result.add(GSON.fromJson(value, type));
        return List.copyOf(result);
    }
    private static String string(JsonObject value, String key) {
        return value.has(key) && !value.get(key).isJsonNull() ? value.get(key).getAsString() : "";
    }
    private static long number(JsonObject value, String key) {
        return value.has(key) && !value.get(key).isJsonNull() ? value.get(key).getAsLong() : -1L;
    }

    @Override public void close() { http.close(); }

    public record AudioUrl(String url, String type, long sizeBytes, long durationMs) {}
    public record QrLogin(String key, String url) {}
    public record QrStatus(int code, String cookie) {}
    public record SmsStatus(int code, String message) {
        public SmsStatus { message = message == null ? "" : message; }
        public boolean successful() { return code == 200; }
    }
    public record SmsLogin(int code, String message, String cookie) {
        public SmsLogin { message = message == null ? "" : message; cookie = cookie == null ? "" : cookie; }
        public boolean successful() { return code == 200 && !cookie.isBlank(); }
    }
}
