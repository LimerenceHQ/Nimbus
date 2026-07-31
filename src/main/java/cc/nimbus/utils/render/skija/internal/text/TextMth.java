package cc.nimbus.utils.render.skija.internal.text;

public class TextMth {

    public static int calculateShadowColor(int hex) {
        int r = (hex >> 16) & 0xFF;
        int g = (hex >> 8) & 0xFF;
        int b = hex & 0xFF;

        double rNorm = r / 255.0;
        double gNorm = g / 255.0;
        double bNorm = b / 255.0;

        double maxVal = Math.max(rNorm, Math.max(gNorm, bNorm));
        double minVal = Math.min(rNorm, Math.min(gNorm, bNorm));
        double lightness = (maxVal + minVal) / 2;

        int shadowR, shadowG, shadowB, alpha;

        if (lightness > 0.8) {
            shadowR = (int)(r * 0.2);
            shadowG = (int)(g * 0.2);
            shadowB = (int)(b * 0.3);
            alpha = 0xCC;

        } else if (lightness < 0.2) {
            shadowR = Math.min(255, (int)(r + (255 - r) * 0.5));
            shadowG = Math.min(255, (int)(g + (255 - g) * 0.5));
            shadowB = Math.min(255, (int)(b + (255 - b) * 0.5));
            alpha = 0x99;

        } else {
            if (r > g && r > b) {
                shadowR = (int)(r * 0.5);
                shadowG = (int)(g * 0.3);
                shadowB = (int)(b * 0.3);
            } else if (g > r && g > b) {
                shadowR = (int)(r * 0.3);
                shadowG = (int)(g * 0.5);
                shadowB = (int)(b * 0.3);
            } else {
                shadowR = (int)(r * 0.3);
                shadowG = (int)(g * 0.3);
                shadowB = (int)(b * 0.5);
            }
            alpha = 0xAA;
        }

        return (alpha << 24) | (shadowR << 16) | (shadowG << 8) | shadowB;
    }
}
