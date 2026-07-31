package cc.nimbus.ui.clickgui.neutron.widget;

import io.github.humbleui.skija.Paint;

public final class UiPaints {
    public static Paint FILL;
    public static Paint STROKE;
    public static Paint IMAGE;
    public static Paint TEXT;
    private static int users;

    private UiPaints() {
    }

    public static synchronized void open() {
        users++;
        if (FILL == null || FILL.isClosed()) FILL = new Paint().setAntiAlias(true);
        if (STROKE == null || STROKE.isClosed()) STROKE = new Paint().setAntiAlias(true).setStroke(true);
        if (IMAGE == null || IMAGE.isClosed()) IMAGE = new Paint().setAntiAlias(true);
        if (TEXT == null || TEXT.isClosed()) TEXT = new Paint().setAntiAlias(true);
    }

    public static synchronized void close() {
        if (users == 0 || --users > 0) return;
        close(FILL);
        close(STROKE);
        close(IMAGE);
        close(TEXT);
        FILL = STROKE = IMAGE = TEXT = null;
    }

    private static void close(Paint paint) {
        if (paint != null && !paint.isClosed()) paint.close();
    }
}
