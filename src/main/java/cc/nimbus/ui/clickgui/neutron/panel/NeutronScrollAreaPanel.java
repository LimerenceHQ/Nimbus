package cc.nimbus.ui.clickgui.neutron.panel;

import cc.nimbus.ui.clickgui.neutron.widget.UiDraw;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.types.Rect;

public abstract class NeutronScrollAreaPanel extends NeutronPanel {
    protected float scrollY;
    protected float scrollVel;
    protected float scrollTargetY;
    private long lastUpdateMs;
    protected float contentHeight;
    protected float radius = 12f;
    protected float scrollStep = 36f;

    @Override
    public final void render(Canvas canvas, float mx, float my, boolean leftDown, boolean rightDown, boolean leftPressed, double scrolledDelta) {
        if (scrolledDelta != 0 && contains(mx, my)) {
            scrollTargetY += (float) (-scrolledDelta * scrollStep);
        }
        updateScroll();
        float maxScroll = Math.max(0f, contentHeight - height);
        float renderScrollY = Math.max(0f, Math.min(maxScroll, (float) Math.round(scrollY)));
        canvas.save();
        UiDraw.clipRRect(canvas, x, y, width, height, radius);
        canvas.translate(0, -renderScrollY);
        renderContent(canvas, mx, my + renderScrollY, leftDown, rightDown, leftPressed);
        canvas.restore();

        if (contentHeight > height) {
            float barH = Math.max(22f, height * (height / contentHeight));
            float denom = Math.max(1f, maxScroll);
            float barY = y + (height - barH) * (renderScrollY / denom);
            UiDraw.fillRRect(canvas, x + width - 5f, barY, 3f, barH, 1.5f, 0x33FFFFFF);
        }
    }

    protected abstract void renderContent(Canvas canvas, float mx, float my, boolean leftDown, boolean rightDown, boolean leftPressed);

    private void updateScroll() {
        long now = System.currentTimeMillis();
        if (lastUpdateMs == 0L) {
            lastUpdateMs = now;
            scrollTargetY = scrollY;
            return;
        }
        float dt = (now - lastUpdateMs) / 1000f;
        lastUpdateMs = now;
        if (dt < 0f) dt = 0f;
        if (dt > 0.05f) dt = 0.05f;

        float max = Math.max(0f, contentHeight - height);
        if (max <= 0f) {
            scrollY = 0f;
            scrollVel = 0f;
            scrollTargetY = 0f;
            return;
        }

        if (scrollTargetY < 0f) scrollTargetY = 0f;
        if (scrollTargetY > max) scrollTargetY = max;
        if (scrollY < 0f) scrollY = 0f;
        if (scrollY > max) scrollY = max;

        if (dt <= 0f) return;

        float smoothTime = 0.14f;
        float omega = 2f / smoothTime;
        float x = omega * dt;
        float exp = 1f / (1f + x + 0.48f * x * x + 0.235f * x * x * x);

        float change = scrollY - scrollTargetY;
        float originalTo = scrollTargetY;

        float maxSpeed = 6000f;
        float maxChange = maxSpeed * smoothTime;
        if (change > maxChange) change = maxChange;
        if (change < -maxChange) change = -maxChange;

        float target = scrollY - change;
        float temp = (scrollVel + omega * change) * dt;
        scrollVel = (scrollVel - omega * temp) * exp;
        float output = target + (change + temp) * exp;

        boolean overshot = (originalTo - scrollY > 0f) == (output > originalTo);
        if (overshot) {
            output = originalTo;
            scrollVel = 0f;
        }

        if (Math.abs(output - scrollTargetY) < 0.05f && Math.abs(scrollVel) < 1f) {
            output = scrollTargetY;
            scrollVel = 0f;
        }

        scrollY = output;
    }

    protected Rect viewport() {
        return Rect.makeXYWH(x, y, width, height);
    }
}
