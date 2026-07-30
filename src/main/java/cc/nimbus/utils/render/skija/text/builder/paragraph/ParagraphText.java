package cc.nimbus.utils.render.skija.text.builder.paragraph;

import io.github.humbleui.skija.Paint;

public class ParagraphText implements AutoCloseable {
    private String family;
    private float size;
    private String text;
    private Paint paint;

    private boolean hasFamily;
    private boolean hasSize;

    public ParagraphText() {
    }

    public String getFamily() {
        return family;
    }

    public void setFamily(String family) {
        this.family = family;
        this.hasFamily = true;
    }

    public boolean hasFamily() {
        return hasFamily;
    }

    public float getSize() {
        return size;
    }

    public void setSize(float size) {
        this.size = size;
        this.hasSize = true;
    }

    public boolean hasSize() {
        return hasSize;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Paint getPaint() {
        return paint;
    }

    public void setPaint(Paint paint) {
        this.paint = paint;
    }

    @Override
    public void close() {
        family = null;
        size = -1;
        text = null;
        if (paint != null && !paint.isClosed()) {
            paint.close();
            paint = null;
        }
    }
}
