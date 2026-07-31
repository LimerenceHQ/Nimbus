package cc.nimbus.utils.render.skija.draw;

import cc.nimbus.utils.render.skija.image.SkijaImages;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintStrokeCap;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;
import net.minecraft.resources.Identifier;

public final class SkijaDraw {
    private final Canvas canvas;

    public SkijaDraw(Canvas canvas) {
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

    public void outline(float x, float y, float width, float height, float radius, float strokeWidth, int color) {
        try (Paint paint = fill(color).setStroke(true).setStrokeWidth(strokeWidth)) {
            canvas.drawRRect(RRect.makeXYWH(x, y, width, height, radius), paint);
        }
    }

    public void line(float x1, float y1, float x2, float y2, float width, int color) {
        try (Paint paint = fill(color).setStrokeWidth(width).setStrokeCap(PaintStrokeCap.ROUND)) {
            canvas.drawLine(x1, y1, x2, y2, paint);
        }
    }

    public void resource(Identifier id, float x, float y, float width, float height) {
        image(SkijaImages.getResourceImage(id), x, y, width, height);
    }

    public void minecraftTexture(Identifier id, float x, float y, float width, float height) {
        image(SkijaImages.getGlImage(id), x, y, width, height);
    }

    public void image(Image image, float x, float y, float width, float height) {
        if (image == null || image.isClosed()) return;
        canvas.drawImageRect(image, Rect.makeXYWH(x, y, width, height));
    }

    private static Paint fill(int color) {
        return new Paint().setAntiAlias(true).setColor(color);
    }
}
