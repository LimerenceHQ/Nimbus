package cc.nimbus.features.music;

import cc.nimbus.features.music.playback.PlayMode;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

public final class MusicConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;
    private String cookie = "";
    private Quality quality = Quality.STANDARD;
    private PlayMode playMode = PlayMode.LOOP_LIST;
    private float volume = 0.7f;
    private float rate = 1f;
    private long cacheLimitBytes = 5L * 1024 * 1024 * 1024;
    private boolean prefetch = true;
    private int[] previousHotkey = new int[0];
    private int[] nextHotkey = new int[0];
    private int[] pauseHotkey = new int[0];

    public MusicConfig(Path file) { this.file = Objects.requireNonNull(file, "file"); }

    public synchronized void load() {
        if (!Files.isRegularFile(file)) return;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            cookie = string(root, "cookie", "");
            quality = enumeration(root, "quality", Quality.class, Quality.STANDARD);
            playMode = enumeration(root, "playMode", PlayMode.class, PlayMode.LOOP_LIST);
            volume = bound(number(root, "volume", 0.7f), 0f, 1f);
            rate = bound(number(root, "rate", 1f), 0.5f, 2f);
            cacheLimitBytes = Math.max(256L * 1024 * 1024, longNumber(root, "cacheLimitBytes", cacheLimitBytes));
            prefetch = bool(root, "prefetch", true);
            previousHotkey = hotkey(root, "previousHotkey");
            nextHotkey = hotkey(root, "nextHotkey");
            pauseHotkey = hotkey(root, "pauseHotkey");
        } catch (Exception error) {
            System.err.println("[Nimbus] Ignoring damaged music config: " + message(error));
        }
    }

    public synchronized void save() {
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("cookie", cookie);
            root.addProperty("quality", quality.name());
            root.addProperty("playMode", playMode.name());
            root.addProperty("volume", volume);
            root.addProperty("rate", rate);
            root.addProperty("cacheLimitBytes", cacheLimitBytes);
            root.addProperty("prefetch", prefetch);
            root.add("previousHotkey", hotkey(previousHotkey));
            root.add("nextHotkey", hotkey(nextHotkey));
            root.add("pauseHotkey", hotkey(pauseHotkey));
            Files.writeString(temporary, GSON.toJson(root), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception error) {
            throw new IllegalStateException("Unable to save music config", error);
        } finally {
            try { Files.deleteIfExists(temporary); } catch (Exception ignored) {}
        }
    }

    public synchronized String cookie() { return cookie; }
    public synchronized void cookie(String value) { cookie = value == null ? "" : value.trim(); }
    public synchronized Quality quality() { return quality; }
    public synchronized void quality(Quality value) { quality = Objects.requireNonNull(value, "quality"); }
    public synchronized PlayMode playMode() { return playMode; }
    public synchronized void playMode(PlayMode value) { playMode = Objects.requireNonNull(value, "playMode"); }
    public synchronized float volume() { return volume; }
    public synchronized void volume(float value) { volume = bound(value, 0f, 1f); }
    public synchronized float rate() { return rate; }
    public synchronized void rate(float value) { rate = bound(value, 0.5f, 2f); }
    public synchronized long cacheLimitBytes() { return cacheLimitBytes; }
    public synchronized void cacheLimitBytes(long value) { cacheLimitBytes = Math.max(256L * 1024 * 1024, value); }
    public synchronized boolean prefetch() { return prefetch; }
    public synchronized void prefetch(boolean value) { prefetch = value; }
    public synchronized int[] hotkey(Hotkey action) {
        return switch (action) {
            case PREVIOUS -> previousHotkey.clone();
            case NEXT -> nextHotkey.clone();
            case PAUSE -> pauseHotkey.clone();
        };
    }
    public synchronized void hotkey(Hotkey action, int... keys) {
        int[] value = keys == null ? new int[0] : java.util.Arrays.stream(keys)
                .filter(key -> key > 0).distinct().limit(3).toArray();
        switch (action) {
            case PREVIOUS -> previousHotkey = value;
            case NEXT -> nextHotkey = value;
            case PAUSE -> pauseHotkey = value;
        }
    }

    private static String string(JsonObject root, String key, String fallback) {
        try { return root.has(key) ? root.get(key).getAsString() : fallback; } catch (Exception ignored) { return fallback; }
    }
    private static float number(JsonObject root, String key, float fallback) {
        try { return root.has(key) ? root.get(key).getAsFloat() : fallback; } catch (Exception ignored) { return fallback; }
    }
    private static long longNumber(JsonObject root, String key, long fallback) {
        try { return root.has(key) ? root.get(key).getAsLong() : fallback; } catch (Exception ignored) { return fallback; }
    }
    private static boolean bool(JsonObject root, String key, boolean fallback) {
        try { return root.has(key) ? root.get(key).getAsBoolean() : fallback; } catch (Exception ignored) { return fallback; }
    }
    private static int[] hotkey(JsonObject root, String key) {
        try {
            if (!root.has(key) || !root.get(key).isJsonArray()) return new int[0];
            return root.getAsJsonArray(key).asList().stream().mapToInt(value -> value.getAsInt())
                    .filter(value -> value > 0).distinct().limit(3).toArray();
        } catch (Exception ignored) {
            return new int[0];
        }
    }
    private static JsonArray hotkey(int[] keys) {
        JsonArray array = new JsonArray();
        for (int key : keys) array.add(key);
        return array;
    }
    private static <T extends Enum<T>> T enumeration(JsonObject root, String key, Class<T> type, T fallback) {
        try { return root.has(key) ? Enum.valueOf(type, root.get(key).getAsString()) : fallback; } catch (Exception ignored) { return fallback; }
    }
    private static float bound(float value, float minimum, float maximum) {
        if (!Float.isFinite(value)) return minimum;
        return Math.max(minimum, Math.min(maximum, value));
    }
    private static String message(Throwable error) { return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(); }

    public enum Hotkey { PREVIOUS, NEXT, PAUSE }
}
