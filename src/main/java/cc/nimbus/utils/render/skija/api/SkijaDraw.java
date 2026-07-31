package cc.nimbus.utils.render.skija.api;

import cc.nimbus.utils.render.skija.internal.image.ImageStore;
import cc.nimbus.utils.render.skija.internal.geometry.gn.SmoothRectSpec;
import cc.nimbus.utils.render.skija.internal.geometry.gn.SmoothRectPathCache;
import cc.nimbus.utils.render.skija.internal.image.SvgIconStore;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ClipMode;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintStrokeCap;
import io.github.humbleui.skija.Path;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;
import net.minecraft.resources.Identifier;

public final class SkijaDraw {
    private final Canvas canvas;

    SkijaDraw(Canvas canvas) {
        this.canvas = canvas;
    }

    public void rect(float x, float y, float width, float height, int color) {
        try (Paint paint = fill(color)) {
            canvas.drawRect(Rect.makeXYWH(x, y, width, height), paint);
        }
    }

    public void roundedRect(float x, float y, float width, float height, float radius, int color) {
        try (Paint paint = fill(color)) {
            canvas.drawRRect(RRect.makeXYWH(x, y, width, height, radius), paint);
        }
    }

    public void roundedRect(float x, float y, float width, float height, float radius, Paint paint) {
        canvas.drawRRect(RRect.makeXYWH(x, y, width, height, radius), paint);
    }

    public void smoothRoundedRect(float x, float y, float width, float height, float radius, Paint paint) {
        try (Path path = smoothPath(x, y, width, height, radius)) {
            canvas.drawPath(path, paint);
        }
    }

    public void smoothOutline(float x, float y, float width, float height, float radius, Paint paint) {
        try (Path path = smoothPath(x, y, width, height, radius)) {
            canvas.drawPath(path, paint);
        }
    }

    public void clipSmoothRoundedRect(float x, float y, float width, float height, float radius) {
        if (radius <= 0f) {
            canvas.clipRect(Rect.makeXYWH(x, y, width, height), ClipMode.INTERSECT, true);
            return;
        }
        try (Path path = smoothPath(x, y, width, height, radius)) {
            canvas.clipPath(path, ClipMode.INTERSECT, true);
        }
    }

    public void rect(float x, float y, float width, float height, Paint paint) {
        canvas.drawRect(Rect.makeXYWH(x, y, width, height), paint);
    }

    public void image(Image image, float x, float y, float width, float height, Paint paint) {
        if (image == null || image.isClosed()) return;
        canvas.drawImageRect(image, Rect.makeWH(image.getWidth(), image.getHeight()),
                Rect.makeXYWH(x, y, width, height), io.github.humbleui.skija.SamplingMode.LINEAR, paint, true);
    }

    public void svg(String content, float x, float y, float size, Paint tint) {
        SvgIconStore.draw(canvas, content, x, y, size, tint);
    }

    public void outline(float x, float y, float width, float height, float radius, float strokeWidth, int color) {
        try (Paint paint = fill(color).setStroke(true).setStrokeWidth(strokeWidth)) {
            canvas.drawRRect(RRect.makeXYWH(x, y, width, height, radius), paint);
        }
    }

    public void outline(float x, float y, float width, float height, float radius, Paint paint) {
        canvas.drawRRect(RRect.makeXYWH(x, y, width, height, radius), paint);
    }

    public void line(float x1, float y1, float x2, float y2, float width, int color) {
        try (Paint paint = fill(color).setStrokeWidth(width).setStrokeCap(PaintStrokeCap.ROUND)) {
            canvas.drawLine(x1, y1, x2, y2, paint);
        }
    }

    public void resource(Identifier id, float x, float y, float width, float height) {
        image(ImageStore.getResourceImage(id), x, y, width, height);
    }

    public void minecraftTexture(Identifier id, float x, float y, float width, float height) {
        image(ImageStore.getGlImage(id), x, y, width, height);
    }

    public void image(Image image, float x, float y, float width, float height) {
        if (image == null || image.isClosed()) return;
        canvas.drawImageRect(image, Rect.makeXYWH(x, y, width, height));
    }

    private static Paint fill(int color) {
        return new Paint().setAntiAlias(true).setColor(color);
    }

    private static Path smoothPath(float x, float y, float width, float height, float radius) {
        float maxRadius = Math.min(width, height) * 0.5f;
        float normalizedRadius = maxRadius <= 0f ? 0f : Math.clamp(radius / maxRadius, 0f, 1f);
        return SmoothRectPathCache.translated(new SmoothRectSpec(width, height, normalizedRadius, 1f), x, y);
    }
}
