package cc.nimbus.ui.clickgui.theme;

import cc.nimbus.features.client.ClientSettings;

public record ClickGuiTheme(int accent) {
    public static final int PANEL = 0x5C141619;
    public static final int PANEL_STRONG = 0x82101316;
    public static final int CONTROL = 0x30111316;
    public static final int HOVER = 0x14FFFFFF;
    public static final int STROKE = 0x18FFFFFF;
    public static final int TEXT = 0xFFF4F4F5;
    public static final int MUTED = 0xFFC2C4C9;
    public static final int DANGER = 0xFFEA6A6A;
    public static final int RAIL = 0xFF24282D;
    public static final int RAIL_HOVER = 0xFF2D3238;
    public static final int RAIL_ACTIVE = 0xFF353B42;
    public static final int RAIL_ICON = 0xE8FFFFFF;
    public static final int RAIL_ICON_ACTIVE = 0xFFFFFFFF;

    public static ClickGuiTheme from(ClientSettings settings) {
        return new ClickGuiTheme(settings.accentColor());
    }

    public int accent(int maximumAlpha) {
        int sourceAlpha = accent >>> 24;
        int alpha = Math.min(sourceAlpha, Math.clamp(maximumAlpha, 0, 255));
        return (accent & 0x00FFFFFF) | alpha << 24;
    }
}
