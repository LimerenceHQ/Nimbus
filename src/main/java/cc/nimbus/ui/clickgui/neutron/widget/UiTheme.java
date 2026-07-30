package cc.nimbus.ui.clickgui.neutron.widget;

import cc.nimbus.module.impl.hud.ThemeSetting;
import cc.nimbus.utils.render.skija.paint.PaintEffects;
import cc.nimbus.utils.render.skija.text.factory.TextFactory;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.GradientStyle;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Shader;
import io.github.humbleui.types.Point;

public final class UiTheme {
    private UiTheme() {
    }

    public static int accent() {
        int[] palette = ThemeSetting.getColorPalette();
        int a = palette[0];
        int b = palette[1];
        long now = System.currentTimeMillis();
        double p = (now % 3200L) / 3200.0;
        double t = 0.5 - 0.5 * Math.cos(p * Math.PI * 2.0);
        return lerpArgb(a, b, (float) t);
    }

    public static void drawFlowText(Canvas canvas, String family, float size, String text, float x, float y) {
        if (text == null) text = "";
        float tw = TextFactory.getTextWidth(family, size, text);
        float progress = (System.currentTimeMillis() % 3000L) / 3000f;
        float gradientWidth = tw * 2f;
        float startX = x - gradientWidth + (progress * gradientWidth);
        float endX = startX + gradientWidth;
        try (Paint paint = new Paint()) {
            paint.setAntiAlias(true);
            paint.setShader(PaintEffects.makeLinearGradient(
                    new Point(startX, y),
                    new Point(endX, y),
                    ThemeSetting.getColorPalette(),
                    new float[]{0f, 0.5f, 1f},
                    GradientStyle.REPEAT
            ));
            TextFactory.drawText(canvas, family, size, text, x, y, paint);
        }
    }

    public static Shader flowShader(float x, float y, float width) {
        float w = Math.max(1f, width);
        float progress = (System.currentTimeMillis() % 3000L) / 3000f;
        float gradientWidth = w * 2f;
        float startX = x - gradientWidth + (progress * gradientWidth);
        float endX = startX + gradientWidth;
        return PaintEffects.makeLinearGradient(
                new Point(startX, y),
                new Point(endX, y),
                ThemeSetting.getColorPalette(),
                new float[]{0f, 0.5f, 1f},
                GradientStyle.REPEAT
        );
    }

    public static int withAlpha(int argb, int alpha255) {
        int a = alpha255 & 0xFF;
        return (argb & 0x00FFFFFF) | (a << 24);
    }

    private static int lerpArgb(int a, int b, float t) {
        if (t <= 0f) return a;
        if (t >= 1f) return b;
        int aA = (a >>> 24) & 0xFF;
        int aR = (a >>> 16) & 0xFF;
        int aG = (a >>> 8) & 0xFF;
        int aB = a & 0xFF;
        int bA = (b >>> 24) & 0xFF;
        int bR = (b >>> 16) & 0xFF;
        int bG = (b >>> 8) & 0xFF;
        int bB = b & 0xFF;
        int oA = aA + Math.round((bA - aA) * t);
        int oR = aR + Math.round((bR - aR) * t);
        int oG = aG + Math.round((bG - aG) * t);
        int oB = aB + Math.round((bB - aB) * t);
        return (oA << 24) | (oR << 16) | (oG << 8) | oB;
    }
}
