package cc.nimbus.utils.render.skija.internal.effect.surface;

import cc.nimbus.utils.render.skija.internal.image.minecraft.MinecraftSnapshot;
import io.github.humbleui.skija.*;
import io.github.humbleui.types.Rect;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@SuppressWarnings("ExtractMethodRecommender")
public final class GaussianSurface extends AbstractOffSurface<GaussianSurface.Target> {
    private final Paint blur = new Paint();
    private final Paint paint = new Paint();

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

        float saturation = 1.F + target.radius() / 120;

        float rw = 0.2126f;
        float gw = 0.7152f;
        float bw = 0.0722f;

        float invS = 1.0f - saturation;

        float rR = rw * invS + saturation;
        float rG = gw * invS;
        float rB = bw * invS;

        float gR = rw * invS;
        float gG = gw * invS + saturation;
        float gB = bw * invS;

        float bR = rw * invS;
        float bG = gw * invS;
        float bB = bw * invS + saturation;

        float[] colorMat = new float[] {
                rR, rG, rB, 0f, 0f,
                gR, gG, gB, 0f, 0f,
                bR, bG, bB, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
        };

        ColorMatrix colorMatrix = new ColorMatrix(colorMat);

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
            saturationFilter = ColorFilter.makeMatrix(colorMatrix);
        }

        this.blur.setImageFilter(blurFilter);
        this.blur.setColorFilter(saturationFilter);

        int count = canvas.saveLayer(null, blur);

        try {
            if (srcs != null && !srcs.isEmpty()) {
                float samplingMargin = target.radius() * 3F;
                for (Rect src : srcs) {
                    Rect samplingBounds = src.inflate(samplingMargin);
                    canvas.drawImageRect(snapshot, samplingBounds, samplingBounds, paint);
                }
            } else {
                canvas.drawImage(snapshot, 0, 0, paint);
            }
        } finally {
            canvas.restoreToCount(count);
        }

        lastRadius = target.radius();
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
        if (!paint.isClosed()) paint.close();
        blurFilter = null;
        saturationFilter = null;
    }
}
