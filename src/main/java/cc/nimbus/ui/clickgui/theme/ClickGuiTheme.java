package cc.nimbus.ui.clickgui.theme;

import cc.nimbus.features.client.ClientSettings;

public final class ClickGuiTheme {
    public static final int PANEL = 0x5C141619;
    public static final int PANEL_STRONG = 0x82101316;
    public static final int CONTROL = 0x30111316;
    public static final int HOVER = 0x14FFFFFF;
    public static final int STROKE = 0x18FFFFFF;
    public static final int TEXT = 0xFFF4F4F5;
    public static final int MUTED = 0xFFC2C4C9;
    public static final int DANGER = 0xFFEA6A6A;
    public static final int RAIL = 0x48101418;
    public static final int RAIL_HOVER = 0x64101418;
    public static final int RAIL_ACTIVE = 0x78101418;
    public static final int RAIL_ICON = 0xE8FFFFFF;
    public static final int RAIL_ICON_ACTIVE = 0xFFFFFFFF;

    private final ClientSettings settings;
    private final long timeMillis;

    private ClickGuiTheme(ClientSettings settings, long timeMillis) {
        this.settings = settings;
        this.timeMillis = timeMillis;
    }

    public static ClickGuiTheme from(ClientSettings settings) {
        return new ClickGuiTheme(settings, System.currentTimeMillis());
    }

    public int accent(int maximumAlpha) {
        return cappedAlpha(settings.accentColor(0, timeMillis), maximumAlpha);
    }

    public int accentAt(float position, int maximumAlpha) {
        return cappedAlpha(settings.accentColorAt(position, timeMillis), maximumAlpha);
    }

    public int motionAccent(int index, int maximumAlpha) {
        return cappedAlpha(settings.accentColor(index, timeMillis), maximumAlpha);
    }

    private static int cappedAlpha(int color, int maximumAlpha) {
        int sourceAlpha = color >>> 24;
        int alpha = Math.min(sourceAlpha, Math.clamp(maximumAlpha, 0, 255));
        return (color & 0x00FFFFFF) | alpha << 24;
    }
}
