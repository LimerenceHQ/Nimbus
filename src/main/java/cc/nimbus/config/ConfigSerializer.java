package cc.nimbus.config;

import cc.nimbus.module.BasicModule;
import cc.nimbus.module.ModuleManager;
import cc.nimbus.settings.impl.*;
import com.google.gson.*;
import cc.nimbus.module.*;
import cc.nimbus.settings.Setting;
import cc.nimbus.settings.impl.*;

import java.util.*;

public final class ConfigSerializer {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private ConfigSerializer() {}
    public static Gson getGson() { return GSON; }
    public static JsonObject createMetadata(String name) { JsonObject value = new JsonObject(); value.addProperty("version", 1); value.addProperty("savedAt", System.currentTimeMillis()); value.addProperty("name", name); return value; }
    public static JsonObject serializeModules() {
        JsonObject result = new JsonObject();
        for (BasicModule module : ModuleManager.getInstance().getModules()) {
            JsonObject value = new JsonObject();
            value.addProperty("enabled", module.isEnabled()); value.addProperty("key", module.getKey()); value.addProperty("hidden", module.isHidden());
            JsonObject settings = new JsonObject(); serializeSettings(settings, module.getSettings()); value.add("settings", settings); result.add(module.getName(), value);
        }
        return result;
    }
    public static void serializeSettings(JsonObject target, List<Setting<?>> settings) {
        for (Setting<?> setting : settings) {
            Object value = setting.getValue();
            if (value instanceof Boolean b) target.addProperty(setting.getName(), b);
            else if (value instanceof Number n) target.addProperty(setting.getName(), n);
            else if (value instanceof String s) target.addProperty(setting.getName(), s);
            else if (value instanceof Set<?> set) { JsonArray array = new JsonArray(); set.forEach(item -> array.add(String.valueOf(item))); target.add(setting.getName(), array); }
        }
    }
    public static void deserializeModules(JsonObject object) {
        if (object == null) return;
        for (BasicModule module : ModuleManager.getInstance().getModules()) {
            JsonElement element = findIgnoreCase(object, module.getName());
            if (element == null || !element.isJsonObject()) continue;
            try {
                JsonObject value = element.getAsJsonObject();
                if (value.has("key")) module.setKey(value.get("key").getAsInt());
                if (value.has("hidden")) module.setHidden(value.get("hidden").getAsBoolean());
                if (value.has("settings") && value.get("settings").isJsonObject()) deserializeSettings(value.getAsJsonObject("settings"), module.getSettings());
                if (value.has("enabled")) module.setEnabled(value.get("enabled").getAsBoolean());
            } catch (RuntimeException error) {
                System.err.println("[Nimbus] Ignoring damaged config entry for " + module.getName() + ": " + error.getMessage());
            }
        }
    }
    private static JsonElement findIgnoreCase(JsonObject object, String name) { for (var entry : object.entrySet()) if (entry.getKey().equalsIgnoreCase(name)) return entry.getValue(); return null; }
    public static void deserializeSettings(JsonObject source, List<Setting<?>> settings) {
        for (Setting<?> setting : settings) {
            JsonElement value = findIgnoreCase(source, setting.getName());
            if (value == null || value.isJsonNull()) continue;
            try {
                if (setting instanceof BooleanSetting s) s.setValue(value.getAsBoolean());
                else if (setting instanceof NumberSetting s) s.setValue(value.getAsDouble());
                else if (setting instanceof ModeSetting s) s.setValue(value.getAsString());
                else if (setting instanceof ColorSetting s) s.setValue(value.getAsInt());
                else if (setting instanceof FoldSetting s) s.setValue(value.getAsBoolean());
                else if (setting instanceof TextSetting s) s.setValue(value.getAsString());
                else if (setting instanceof StringSetting s) s.setValue(value.getAsString());
                else if (setting instanceof MultiSelectSetting s && value.isJsonArray()) { Set<String> selected = new LinkedHashSet<>(); value.getAsJsonArray().forEach(item -> selected.add(item.getAsString())); s.setValue(selected); }
            } catch (RuntimeException ignored) {}
        }
    }
}
