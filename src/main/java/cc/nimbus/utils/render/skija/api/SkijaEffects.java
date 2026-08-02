package cc.nimbus.utils.render.skija.api;

import cc.nimbus.utils.render.skija.internal.resource.FrameResources;
import io.github.humbleui.skija.FilterBlurMode;
import io.github.humbleui.skija.GradientStyle;
import io.github.humbleui.skija.FilterTileMode;
import io.github.humbleui.skija.ImageFilter;
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

    public ImageFilter imageBlur(float sigmaX, float sigmaY, FilterTileMode tileMode) {
        if (sigmaX <= 0f && sigmaY <= 0f) return null;
        return resources.own(ImageFilter.makeBlur(
                Math.max(0f, sigmaX), Math.max(0f, sigmaY),
                tileMode == null ? FilterTileMode.CLAMP : tileMode
        ));
    }

    public Shader linearGradient(Point start, Point end, int[] colors) {
        return resources.own(Shader.makeLinearGradient(start, end, colors));
    }

    public Shader linearGradient(Point start, Point end, int[] colors, float[] positions, GradientStyle style) {
        return resources.own(Shader.makeLinearGradient(start, end, colors, positions, style));
    }
}
