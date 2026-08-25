package cc.nimbus.features.client;

import cc.nimbus.core.Core;
import cc.nimbus.features.module.api.setting.Setting;
import cc.nimbus.features.module.api.setting.impl.BooleanSetting;
import cc.nimbus.features.module.api.setting.impl.ColorSetting;
import cc.nimbus.features.module.api.setting.impl.submodule.ModeValue;
import cc.nimbus.features.module.api.setting.impl.NumberSetting;
import cc.nimbus.features.module.api.setting.impl.TextSetting;
import cc.nimbus.utils.render.skija.api.PostProcessOptions;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.IconSet;

import java.awt.Color;
import java.util.List;

public final class ClientSettings {
    private static final String NIMBUS_STYLE = "Nimbus";
    private static final String CUSTOM_STYLE = "Custom";
    private static final String CUSTOM_PRESET = "Custom";
    private static final List<ThemePreset> THEME_PRESETS = List.of(
            new ThemePreset("Nimbus", 0xFFFFFFFF, 0xFFB8C0CC),
            new ThemePreset("Azure", 0xFF70A7FF, 0xFF62D9E8),
            new ThemePreset("Sakura", 0xFFFF8FB7, 0xFFC49AFF),
            new ThemePreset("Mint", 0xFF70D6B1, 0xFF79C7C5),
            new ThemePreset("Sunset", 0xFFFFA36C, 0xFFFF6F91),
            new ThemePreset("Violet", 0xFFB49AFF, 0xFF738BFF),
            new ThemePreset("Burgundy", 0xFF7F183D, 0xFFC24D6D),
            new ThemePreset(CUSTOM_PRESET, 0xFFFFFFFF, 0xFFB8C0CC)
    );

    public final TextSetting clientName = new TextSetting(
            "Client Name", "Nimbus", "Client name", false, false,
            "Name used by Nimbus-styled HUD elements.", 32
    );
    public final ModeValue language = new ModeValue(
            "Language", ClientLanguage.valuesForSetting(), ClientLanguage.FOLLOW_GAME.value()
    );
    public final ModeValue style = new ModeValue(
            "Style", new String[]{NIMBUS_STYLE, CUSTOM_STYLE}, NIMBUS_STYLE
    );
    public final ModeValue themePreset = new ModeValue(
            "Theme Preset", presetNames(), "Nimbus"
    );
    public final ModeValue colorMode = new ModeValue(
            "Color Mode", new String[]{"Static", "Fade", "Dynamic", "Astolfo", "Rainbow"}, "Static"
    );
    public final BooleanSetting arentIOnePointEight = new BooleanSetting(
            "1.8.9 Window Branding", false
    );
    public final ColorSetting accent = new ColorSetting(
            "Accent", 0xFFFFFFFF, () -> customPreset() && colorMode.is("Static", "Fade", "Dynamic"), false
    );
    public final ColorSetting secondaryAccent = new ColorSetting(
            "Secondary Accent", 0xFF70A7FF, () -> customPreset() && colorMode.is("Dynamic"), false
    );
    public final NumberSetting rainbowSaturation = new NumberSetting(
            "Rainbow Saturation", 0.6, 0, 1, 0.05, () -> colorMode.is("Rainbow")
    );
    public final NumberSetting rainbowBrightness = new NumberSetting(
            "Rainbow Brightness", 1, 0.2, 1, 0.05, () -> colorMode.is("Rainbow")
    );
    public final NumberSetting astolfoIndex = new NumberSetting(
            "Astolfo Index", 75, 1, 200, 1, () -> colorMode.is("Astolfo")
    );
    public final NumberSetting astolfoOffset = new NumberSetting(
            "Astolfo Offset", 5, 0, 20, 1, () -> colorMode.is("Astolfo")
    );
    public final NumberSetting hudScale = new NumberSetting("HUD Scale", 1, 0.5, 2, 0.05);
    public final ModeValue hudRefresh = new ModeValue(
            "HUD Refresh", new String[]{"Limited", "V-Sync"}, "Limited"
    );
    public final NumberSetting hudRefreshLimit = new NumberSetting(
            "HUD Refresh Limit", 120, 30, 360, 1, () -> hudRefresh.is("Limited")
    );

