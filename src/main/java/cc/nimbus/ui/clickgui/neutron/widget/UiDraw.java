package cc.nimbus.ui.clickgui.neutron.widget;

import cc.nimbus.utils.render.skija.api.SkijaFrame;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;

public final class UiDraw {
    private UiDraw() {
    }

    public static void fillRect(SkijaFrame frame, float x, float y, float w, float h, int argb) {
        UiPaints.FILL.setColor(argb);
        frame.draw().rect(x, y, w, h, UiPaints.FILL);
    }

    public static void fillRRect(SkijaFrame frame, float x, float y, float w, float h, float r, int argb) {
        UiPaints.FILL.setColor(argb);
        frame.draw().roundedRect(x, y, w, h, r, UiPaints.FILL);
    }

    public static void strokeRRect(SkijaFrame frame, float x, float y, float w, float h, float r, float strokeWidth, int argb) {
        UiPaints.STROKE.setColor(argb);
        UiPaints.STROKE.setStrokeWidth(strokeWidth);
        frame.draw().outline(x, y, w, h, r, UiPaints.STROKE);
    }

    public static void clipRRect(SkijaFrame frame, float x, float y, float w, float h, float r) {
        frame.draw().clipSmoothRoundedRect(x, y, w, h, r);
    }

    public static void snowflake(SkijaFrame frame, float x, float y, float radius, Paint paint) {
        int save = frame.canvas().save();
        try {
            frame.canvas().translate(x, y);
            for (int index = 0; index < 6; index++) {
                frame.canvas().drawLine(0, 0, 0, -radius, paint);
                float branchDistance = radius * 0.6f;
                float branchSize = radius * 0.35f;
                frame.canvas().drawLine(0, -branchDistance,
                        -branchSize, -branchDistance - branchSize * 0.5f, paint);
                frame.canvas().drawLine(0, -branchDistance,
                        branchSize, -branchDistance - branchSize * 0.5f, paint);
                frame.canvas().rotate(60f);
            }
        } finally {
            frame.canvas().restoreToCount(save);
        }
    }

    public static void drawImageRRect(SkijaFrame frame, Image img, float x, float y, float w, float h, float r, int placeholderArgb) {
        float px = (float) Math.round(x);
        float py = (float) Math.round(y);
        if (img == null || img.isClosed()) {
            fillRRect(frame, px, py, w, h, r, placeholderArgb);
            return;
        }

        int save = frame.canvas().save();
        try {
            clipRRect(frame, px, py, w, h, r);
            frame.draw().image(img, px, py, w, h, UiPaints.IMAGE);
        } finally {
            frame.canvas().restoreToCount(save);
        }
    }
}
