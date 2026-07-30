package cc.nimbus.settings.impl;

import cc.nimbus.config.ChangeTracker;
import cc.nimbus.settings.Setting;
import java.awt.Color;
import java.util.function.Supplier;

public class ColorSetting extends Setting<Integer> {
    private boolean rainbow;
    private boolean expanded;
    public ColorSetting(String name, int color) { this(name, color, () -> true); }
    public ColorSetting(String name, int color, Supplier<Boolean> visibility) { super(name, color, visibility); }
    public Color getColor() { return new Color(getValue(), true); }
    public int getRgba() { return getValue(); }
    public void setRainbow(boolean value) { if (rainbow != value) { rainbow = value; ChangeTracker.changed(); } }
    public boolean isRainbow() { return rainbow; }
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
