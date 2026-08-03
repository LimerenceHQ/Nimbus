package cc.nimbus.utils.render.skija.internal.effect.surface;

import cc.nimbus.utils.render.skija.internal.image.minecraft.MinecraftSnapshot;
import io.github.humbleui.skija.*;
import io.github.humbleui.types.Rect;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@SuppressWarnings("ExtractMethodRecommender")
public final class GaussianSurface extends AbstractOffSurface<GaussianSurface.Target> {
    private final Paint blur = new Paint();

    private float lastRadius = 0;

    private ImageFilter blurFilter;
    private ColorFilter saturationFilter;

    public GaussianSurface(int targetFps) {
        super(targetFps);
    }

    @Override
    protected ImageInfo imageInfo(int width, int height, Target target) {
        return ImageInfo.makeN32Premul(width, height);
    }

    @Override
    protected float inflate(Target target) {
        // A Gaussian kernel reaches roughly three sigma beyond the requested path.
        // Keeping that margin prevents the offscreen surface edge from becoming a dark halo.
        return target.radius() * 3F;
    }

    @Override
    protected void onCanvas(DirectContext context, Surface surface, Canvas canvas, @Nullable Path path, @Nullable List<Rect> srcs, @Nullable Target target) {
        if (target == null || target.radius() <= 0) {
            return;
        }

        Image snapshot = MinecraftSnapshot.getScreenshot();
        if (snapshot == null || snapshot.isClosed()) {
            return;
        }

        if (blurFilter == null || blurFilter.isClosed() || lastRadius != target.radius()) {
            if (blurFilter != null && !blurFilter.isClosed()) {
                blurFilter.close();
            }
            blurFilter = ImageFilter.makeBlur(target.radius(), target.radius(), FilterTileMode.CLAMP);
        }

        if (saturationFilter == null || saturationFilter.isClosed() || lastRadius != target.radius()) {
            if (saturationFilter != null && !saturationFilter.isClosed()) {
                saturationFilter.close();
            }
            saturationFilter = ColorFilter.makeMatrix(saturationMatrix(target.radius()));
        }

        this.blur.setImageFilter(blurFilter);
        this.blur.setColorFilter(saturationFilter);

        float samplingMargin = target.radius() * 3F;
        Rect fullBounds = path == null
                ? Rect.makeWH(snapshot.getWidth(), snapshot.getHeight())
                : path.getBounds().inflate(samplingMargin);
        try (Shader scene = snapshot.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP,
                SamplingMode.LINEAR, Matrix33.IDENTITY);
             Paint source = new Paint().setShader(scene)) {
            if (srcs != null && !srcs.isEmpty()) {
                for (Rect src : srcs) {
                    Rect samplingBounds = src.inflate(samplingMargin);
                    int count = canvas.saveLayer(samplingBounds, blur);
                    try {
                        canvas.drawRect(samplingBounds, source);
                    } finally {
                        canvas.restoreToCount(count);
                    }
                }
            } else {
                int count = canvas.saveLayer(fullBounds, blur);
                try {
                    canvas.drawRect(fullBounds, source);
                } finally {
                    canvas.restoreToCount(count);
                }
            }
        }

        lastRadius = target.radius();
    }

    private static ColorMatrix saturationMatrix(float radius) {
        float saturation = 1F + radius / 120F;
        float red = 0.2126F;
        float green = 0.7152F;
        float blue = 0.0722F;
        float inverse = 1F - saturation;
        return new ColorMatrix(new float[]{
                red * inverse + saturation, green * inverse, blue * inverse, 0F, 0F,
                red * inverse, green * inverse + saturation, blue * inverse, 0F, 0F,
                red * inverse, green * inverse, blue * inverse + saturation, 0F, 0F,
                0F, 0F, 0F, 1F, 0F
        });
    }

    public record Target(float radius) {
        public Target(float radius) {
            this.radius = Math.min(20F, radius);
        }
    }

    @Override
    public void close() {
        super.close();
        blur.setImageFilter(null);
        blur.setColorFilter(null);
        if (blurFilter != null && !blurFilter.isClosed()) blurFilter.close();
        if (saturationFilter != null && !saturationFilter.isClosed()) saturationFilter.close();
        if (!blur.isClosed()) blur.close();
        blurFilter = null;
        saturationFilter = null;
    }
}
