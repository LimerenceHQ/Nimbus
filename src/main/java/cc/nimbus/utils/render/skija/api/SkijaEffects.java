package cc.nimbus.utils.render.skija.api;

import cc.nimbus.utils.render.skija.internal.resource.FrameResources;
import io.github.humbleui.skija.FilterBlurMode;
import io.github.humbleui.skija.GradientStyle;
import io.github.humbleui.skija.MaskFilter;
import io.github.humbleui.skija.Shader;
import io.github.humbleui.types.Point;

/** Creates paint effects that are released automatically after the frame. */
public final class SkijaEffects {
    private final FrameResources resources;

    SkijaEffects(FrameResources resources) {
        this.resources = resources;
    }

    public MaskFilter blur(FilterBlurMode mode, float radius) {
        if (radius <= 0f) return null;
        return resources.own(MaskFilter.makeBlur(mode, radius));
    }

    public Shader linearGradient(Point start, Point end, int[] colors) {
        return resources.own(Shader.makeLinearGradient(start, end, colors));
    }

    public Shader linearGradient(Point start, Point end, int[] colors, float[] positions, GradientStyle style) {
        return resources.own(Shader.makeLinearGradient(start, end, colors, positions, style));
    }
}
