package cc.nimbus.features.client;

import cc.nimbus.features.module.api.setting.Setting;
import cc.nimbus.features.module.api.setting.impl.BooleanSetting;
import cc.nimbus.features.module.api.setting.impl.ColorSetting;
import cc.nimbus.features.module.api.setting.impl.ModeSetting;
import cc.nimbus.features.module.api.setting.impl.NumberSetting;
import cc.nimbus.features.module.api.setting.impl.TextSetting;
import cc.nimbus.utils.render.skija.api.PostProcessOptions;
import net.minecraft.client.Minecraft;

import java.util.List;

public final class ClientSettings {
    private static final String NIMBUS_STYLE = "Nimbus";
    private static final String CUSTOM_STYLE = "Custom";

    public final TextSetting clientName = new TextSetting(
            "Client Name", "Nimbus", "Client name", false, false,
            "Name used by Nimbus-styled HUD elements.", 32
    );
    public final ModeSetting style = new ModeSetting(
            "Style", new String[]{NIMBUS_STYLE, CUSTOM_STYLE}, NIMBUS_STYLE
    );
    public final ColorSetting accent = new ColorSetting("Accent", 0x66FFFFFF);

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
    public final ModeSetting blurMode = new ModeSetting(
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
    public final ModeSetting blurRefresh = new ModeSetting(
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
    public final ModeSetting shadowRefresh = new ModeSetting(
            "Shadow Refresh", refreshModes(), "V-Sync",
            () -> postProcessing.getValue() && shadow.getValue()
    );
    public final NumberSetting shadowRefreshLimit = new NumberSetting(
            "Shadow Refresh Limit", 30, 1, 360, 1,
            () -> postProcessing.getValue() && shadow.getValue() && shadowRefresh.is("Limited")
    );

    private final List<Setting<?>> settings = List.of(
            clientName, style, accent,
            postProcessing, splitRegions, texture, textureOpacity,
            blurMode, blurRadius, blurPasses, downSample, upSample,
            blurRefresh, blurRefreshLimit,
            shadow, shadowRadius, shadowOpacity, shadowRefresh, shadowRefreshLimit
    );

    public List<Setting<?>> settings() {
        return settings;
    }

    public void bindChangeListener(Runnable listener) {
        settings.forEach(setting -> setting.setChangeListener(listener));
    }

    public boolean customStyle() {
        return style.is(CUSTOM_STYLE);
    }

    public String displayName() {
        String value = clientName.getValue();
        return value == null || value.isBlank() ? "Nimbus" : value.strip();
    }

    public int accentColor() {
        return accent.isRainbow() ? accent.getRainbow(3200, 0) : accent.getValue();
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

    private static String[] refreshModes() {
        return new String[]{"Unlimited", "Limited", "V-Sync"};
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
