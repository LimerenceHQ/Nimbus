package cc.nimbus.ui.clickgui.neutron.panel;

import io.github.humbleui.skija.Canvas;

public abstract class NeutronPanel {
    protected float x;
    protected float y;
    protected float width;
    protected float height;

    public void setBounds(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public boolean contains(float mx, float my) {
        return mx >= x && my >= y && mx <= x + width && my <= y + height;
    }

    public abstract void render(Canvas canvas, float mx, float my, boolean leftDown, boolean rightDown, boolean leftPressed, double scrolledDelta);
}