    public final BooleanSetting postProcessing = new BooleanSetting("Post Processing", true);
    public final BooleanSetting splitRegions = new BooleanSetting(
            "Split Regions", true, postProcessing::getValue
    );
    public final BooleanSetting texture = new BooleanSetting(
            "Texture", true, postProcessing::getValue
    );
    public final NumberSetting textureOpacity = new NumberSetting(
            "Texture Opacity", 0.2, 0, 1, 0.05,
            () -> postProcessing.getValue() && texture.getValue()
    );
    public final ModeValue blurMode = new ModeValue(
            "Blur", new String[]{"Off", "Gaussian", "Kawase"}, "Gaussian",
            postProcessing::getValue
    );
    public final NumberSetting blurRadius = new NumberSetting(
            "Blur Radius", 10, 0, 20, 0.5,
            () -> postProcessing.getValue() && blurMode.is("Gaussian")
    );
    public final NumberSetting blurPasses = new NumberSetting(
            "Blur Passes", 2, 1, 5, 1,
            () -> postProcessing.getValue() && blurMode.is("Kawase")
    );
    public final NumberSetting downSample = new NumberSetting(
            "Down Sample", 3, 1, 5, 0.5,
            () -> postProcessing.getValue() && blurMode.is("Kawase")
    );
    public final NumberSetting upSample = new NumberSetting(
            "Up Sample", 3, 1, 5, 0.5,
            () -> postProcessing.getValue() && blurMode.is("Kawase")
    );
    public final ModeValue blurRefresh = new ModeValue(
            "Blur Refresh", refreshModes(), "V-Sync",
            () -> postProcessing.getValue() && !blurMode.is("Off")
    );
    public final NumberSetting blurRefreshLimit = new NumberSetting(
            "Blur Refresh Limit", 60, 1, 360, 1,
            () -> postProcessing.getValue() && !blurMode.is("Off") && blurRefresh.is("Limited")
    );
    public final BooleanSetting shadow = new BooleanSetting(
            "Shadow", false, postProcessing::getValue
    );
    public final NumberSetting shadowRadius = new NumberSetting(
            "Shadow Radius", 10, 0, 20, 0.5,
            () -> postProcessing.getValue() && shadow.getValue()
    );
    public final NumberSetting shadowOpacity = new NumberSetting(
            "Shadow Opacity", 0.2, 0, 1, 0.05,
            () -> postProcessing.getValue() && shadow.getValue()
    );
    public final ModeValue shadowRefresh = new ModeValue(
            "Shadow Refresh", refreshModes(), "V-Sync",
            () -> postProcessing.getValue() && shadow.getValue()
    );
    public final NumberSetting shadowRefreshLimit = new NumberSetting(
            "Shadow Refresh Limit", 30, 1, 360, 1,
            () -> postProcessing.getValue() && shadow.getValue() && shadowRefresh.is("Limited")
    );

    private final List<Setting<?>> settings = List.of(
            clientName, language, style, themePreset, colorMode, arentIOnePointEight, accent, secondaryAccent,
            rainbowSaturation, rainbowBrightness, astolfoIndex, astolfoOffset, hudScale,
            hudRefresh, hudRefreshLimit,
            postProcessing, splitRegions, texture, textureOpacity,
            blurMode, blurRadius, blurPasses, downSample, upSample,
            blurRefresh, blurRefreshLimit,
            shadow, shadowRadius, shadowOpacity, shadowRefresh, shadowRefreshLimit
    );

    public ClientSettings() {
        settings.forEach(setting -> setting.bindTranslationScope("nimbus.client"));
    }

    public List<Setting<?>> settings() {
        return settings;
    }

