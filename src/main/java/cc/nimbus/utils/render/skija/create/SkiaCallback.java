package cc.nimbus.utils.render.skija.create;

import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import org.jetbrains.annotations.Nullable;

@FunctionalInterface
public interface SkiaCallback {
    void call(Canvas canvas, float width, float height, float mouseX, float mouseY, @Nullable Paint paint);
}