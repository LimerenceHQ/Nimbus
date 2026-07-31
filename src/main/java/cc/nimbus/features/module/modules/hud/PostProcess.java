package cc.nimbus.features.module.modules.hud;

import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.BooleanSetting;
import cc.nimbus.features.module.api.setting.impl.ModeSetting;
import cc.nimbus.features.module.api.setting.impl.NumberSetting;
import cc.nimbus.features.module.api.setting.impl.modes.Modes;
import cc.nimbus.utils.render.skija.surface.RefreshMode;

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

    public final ModeSetting blurRefresh = new ModeSetting("Refresh (Blur)", RefreshMode.getNames(), RefreshMode.VSync.toString(), () -> !blur_mode.is("Off"));
    public final NumberSetting blurRefreshLimit = new NumberSetting("Refresh Limit (Blur)", 60, 1, 165, 1,
            () -> !blur_mode.is("Off") && blurRefresh.is(RefreshMode.Limited.toString()));
    public final ModeSetting glowRefresh = new ModeSetting("Refresh (Glow)", RefreshMode.getNames(), RefreshMode.VSync.toString(), shadow::getValue);
    public final NumberSetting glowRefreshLimit = new NumberSetting("Refresh Limit (Glow)", 30, 1, 165, 1,
            () -> shadow.getValue() && glowRefresh.is(RefreshMode.Limited.toString()));

}
