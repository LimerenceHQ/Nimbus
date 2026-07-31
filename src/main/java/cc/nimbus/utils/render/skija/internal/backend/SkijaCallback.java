package cc.nimbus.utils.render.skija.internal.backend;

import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import org.jetbrains.annotations.Nullable;

@FunctionalInterface
public interface SkijaCallback {
    void call(Canvas canvas, float width, float height, @Nullable Paint paint);
}
