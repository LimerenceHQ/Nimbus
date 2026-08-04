package cc.nimbus.utils.i18n;

import java.util.Locale;

public final class I18nKeys {
    private I18nKeys() {}

    public static String part(String value) {
        if (value == null || value.isBlank()) return "unknown";
        String separated = value.trim().replaceAll("([a-z0-9])([A-Z])", "$1_$2");
        String normalized = separated.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return normalized.isEmpty() ? "unknown" : normalized;
    }
}
