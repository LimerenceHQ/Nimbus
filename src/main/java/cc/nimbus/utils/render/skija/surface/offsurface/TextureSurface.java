package cc.nimbus.utils.render.skija.surface.offsurface;

import cc.nimbus.utils.render.skija.create.Skia;
import cc.nimbus.utils.render.skija.image.SvgFactory;
import io.github.humbleui.skija.*;
import io.github.humbleui.types.Rect;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@SuppressWarnings("SpellCheckingInspection")
public final class TextureSurface extends AbstractOffSurface<TextureSurface.Target> {
    public TextureSurface(int targetFps) {
        super(targetFps);
    }

    @Override
    public boolean shouldUpdate(long nowMs, float currentFps) {
        Image cached = this.getSnapshot();
        if (cached == null) {
            return true;
        }

        int cachedWidth = cached.getImageInfo().getWidth();
        int cachedHeight = cached.getImageInfo().getHeight();

        int currentWidth = Skia.INSTANCE.getCurrentWidth();
        int currentHeight = Skia.INSTANCE.getCurrentHeight();

        return cachedWidth < currentWidth || cachedHeight < currentHeight;
    }

    @Override
    protected ImageInfo imageInfo(int width, int height, Target target) {
        return ImageInfo.makeN32Premul(width, height);
    }

    @Override
    protected void onCanvas(DirectContext context, Surface surface, Canvas canvas, @Nullable Path path, @Nullable List<Rect> srcs, @Nullable Target target) {
        if (target == null) {
            return;
        }

        float size = 600;
        try (Paint paint = new Paint()) {
            paint.setColor(target.color);
            paint.setAlpha(target.alpha);
            paint.setAntiAlias(true);

            for (float y = 0; y < Skia.INSTANCE.getCurrentHeight(); y += size) {
                for (float x = 0; x < Skia.INSTANCE.getCurrentWidth(); x += size) {
                    SvgFactory.drawSvgFromImage(canvas, "assets/nimbus/image/topography.svg", x, y, size, paint);
                }
            }
        }
    }

    public record Target(int color, int alpha) {
    }
}
