package cc.nimbus.features.client;

public record ThemePreset(String name, int primary, int secondary) {
    public ThemePreset {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Theme preset name cannot be blank");
    }
}
