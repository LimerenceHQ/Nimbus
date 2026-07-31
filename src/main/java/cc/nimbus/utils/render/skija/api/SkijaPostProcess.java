package cc.nimbus.utils.render.skija.api;

import cc.nimbus.utils.render.skija.internal.effect.post.PostProcessPipeline;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Path;
import io.github.humbleui.types.Rect;
import io.github.humbleui.types.RRect;

public final class SkijaPostProcess {
    private final Canvas canvas;
    private final SkijaRenderer renderer;

    SkijaPostProcess(Canvas canvas, SkijaRenderer renderer) {
        this.canvas = canvas;
        this.renderer = renderer;
    }

    public void add(Rect rectangle) {
        PostProcessPipeline.add(rectangle);
    }

    public void add(RRect rectangle) {
        PostProcessPipeline.add(rectangle);
    }

    public void add(Path path) {
        PostProcessPipeline.add(path);
    }

    public void draw() {
        if (canvas == null) throw new IllegalStateException("Post-processing can only be drawn inside a Skija frame");
        PostProcessPipeline.drawAll(canvas, renderer.postProcessOptions());
    }
}
