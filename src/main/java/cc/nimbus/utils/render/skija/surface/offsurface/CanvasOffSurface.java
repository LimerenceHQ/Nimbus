package cc.nimbus.utils.render.skija.surface.offsurface;

import io.github.humbleui.skija.*;
import io.github.humbleui.types.Rect;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

public final class CanvasOffSurface extends AbstractOffSurface<Consumer<Canvas>> {
    public CanvasOffSurface(int targetFps) {
        super(targetFps);
    }

    @Override
    protected ImageInfo imageInfo(int width, int height, Consumer<Canvas> target) {
        return ImageInfo.makeN32Premul(width, height);
    }

    @Override
    protected void onCanvas(DirectContext context, Surface surface, Canvas canvas, @Nullable Path path, @Nullable List<Rect> srcs, @Nullable Consumer<Canvas> target) {
        if (target != null) {
            target.accept(canvas);
        }
    }
}
