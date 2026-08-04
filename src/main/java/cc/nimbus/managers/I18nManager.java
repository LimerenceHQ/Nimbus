package cc.nimbus.managers;

import cc.nimbus.core.interfaces.InstanceManager;
import cc.nimbus.features.client.ClientLanguage;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;

import java.io.IOException;
import java.io.InputStream;
import java.util.IllegalFormatException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public final class I18nManager implements InstanceManager {
    private static final String DEFAULT_LANGUAGE = "en_us";
    private static final String RESOURCE_ROOT = "/assets/nimbus/lang/";

    private final Supplier<ClientLanguage> selectedLanguage;
    private final Map<String, Map<String, String>> translations = new LinkedHashMap<>();
    private boolean initialized;

    public I18nManager(Supplier<ClientLanguage> selectedLanguage) {
        this.selectedLanguage = Objects.requireNonNull(selectedLanguage, "selectedLanguage");
    }

    @Override
    public synchronized void init() {
        if (initialized) return;
        translations.put(DEFAULT_LANGUAGE, load(DEFAULT_LANGUAGE));
        translations.put("zh_cn", load("zh_cn"));
        initialized = true;
    }

    @Override
    public synchronized void shutdown() {
        translations.clear();
        initialized = false;
    }

    public String get(String key, String fallback, Object... arguments) {
        String value = lookup(key, fallback);
        if (arguments == null || arguments.length == 0) return value;
        try {
            return String.format(Locale.ROOT, value, arguments);
        } catch (IllegalFormatException error) {
            System.err.println("[Nimbus] Invalid translation format for '" + key + "': " + error.getMessage());
            return fallback == null ? key : fallback;
        }
    }

    public boolean has(String key) {
        if (key == null || key.isBlank()) return false;
        Map<String, String> selected = translations.get(resolvedLanguageCode());
        return selected != null && selected.containsKey(key)
                || translations.getOrDefault(DEFAULT_LANGUAGE, Map.of()).containsKey(key);
    }

    public String currentLanguageCode() {
        return resolvedLanguageCode();
    }

    private String lookup(String key, String fallback) {
        String safeFallback = fallback == null ? Objects.toString(key, "") : fallback;
        if (!initialized || key == null || key.isBlank()) return safeFallback;
        Map<String, String> selected = translations.get(resolvedLanguageCode());
        if (selected != null && selected.containsKey(key)) return selected.get(key);
        return translations.getOrDefault(DEFAULT_LANGUAGE, Map.of()).getOrDefault(key, safeFallback);
    }

    private String resolvedLanguageCode() {
        ClientLanguage selected = selectedLanguage.get();
        if (selected != null && selected.languageCode() != null) return selected.languageCode();
        Minecraft minecraft = Minecraft.getInstance();
        String gameLanguage = minecraft == null || minecraft.getLanguageManager() == null
                ? DEFAULT_LANGUAGE : minecraft.getLanguageManager().getSelected();
        return translations.containsKey(gameLanguage) ? gameLanguage : DEFAULT_LANGUAGE;
    }

    private static Map<String, String> load(String languageCode) {
        String resource = RESOURCE_ROOT + languageCode + ".json";
        try (InputStream stream = I18nManager.class.getResourceAsStream(resource)) {
            if (stream == null) throw new IllegalStateException("Missing language resource: " + resource);
            Map<String, String> values = new LinkedHashMap<>();
            Language.loadFromJson(stream, values::put);
            return Map.copyOf(values);
        } catch (IOException error) {
            throw new IllegalStateException("Unable to load language resource: " + resource, error);
        }
    }
}
