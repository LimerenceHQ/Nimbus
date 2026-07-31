package cc.nimbus.features.module.modules.hud;

import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.BooleanSetting;
import cc.nimbus.features.module.api.setting.impl.ModeSetting;
import cc.nimbus.features.module.api.setting.impl.NumberSetting;
import cc.nimbus.features.module.api.setting.impl.modes.Modes;
import cc.nimbus.utils.render.skija.api.PostProcessOptions;

@ModuleInfo(name = "PostProcess", displayName = "Post Process", alias = {"blur", "shader"}, category = ModuleCategory.HUD, enableOnStartUp = true)
public class PostProcess extends BasicModule {
    public final BooleanSetting spilt = new BooleanSetting("Spilt", true);

    public final BooleanSetting texture = new BooleanSetting("Texture", true);
    public final NumberSetting texture_opacity = new NumberSetting("Texture Opacity", 0.2, 0, 1, 0.3, texture::getValue);

    public final ModeSetting blur_mode = new ModeSetting("Blur", new Modes("Off", "Gaussian", "Kawase"));
    public final NumberSetting blur_radius = new NumberSetting("Blur Radius", 10, 0, 20, 0.5, () -> blur_mode.is("Gaussian"));
    public final NumberSetting blur_passes = new NumberSetting("Blur Passes", 2, 1, 5, 1, () -> blur_mode.is("Kawase"));
    public final NumberSetting blur_sample = new NumberSetting("Blur Sample", 3, 1, 5, 0.5, () -> blur_mode.is("Kawase"));

    public final BooleanSetting shadow = new BooleanSetting("Shadow", false);
    public final NumberSetting shadow_radius = new NumberSetting("Shadow Radius", 10, 0, 20, 0.5, shadow::getValue);
    public final NumberSetting shadow_opacity = new NumberSetting("Shadow Opacity", 0.2, 0, 1, 0.1, shadow::getValue);

    public final ModeSetting blurRefresh = new ModeSetting("Refresh (Blur)", RefreshMode.getNames(), RefreshMode.V_SYNC.toString(), () -> !blur_mode.is("Off"));
    public final NumberSetting blurRefreshLimit = new NumberSetting("Refresh Limit (Blur)", 60, 1, 165, 1,
            () -> !blur_mode.is("Off") && blurRefresh.is(RefreshMode.LIMITED.toString()));
    public final ModeSetting glowRefresh = new ModeSetting("Refresh (Glow)", RefreshMode.getNames(), RefreshMode.V_SYNC.toString(), shadow::getValue);
    public final NumberSetting glowRefreshLimit = new NumberSetting("Refresh Limit (Glow)", 30, 1, 165, 1,
            () -> shadow.getValue() && glowRefresh.is(RefreshMode.LIMITED.toString()));

    public PostProcessOptions options() {
        return new PostProcessOptions(
                isEnabled(),
                spilt.getValue(),
                blurMode(blur_mode.getValue()),
                blur_radius.getValue().floatValue(),
                blur_passes.getValue().intValue(),
                blur_sample.getValue().floatValue(),
                blur_sample.getValue().floatValue(),
                refreshRate(blurRefresh.getValue(), blurRefreshLimit.getValue().intValue(), 60),
                shadow.getValue(),
                shadow_radius.getValue().floatValue(),
                Math.round(shadow_opacity.getValue().floatValue() * 255),
                refreshRate(glowRefresh.getValue(), glowRefreshLimit.getValue().intValue(), 30),
                texture.getValue(),
                Math.round(texture_opacity.getValue().floatValue() * 255)
        );
    }

    private static int refreshRate(String name, int limit, int fallback) {
        RefreshMode mode = RefreshMode.getByName(name);
        return mode == null ? fallback : mode.getRefreshRate(limit);
    }

    private static PostProcessOptions.BlurMode blurMode(String name) {
        return switch (name) {
            case "Gaussian" -> PostProcessOptions.BlurMode.GAUSSIAN;
            case "Kawase" -> PostProcessOptions.BlurMode.KAWASE;
            default -> PostProcessOptions.BlurMode.OFF;
        };
    }

    private enum RefreshMode {
        UNLIMITED("Unlimited"),
        LIMITED("Limited"),
        V_SYNC("V-Sync");

        private static final RefreshMode[] VALUES = values();
        private static final String[] NAMES = java.util.Arrays.stream(VALUES)
                .map(RefreshMode::toString)
                .toArray(String[]::new);

        private final String displayName;

        RefreshMode(String displayName) {
            this.displayName = displayName;
        }

        static RefreshMode getByName(String name) {
            if (name == null) return null;
            for (RefreshMode mode : VALUES) {
                if (mode.displayName.equals(name)) return mode;
            }
            return null;
        }

        static String[] getNames() {
            return NAMES.clone();
        }

        int getRefreshRate(int limitedFps) {
            return switch (this) {
                case UNLIMITED -> Integer.MAX_VALUE;
                case LIMITED -> limitedFps > 0 ? limitedFps : 165;
                case V_SYNC -> {
                    int refreshRate = net.minecraft.client.Minecraft.getInstance().getWindow().getRefreshRate();
                    yield refreshRate > 0 ? refreshRate : 165;
                }
            };
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

}
