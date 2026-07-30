package cc.nimbus.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class ConfigManager {
    public static final String DEFAULT_CONFIG = "default";
    private static final AtomicBoolean DIRTY = new AtomicBoolean();
    private static final List<Consumer<String>> LISTENERS = new CopyOnWriteArrayList<>();
    private static Path directory;
    private static volatile String currentConfig = DEFAULT_CONFIG;
    private static ScheduledExecutorService autoSaver;

    private ConfigManager() {}

    public static synchronized void init(Path fabricConfigDirectory) {
        directory = fabricConfigDirectory.resolve("nimbus").resolve("configs");
        ConfigConstants.configure(directory);
        ChangeTracker.setListener(ConfigManager::markDirty);
        if (!loadConfig(DEFAULT_CONFIG)) saveConfig(DEFAULT_CONFIG);
        autoSaver = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "Nimbus-Config-AutoSave");
            thread.setDaemon(true);
            return thread;
        });
        autoSaver.scheduleWithFixedDelay(() -> {
            if (isDirty()) saveConfig(currentConfig);
        }, 2, 2, TimeUnit.SECONDS);
    }

    public static synchronized void shutdown() {
        if (autoSaver != null) autoSaver.shutdownNow();
        if (directory != null) saveConfig(currentConfig);
        ChangeTracker.setListener(null);
        LISTENERS.clear();
    }

    public static File getConfigDir() { return requireDirectory().toFile(); }
    public static String getCurrentConfig() { return currentConfig; }
    public static void markDirty() { DIRTY.set(true); }
    public static boolean isDirty() { return DIRTY.get(); }

    private static Path requireDirectory() {
        if (directory == null) throw new IllegalStateException("ConfigManager is not initialized");
        return directory;
    }

    private static Path file(String name) { return requireDirectory().resolve(sanitizeConfigName(name) + ConfigConstants.JSON_EXT); }
    public static boolean isValidConfigName(String name) { return name != null && name.equals(sanitizeConfigName(name)); }
    public static String sanitizeConfigName(String name) { return name == null ? "" : name.trim().replaceAll("[^a-zA-Z0-9._-]", "_").replaceAll("\\.{2,}", "."); }

    public static synchronized boolean saveConfig(String name) {
        if (!isValidConfigName(name) || name.isBlank()) return false;
        Path target = file(name);
        Path temporary = target.resolveSibling(target.getFileName() + ConfigConstants.TEMP_EXT);
        try {
            Files.createDirectories(target.getParent());
            JsonObject root = new JsonObject();
            root.add("_metadata", ConfigSerializer.createMetadata(name));
            root.add("modules", ConfigSerializer.serializeModules());
            Files.writeString(temporary, ConfigSerializer.getGson().toJson(root), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            currentConfig = name;
            DIRTY.set(false);
            LISTENERS.forEach(listener -> listener.accept(name));
            return true;
        } catch (java.io.IOException | RuntimeException error) {
            System.err.println("[Nimbus] Failed to save config: " + error.getMessage());
            return false;
        }
    }

    public static synchronized boolean loadConfig(String name) {
        if (!isValidConfigName(name) || !Files.isRegularFile(file(name))) return false;
        try (Reader reader = Files.newBufferedReader(file(name), StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) return false;
            JsonObject root = parsed.getAsJsonObject();
            if (root.has("modules") && root.get("modules").isJsonObject()) {
                ConfigSerializer.deserializeModules(root.getAsJsonObject("modules"));
            }
            currentConfig = name;
            DIRTY.set(false);
            LISTENERS.forEach(listener -> listener.accept(name));
            return true;
        } catch (java.io.IOException | RuntimeException error) {
            return false;
        }
    }

    public static List<String> getConfigs() {
        try (var stream = Files.list(requireDirectory())) {
            return stream.filter(path -> path.getFileName().toString().endsWith(ConfigConstants.JSON_EXT))
                    .map(path -> path.getFileName().toString().replaceFirst("\\.json$", ""))
                    .sorted(String.CASE_INSENSITIVE_ORDER).toList();
        } catch (java.io.IOException error) {
            return List.of();
        }
    }


    public static boolean deleteConfig(String name) {
        if (DEFAULT_CONFIG.equals(name) || currentConfig.equals(name)) return false;
        try { return Files.deleteIfExists(file(name)); }
        catch (java.io.IOException error) { return false; }
    }
}