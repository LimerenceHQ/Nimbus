package cc.nimbus.ui.clickgui.neutron.widget;

import cc.nimbus.utils.render.skija.path.gn.Gn;
import cc.nimbus.utils.render.skija.path.gn.GnBuilder;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.types.Rect;
import net.minecraft.resources.Identifier;

public final class UiDraw {
    private UiDraw() {
    }

    private static float radiusFromPx(float w, float h, float px) {
        float maxR = Math.min(w, h) * 0.5F;
        if (maxR <= 0F) return 0F;
        float v = px / maxR;
        if (v < 0F) return 0F;
        if (v > 1F) return 1F;
        return v;
    }

    public static void fillRect(Canvas canvas, float x, float y, float w, float h, int argb) {
        UiPaints.FILL.setColor(argb);
        canvas.drawRect(Rect.makeXYWH(x, y, w, h), UiPaints.FILL);
    }

    public static void fillRRect(Canvas canvas, float x, float y, float w, float h, float r, int argb) {
        UiPaints.FILL.setColor(argb);
        Gn gn = new Gn(w, h, radiusFromPx(w, h, r), 1f);
        try (Path path = GnBuilder.getPath(gn, x, y)) {
            canvas.drawPath(path, UiPaints.FILL);
        }
    }

    public static void strokeRRect(Canvas canvas, float x, float y, float w, float h, float r, float strokeWidth, int argb) {
        UiPaints.STROKE.setColor(argb);
        UiPaints.STROKE.setStrokeWidth(strokeWidth);
        Gn gn = new Gn(w, h, radiusFromPx(w, h, r), 1f);
        try (Path path = GnBuilder.getPath(gn, x, y)) {
            canvas.drawPath(path, UiPaints.STROKE);
        }
    }

    public static void clipRRect(Canvas canvas, float x, float y, float w, float h, float r) {
        if (r <= 0f) return;
        Gn gn = new Gn(w, h, radiusFromPx(w, h, r), 1f);
        try (Path path = GnBuilder.getPath(gn, x, y)) {
            canvas.clipPath(path, true);
        }
    }

    public static void drawImageRRect(Canvas canvas, Identifier loc, Image img, float x, float y, float w, float h, float r, int placeholderArgb, long fadeMs) {
        float px = (float) Math.round(x);
        float py = (float) Math.round(y);
        if (img == null || img.isClosed()) {
            fillRRect(canvas, px, py, w, h, r, placeholderArgb);
            return;
        }

        canvas.save();
        clipRRect(canvas, px, py, w, h, r);
        canvas.drawImageRect(img, Rect.makeWH(img.getWidth(), img.getHeight()), Rect.makeXYWH(px, py, w, h), SamplingMode.LINEAR, UiPaints.IMAGE, true);
        canvas.restore();
    }
}