    public void bindChangeListener(Runnable listener) {
        Runnable callback = listener == null ? () -> {} : listener;
        settings.forEach(setting -> setting.setChangeListener(() -> {
            callback.run();
            if (setting == arentIOnePointEight && Core.INSTANCE.isInitialized()) refreshWindowIdentity();
        }));
    }

    public void refreshWindowIdentity() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getWindow() == null) return;
        minecraft.updateTitle();
        try {
            minecraft.getWindow().setIcon(minecraft.getVanillaPackResources(), IconSet.RELEASE);
        } catch (java.io.IOException error) {
            System.err.println("[Nimbus] Failed to refresh window icon: " + error.getMessage());
        }
    }

    public boolean customStyle() {
        return style.is(CUSTOM_STYLE);
    }

    public String displayName() {
        String value = clientName.getValue();
        return value == null || value.isBlank() ? "Nimbus" : value.strip();
    }

    public int accentColor() {
        return accentColor(0, System.currentTimeMillis());
    }

    public int accentColor(int index) {
        return accentColor(index, System.currentTimeMillis());
    }

    public int accentColor(int index, long timeMillis) {
        int primary = primaryColor();
        int secondary = secondaryColor();
        return switch (colorMode.getValue()) {
            case "Fade" -> fade(primary, timeMillis, index);
            case "Dynamic" -> dynamic(primary, secondary, timeMillis, index);
            case "Astolfo" -> astolfo(timeMillis, index, primary >>> 24);
            case "Rainbow" -> rainbow(timeMillis, index, primary >>> 24);
            default -> primary;
        };
    }

    public int accentColorAt(float position, long timeMillis) {
        int index = Math.round(Math.clamp(position, 0f, 1f) * 6f);
        if (colorMode.is("Fade", "Astolfo", "Rainbow")) index = -index;
        return accentColor(index, timeMillis);
    }

    public int interfaceAccentColor() {
        return primaryColor();
    }

    public boolean usesThemePreset() {
        return !colorMode.is("Astolfo", "Rainbow");
    }

    public List<ThemePreset> themePresets() {
        return THEME_PRESETS;
    }

    public ThemePreset selectedPreset() {
        String selected = themePreset.getValue();
        for (ThemePreset preset : THEME_PRESETS) {
            if (preset.name().equals(selected)) return preset;
        }
        return THEME_PRESETS.getFirst();
    }

    public int previewPrimary(ThemePreset preset) {
        return preset.name().equals(CUSTOM_PRESET) ? accent.getValue() : preset.primary();
    }

    public int previewSecondary(ThemePreset preset) {
        return preset.name().equals(CUSTOM_PRESET) ? secondaryAccent.getValue() : preset.secondary();
    }

    public PostProcessOptions postProcessOptions() {
        return new PostProcessOptions(
                postProcessing.getValue(),
                splitRegions.getValue(),
                switch (blurMode.getValue()) {
                    case "Gaussian" -> PostProcessOptions.BlurMode.GAUSSIAN;
                    case "Kawase" -> PostProcessOptions.BlurMode.KAWASE;
                    default -> PostProcessOptions.BlurMode.OFF;
                },
                blurRadius.getValue().floatValue(),
                blurPasses.getValue().intValue(),
                downSample.getValue().floatValue(),
                upSample.getValue().floatValue(),
                refreshRate(blurRefresh.getValue(), blurRefreshLimit.getValue().intValue(), 60),
                shadow.getValue(),
                shadowRadius.getValue().floatValue(),
                Math.round(shadowOpacity.getValue().floatValue() * 255f),
                refreshRate(shadowRefresh.getValue(), shadowRefreshLimit.getValue().intValue(), 30),
                texture.getValue(),
                Math.round(textureOpacity.getValue().floatValue() * 255f)
        );
    }

    public int hudRefreshRate() {
        return refreshRate(hudRefresh.getValue(), hudRefreshLimit.getValue().intValue(), 60);
    }

    private static String[] refreshModes() {
        return new String[]{"Unlimited", "Limited", "V-Sync"};
    }

    private boolean customPreset() {
        return themePreset.is(CUSTOM_PRESET);
    }

    private int primaryColor() {
        return previewPrimary(selectedPreset());
    }

    private int secondaryColor() {
        return previewSecondary(selectedPreset());
    }

    private int fade(int color, long timeMillis, int index) {
        int red = color >> 16 & 0xFF;
        int green = color >> 8 & 0xFF;
        int blue = color & 0xFF;
        int maximum = Math.max(red, Math.max(green, blue));
        int minimum = Math.min(red, Math.min(green, blue));
        float saturation = maximum == 0 ? 0f : (maximum - minimum) / (float) maximum;
        float hue = 0f;
        int delta = maximum - minimum;
        if (delta != 0) {
            float redDistance = (maximum - red) / (float) delta;
            float greenDistance = (maximum - green) / (float) delta;
            float blueDistance = (maximum - blue) / (float) delta;
            if (red == maximum) hue = blueDistance - greenDistance;
            else if (green == maximum) hue = 2f + redDistance - blueDistance;
            else hue = 4f + greenDistance - redDistance;
            hue /= 6f;
            if (hue < 0f) hue += 1f;
        }
        int angle = (int) ((timeMillis / 4L + index * 60L) % 360L);
        angle = (angle > 180 ? 360 - angle : angle) + 180;
        int rgb = Color.HSBtoRGB(hue, saturation, angle / 360f);
        return rgb & 0x00FFFFFF | color & 0xFF000000;
    }

    private static int dynamic(int first, int second, long timeMillis, int index) {
        double phase = (timeMillis - index * 200L) / 2000d * Math.PI * 2d;
        return mix(first, second, (float) ((Math.sin(phase) + 1d) * 0.5d));
    }

    private int astolfo(long timeMillis, int index, int alpha) {
        double cycle = timeMillis / 3000d;
        double offset = index * astolfoOffset.getValue().doubleValue()
                / Math.max(1d, astolfoIndex.getValue().doubleValue());
        float hue = (float) (cycle + offset - Math.floor(cycle + offset));
        if (hue > 0.5f) hue = 1f - hue;
        int rgb = Color.HSBtoRGB(hue + 0.5f, 0.52f, 1f);
        return rgb & 0x00FFFFFF | alpha << 24;
    }

    private int rainbow(long timeMillis, int index, int alpha) {
        float hue = (float) ((timeMillis / 3600d + index * 0.035d) % 1d);
        int rgb = Color.HSBtoRGB(hue, rainbowSaturation.getValue().floatValue(),
                rainbowBrightness.getValue().floatValue());
        return rgb & 0x00FFFFFF | alpha << 24;
    }

    private static int mix(int first, int second, float progress) {
        float value = Math.clamp(progress, 0f, 1f);
        int a = Math.round((first >>> 24) + ((second >>> 24) - (first >>> 24)) * value);
        int r = Math.round((first >> 16 & 0xFF) + ((second >> 16 & 0xFF) - (first >> 16 & 0xFF)) * value);
        int g = Math.round((first >> 8 & 0xFF) + ((second >> 8 & 0xFF) - (first >> 8 & 0xFF)) * value);
        int b = Math.round((first & 0xFF) + ((second & 0xFF) - (first & 0xFF)) * value);
        return a << 24 | r << 16 | g << 8 | b;
    }

    private static String[] presetNames() {
        return THEME_PRESETS.stream().map(ThemePreset::name).toArray(String[]::new);
    }

    private static int refreshRate(String mode, int limit, int fallback) {
        return switch (mode) {
            case "Unlimited" -> Integer.MAX_VALUE;
            case "Limited" -> Math.max(1, limit);
            case "V-Sync" -> {
                int refreshRate = Minecraft.getInstance().getWindow().getRefreshRate();
                yield refreshRate > 0 ? refreshRate : fallback;
            }
            default -> fallback;
        };
    }
}
