package cc.nimbus.managers;

import cc.nimbus.core.interfaces.InstanceManager;
import cc.nimbus.event.EventBus;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.game.EventTick;
import cc.nimbus.features.client.ClientSettings;
import cc.nimbus.features.hud.Draggable;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.setting.Setting;
import cc.nimbus.features.module.api.setting.impl.*;
import cc.nimbus.features.module.api.setting.impl.submodule.ModeValue;
import cc.nimbus.features.module.api.setting.impl.submodule.ComponentValue;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
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
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ConfigManager implements InstanceManager {
    public static final String DEFAULT_CONFIG = "default";
    private static final String JSON_EXTENSION = ".json";
    private static final String TEMP_EXTENSION = ".tmp";
    private static final long AUTO_SAVE_DELAY_MS = 800L;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path rootDirectory;
    private final Path directory;
    private final Path clientFile;
    private final ModuleManager moduleManager;
    private ClientSettings clientSettings = new ClientSettings();
    private String currentConfig = DEFAULT_CONFIG;
    private boolean dirty;
    private boolean clientDirty;
    private boolean loading;
    private boolean initialized;
    private long dirtySince;
    private long clientDirtySince;
    private final Map<BasicModule, Boolean> deferredModuleStates = new LinkedHashMap<>();

    public ConfigManager(Path rootDirectory, ModuleManager moduleManager) {
        this.rootDirectory = Objects.requireNonNull(rootDirectory, "rootDirectory");
        this.directory = rootDirectory.resolve("configs");
        this.clientFile = rootDirectory.resolve("client.json");
        this.moduleManager = Objects.requireNonNull(moduleManager, "moduleManager");
    }

    @Override
    public synchronized void init() {
        if (initialized) return;
        try {
            Files.createDirectories(rootDirectory);
            Files.createDirectories(directory);
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Unable to create Nimbus config directories", error);
        }
        clientSettings.bindChangeListener(this::markClientDirty);
        moduleManager.setChangeListener(this::markDirty);
        EventBus.register(this);
        initialized = true;
        if (Files.isRegularFile(clientFile)) {
            if (!loadClientSettings()) {
                System.err.println("[Nimbus] Ignoring damaged client settings: " + clientFile);
                clientSettings = new ClientSettings();
                clientSettings.bindChangeListener(this::markClientDirty);
                if (!saveClientSettings()) throw failInitialization("Unable to recreate client settings: " + clientFile);
            }
        } else {
            if (!saveClientSettings()) throw failInitialization("Unable to create client settings: " + clientFile);
        }
        Path defaultFile = file(DEFAULT_CONFIG);
        if (Files.isRegularFile(defaultFile)) {
            if (!loadConfig(DEFAULT_CONFIG, true)) {
                throw failInitialization("Unable to load default config: " + defaultFile);
            }
        } else if (!saveConfig(DEFAULT_CONFIG)) {
            throw failInitialization("Unable to create default config: " + defaultFile);
        }
    }

    @Override
    public synchronized void shutdown() {
        if (!initialized) return;
        try {
            saveConfig(currentConfig);
        } finally {
            try {
                saveClientSettings();
            } finally {
                EventBus.unregister(this);
                moduleManager.setChangeListener(null);
                clientSettings.bindChangeListener(null);
                initialized = false;
            }
        }
    }

    public synchronized File getConfigDir() {
        return directory.toFile();
    }

    public synchronized String getCurrentConfig() {
        return currentConfig;
    }

    public synchronized boolean isDirty() {
        return dirty;
    }

    public ClientSettings clientSettings() {
        return clientSettings;
    }

    public synchronized void markDirty() {
        if (loading) return;
        dirty = true;
        dirtySince = System.currentTimeMillis();
    }

    public synchronized void markClientDirty() {
        if (loading) return;
        clientDirty = true;
        clientDirtySince = System.currentTimeMillis();
    }

    @EventTarget
    public synchronized void onTick(EventTick event) {
        if (event.getPhase() != EventTick.Phase.POST) return;
        if (dirty && System.currentTimeMillis() - dirtySince >= AUTO_SAVE_DELAY_MS) {
            saveConfig(currentConfig);
        }
        if (clientDirty && System.currentTimeMillis() - clientDirtySince >= AUTO_SAVE_DELAY_MS) {
            saveClientSettings();
        }
    }

    public synchronized boolean saveClientSettings() {
        Path temporary = clientFile.resolveSibling(clientFile.getFileName() + TEMP_EXTENSION);
        try {
            Files.createDirectories(rootDirectory);
            Files.writeString(temporary, GSON.toJson(clientSnapshot()), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            moveReplacing(temporary, clientFile);
            clientDirty = false;
            return true;
        } catch (java.io.IOException | RuntimeException error) {
            System.err.println("[Nimbus] Failed to save client settings: " + message(error));
            return false;
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (java.io.IOException ignored) {
            }
        }
    }

    private synchronized boolean loadClientSettings() {
        try (Reader reader = Files.newBufferedReader(clientFile, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) return false;
            JsonObject root = parsed.getAsJsonObject();
            JsonElement settings = root.get("settings");
            if (settings == null || !settings.isJsonObject()) return false;
            loading = true;
            deserializeSettings(settings.getAsJsonObject(), clientSettings.settings());
            clientDirty = false;
            return true;
        } catch (java.io.IOException | RuntimeException error) {
            System.err.println("[Nimbus] Failed to load client settings: " + message(error));
            return false;
        } finally {
            loading = false;
        }
    }

    public synchronized boolean saveConfig(String name) {
        if (!isValidConfigName(name)) return false;
        Path target = file(name);
        Path temporary = target.resolveSibling(target.getFileName() + TEMP_EXTENSION);
        try {
            Files.createDirectories(directory);
            Files.writeString(temporary, GSON.toJson(snapshot(name)), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            moveReplacing(temporary, target);
            currentConfig = name;
            dirty = false;
            return true;
        } catch (java.io.IOException | RuntimeException error) {
            System.err.println("[Nimbus] Failed to save config '" + name + "': " + message(error));
            return false;
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (java.io.IOException ignored) {
            }
        }
    }

    public synchronized boolean loadConfig(String name) {
        return loadConfig(name, false);
    }

    private boolean loadConfig(String name, boolean deferModuleStates) {
        if (!isValidConfigName(name) || !Files.isRegularFile(file(name))) return false;
        deferredModuleStates.clear();
        try (Reader reader = Files.newBufferedReader(file(name), StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) return false;
            JsonObject root = parsed.getAsJsonObject();
            loading = true;
            if (root.has("modules") && root.get("modules").isJsonObject()) {
                deserializeModules(root.getAsJsonObject("modules"), deferModuleStates);
            }
            currentConfig = name;
            dirty = false;
            return true;
        } catch (java.io.IOException | RuntimeException error) {
            System.err.println("[Nimbus] Failed to load config '" + name + "': " + message(error));
            return false;
        } finally {
            loading = false;
        }
    }

    public synchronized void applyDeferredModuleStates() {
        if (!initialized || deferredModuleStates.isEmpty()) return;
        loading = true;
        try {
            for (var entry : List.copyOf(deferredModuleStates.entrySet())) {
                try {
                    entry.getKey().setEnabled(entry.getValue());
                } catch (RuntimeException error) {
                    System.err.println("[Nimbus] Ignoring damaged enabled state for "
                            + entry.getKey().getName() + ": " + message(error));
                }
            }
        } finally {
            deferredModuleStates.clear();
            loading = false;
        }
    }

    public synchronized List<String> getConfigs() {
        try (var stream = Files.list(directory)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(JSON_EXTENSION))
                    .map(path -> path.getFileName().toString().replaceFirst("\\.json$", ""))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        } catch (java.io.IOException error) {
            System.err.println("[Nimbus] Failed to list configs: " + message(error));
            return List.of();
        }
    }

    public synchronized boolean deleteConfig(String name) {
        if (!isValidConfigName(name) || DEFAULT_CONFIG.equalsIgnoreCase(name)
                || currentConfig.equalsIgnoreCase(name)) return false;
        try {
            return Files.deleteIfExists(file(name));
        } catch (java.io.IOException error) {
            System.err.println("[Nimbus] Failed to delete config '" + name + "': " + message(error));
            return false;
        }
    }

    public static String sanitizeConfigName(String name) {
        return name == null ? "" : name.trim()
                .replaceAll("[^a-zA-Z0-9._-]", "_")
                .replaceAll("\\.{2,}", ".");
    }

    public static boolean isValidConfigName(String name) {
        return name != null && !name.isBlank() && name.equals(sanitizeConfigName(name));
    }

    private Path file(String name) {
        return directory.resolve(sanitizeConfigName(name) + JSON_EXTENSION);
    }

    private JsonObject snapshot(String name) {
        JsonObject root = new JsonObject();
        JsonObject metadata = new JsonObject();
        metadata.addProperty("version", 1);
        metadata.addProperty("savedAt", System.currentTimeMillis());
        metadata.addProperty("name", name);
        root.add("_metadata", metadata);

        JsonObject modules = new JsonObject();
        for (BasicModule module : moduleManager.getModules()) {
            JsonObject value = new JsonObject();
            value.addProperty("enabled", module.isEnabled());
            value.addProperty("key", module.getKey());
            value.addProperty("hidden", module.isHidden());
            JsonObject settings = new JsonObject();
            for (Setting<?> setting : module.getSettings()) settings.add(setting.getName(), writeSetting(setting));
            value.add("settings", settings);
            JsonObject draggables = new JsonObject();
            for (Draggable draggable : module.getDraggables()) {
                JsonObject state = new JsonObject();
                state.addProperty("x", draggable.getX());
                state.addProperty("y", draggable.getY());
                state.addProperty("scale", draggable.getScale());
                draggables.add(draggable.getName(), state);
            }
            if (draggables.size() > 0) value.add("draggables", draggables);
            modules.add(module.getName(), value);
        }
        root.add("modules", modules);
        return root;
    }

    private JsonObject clientSnapshot() {
        JsonObject root = new JsonObject();
        JsonObject metadata = new JsonObject();
        metadata.addProperty("version", 1);
        metadata.addProperty("savedAt", System.currentTimeMillis());
        root.add("_metadata", metadata);
        JsonObject settings = new JsonObject();
        for (Setting<?> setting : clientSettings.settings()) {
            settings.add(setting.getName(), writeSetting(setting));
        }
        root.add("settings", settings);
        return root;
    }

    private JsonElement writeSetting(Setting<?> setting) {
        Object value = setting.getValue();
        if (setting instanceof ModeValue mode) {
            return GSON.toJsonTree(mode.getValueName());
        }
        if (setting instanceof ColorSetting color) {
            JsonObject object = new JsonObject();
            object.addProperty("value", color.getValue());
            object.addProperty("rainbow", color.isRainbow());
            return object;
        }
        if (value instanceof Set<?> set) {
            JsonArray array = new JsonArray();
            set.forEach(item -> array.add(String.valueOf(item)));
            return array;
        }
        return GSON.toJsonTree(value);
    }

    private void deserializeModules(JsonObject source, boolean deferModuleStates) {
        for (BasicModule module : moduleManager.getModules()) {
            JsonElement element = findIgnoreCase(source, module.getName());
            if (element == null || !element.isJsonObject()) continue;
            try {
                JsonObject value = element.getAsJsonObject();
                if (value.has("key")) module.setKey(value.get("key").getAsInt());
                if (value.has("hidden")) module.setHidden(value.get("hidden").getAsBoolean());
                if (value.has("settings") && value.get("settings").isJsonObject()) {
                    deserializeSettings(value.getAsJsonObject("settings"), module.getSettings());
                }
                if (value.has("draggables") && value.get("draggables").isJsonObject()) {
                    deserializeDraggables(value.getAsJsonObject("draggables"), module);
                }
                if (value.has("enabled")) {
                    boolean enabled = value.get("enabled").getAsBoolean();
                    if (deferModuleStates) deferredModuleStates.put(module, enabled);
                    else module.setEnabled(enabled);
                }
            } catch (RuntimeException error) {
                System.err.println("[Nimbus] Ignoring damaged config entry for " + module.getName() + ": " + error.getMessage());
            }
        }
    }

    private void deserializeDraggables(JsonObject source, BasicModule module) {
        for (Draggable draggable : module.getDraggables()) {
            JsonElement element = findIgnoreCase(source, draggable.getName());
            if (element == null || !element.isJsonObject()) continue;
            try {
                JsonObject state = element.getAsJsonObject();
                double x = state.has("x") ? state.get("x").getAsDouble() : draggable.getX();
                double y = state.has("y") ? state.get("y").getAsDouble() : draggable.getY();
                draggable.setPosition(x, y);
                if (state.has("scale")) draggable.setScale(state.get("scale").getAsDouble());
            } catch (RuntimeException error) {
                System.err.println("[Nimbus] Ignoring damaged draggable '" + draggable.getName()
                        + "' in module " + module.getName() + ": " + message(error));
            }
        }
    }

    private void deserializeSettings(JsonObject source, List<Setting<?>> settings) {
        for (Setting<?> setting : settings) {
            JsonElement value = findIgnoreCase(source, setting.getName());
            if (value == null || value.isJsonNull()) continue;
            try {
                if (setting instanceof BooleanSetting target) target.setValue(value.getAsBoolean());
                else if (setting instanceof NumberSetting target) target.setValue(value.getAsDouble());
                else if (setting instanceof ModeValue target) target.update(value.getAsString());
                else if (setting instanceof ModeSetting target) target.setValue(value.getAsString());
                else if (setting instanceof ComponentValue target && value.isJsonArray()) {
                    Set<String> selected = new LinkedHashSet<>();
                    value.getAsJsonArray().forEach(item -> selected.add(item.getAsString()));
                    target.setValue(selected);
                }
                else if (setting instanceof ColorSetting target) readColor(target, value);
                else if (setting instanceof FoldSetting target) target.setValue(value.getAsBoolean());
                else if (setting instanceof TextSetting target) target.setValue(value.getAsString());
                else if (setting instanceof MultiSelectSetting target && value.isJsonArray()) {
                    Set<String> selected = new LinkedHashSet<>();
                    value.getAsJsonArray().forEach(item -> selected.add(item.getAsString()));
                    target.setValue(selected);
                }
            } catch (RuntimeException error) {
                System.err.println("[Nimbus] Ignoring damaged setting '" + setting.getName()
                        + "': " + message(error));
            }
        }
    }

    private void readColor(ColorSetting target, JsonElement value) {
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            if (object.has("value")) target.setValue(object.get("value").getAsInt());
            if (object.has("rainbow")) target.setRainbow(object.get("rainbow").getAsBoolean());
        } else {
            target.setValue(value.getAsInt());
        }
    }

    private static JsonElement findIgnoreCase(JsonObject object, String name) {
        for (var entry : object.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) return entry.getValue();
        }
        return null;
    }

    private static void moveReplacing(Path source, Path target) throws java.io.IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private IllegalStateException failInitialization(String message) {
        EventBus.unregister(this);
        moduleManager.setChangeListener(null);
        clientSettings.bindChangeListener(null);
        initialized = false;
        return new IllegalStateException(message);
    }

    private static String message(Throwable error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
}
