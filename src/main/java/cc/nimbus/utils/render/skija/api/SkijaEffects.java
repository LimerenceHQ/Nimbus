package cc.nimbus.utils.render.skija.api;

import cc.nimbus.utils.render.skija.internal.effect.paint.PaintEffects;
import io.github.humbleui.skija.FilterBlurMode;
import io.github.humbleui.skija.GradientStyle;
import io.github.humbleui.skija.MaskFilter;
import io.github.humbleui.skija.Shader;
import io.github.humbleui.types.Point;

/** Frame-facing access to cached Skija paint effects. */
public final class SkijaEffects {
    SkijaEffects() {
    }

    public MaskFilter blur(FilterBlurMode mode, float radius) {
        return PaintEffects.makeBlur(mode, radius);
    }

    public Shader linearGradient(Point start, Point end, int[] colors) {
        return PaintEffects.makeLinearGradient(start, end, colors);
    }

    public Shader linearGradient(Point start, Point end, int[] colors, float[] positions, GradientStyle style) {
        return PaintEffects.makeLinearGradient(start, end, colors, positions, style);
    }
}
