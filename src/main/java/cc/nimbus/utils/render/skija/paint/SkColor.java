package cc.nimbus.utils.render.skija.paint;

import cc.nimbus.utils.render.skija.internal.SkijaBackend;
import io.github.humbleui.skija.Paint;

@SuppressWarnings("all")
public class SkColor {
    private final Paint _paint;

    private SkColor() {
        this(new Paint());
    }

    private SkColor(Paint paint) {
        this._paint = paint;
        this._paint.setAntiAlias(true);
        SkijaBackend.INSTANCE.ownFrameResource(paint);
    }

    public Paint getPaint() {
        return _paint;
    }

    public static SkColor makeHex(int rgba) {
        Paint paint = new Paint();
        paint.setColor(rgba);
        return new SkColor(paint);
    }

    public static class Util {
        public static int HSVToColor(float h, float s, float v) {
            return HSVToColor(1.0f, h, s, v);
        }

        public static int HSVToColor(float alpha, float h, float s, float v) {
            if (s < 0f) s = 0f;
            if (s > 1f) s = 1f;
            if (v < 0f) v = 0f;
            if (v > 1f) v = 1f;
            if (alpha < 0f) alpha = 0f;
            if (alpha > 1f) alpha = 1f;

            h = h % 360f;
            if (h < 0f) h += 360f;

            float c = v * s;
            float x = c * (1f - Math.abs((h / 60f) % 2f - 1f));
            float m = v - c;

            float r1 = 0f, g1 = 0f, b1 = 0f;

            if (h < 60f) {
                r1 = c; g1 = x; b1 = 0f;
            } else if (h < 120f) {
                r1 = x; g1 = c; b1 = 0f;
            } else if (h < 180f) {
                r1 = 0f; g1 = c; b1 = x;
            } else if (h < 240f) {
                r1 = 0f; g1 = x; b1 = c;
            } else if (h < 300f) {
                r1 = x; g1 = 0f; b1 = c;
            } else {
                r1 = c; g1 = 0f; b1 = x;
            }

            int a = Math.round(alpha * 255f);
            int r = Math.round((r1 + m) * 255f);
            int g = Math.round((g1 + m) * 255f);
            int b = Math.round((b1 + m) * 255f);

            return (a << 24) | (r << 16) | (g << 8) | b;
        }
    }
}
