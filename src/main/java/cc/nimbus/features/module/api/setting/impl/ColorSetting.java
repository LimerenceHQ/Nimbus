package cc.nimbus.features.module.api.setting.impl;

import cc.nimbus.features.module.api.setting.Setting;
import java.awt.Color;
import java.util.function.Supplier;

public class ColorSetting extends Setting<Integer> {
    private final boolean rainbowSupported;
    private boolean rainbow;
    private boolean expanded;
    public ColorSetting(String name, int color) { this(name, color, () -> true, true); }
    public ColorSetting(String name, int color, Supplier<Boolean> visibility) { this(name, color, visibility, true); }
    public ColorSetting(String name, int color, Supplier<Boolean> visibility, boolean rainbowSupported) {
        super(name, color, visibility);
        this.rainbowSupported = rainbowSupported;
    }
    public Color getColor() { return new Color(getValue(), true); }
    public int getRgba() { return getValue(); }
    public void setRainbow(boolean value) {
        boolean normalized = rainbowSupported && value;
        if (rainbow != normalized) { rainbow = normalized; notifyChanged(); }
    }
    public boolean isRainbow() { return rainbow; }
    public boolean isRainbowSupported() { return rainbowSupported; }
    public boolean isExpanded() { return expanded; }
    public void setExpanded(boolean expanded) { this.expanded = expanded; }
    public void toggleExpanded() { expanded = !expanded; }
    public float getHue() { return Color.RGBtoHSB(getColor().getRed(), getColor().getGreen(), getColor().getBlue(), null)[0]; }
    public float getSaturation() { return Color.RGBtoHSB(getColor().getRed(), getColor().getGreen(), getColor().getBlue(), null)[1]; }
    public float getBrightness() { return Color.RGBtoHSB(getColor().getRed(), getColor().getGreen(), getColor().getBlue(), null)[2]; }
    public int getAlphaValue() { return getColor().getAlpha(); }
    public void setHue(float value) { setHSB(value, getSaturation(), getBrightness()); }
    public void setSaturation(float value) { setHSB(getHue(), value, getBrightness()); }
    public void setBrightness(float value) { setHSB(getHue(), getSaturation(), value); }
    public void setAlphaValue(int alpha) { int a = Math.max(0, Math.min(255, alpha)); setValue((a << 24) | (getValue() & 0xFFFFFF)); }
    public void setHSB(float h, float s, float b) { int rgb = Color.HSBtoRGB(clamp(h), clamp(s), clamp(b)); setValue((getAlphaValue() << 24) | (rgb & 0xFFFFFF)); }
    private float clamp(float value) { return Math.max(0, Math.min(1, value)); }
    public int getRainbow(int speed, int offset) { return Color.getHSBColor((System.currentTimeMillis() + offset) % speed / (float) speed, 1, 1).getRGB(); }
}
