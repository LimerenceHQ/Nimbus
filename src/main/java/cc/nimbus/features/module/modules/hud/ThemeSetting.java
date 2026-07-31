package cc.nimbus.features.module.modules.hud;

public final class ThemeSetting {
    private static final int[] PALETTE = {0xFF2AA876, 0xFF52C7B8, 0xFF2AA876};
    private ThemeSetting() {}
    public static int[] getColorPalette() { return PALETTE.clone(); }
}
