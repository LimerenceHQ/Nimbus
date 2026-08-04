package cc.nimbus.ui.clickgui.tool.music.coverflow;

public final class CoverFlowState {
    private float position;
    private float target;
    private float lastPointerX;
    private float releaseVelocity;
    private float dragDistance;
    private boolean dragging;

    public void update(float deltaSeconds, int itemCount) {
        clamp(itemCount);
        if (dragging) return;
        float speed = 1f - (float) Math.exp(-Math.max(0f, deltaSeconds) * 11f);
        position += (target - position) * speed;
        if (Math.abs(position - target) < .001f) position = target;
    }

    public void select(int index, int itemCount) {
        target = bounded(index, itemCount);
        releaseVelocity = 0f;
    }

    public void scroll(double amount, int itemCount) {
        target = bounded(target - (float) amount, itemCount);
        releaseVelocity = 0f;
    }

    public void beginDrag(float pointerX) {
        dragging = true;
        lastPointerX = pointerX;
        releaseVelocity = 0f;
        dragDistance = 0f;
    }

    public void drag(float pointerX, float itemSpacing, int itemCount) {
        if (!dragging) return;
        float delta = pointerX - lastPointerX;
        lastPointerX = pointerX;
        dragDistance += Math.abs(delta);
        float step = Math.max(1f, itemSpacing);
        releaseVelocity = -delta / step;
        position = bounded(position + releaseVelocity, itemCount);
        target = position;
    }

    public boolean endDrag(int itemCount) {
        if (!dragging) return false;
        dragging = false;
        boolean click = dragDistance < 4f;
        target = bounded(Math.round(position + releaseVelocity * 1.6f), itemCount);
        releaseVelocity = 0f;
        return click;
    }

    public void reset() {
        position = 0f;
        target = 0f;
        releaseVelocity = 0f;
        dragDistance = 0f;
        dragging = false;
    }

    public float position() { return position; }
    public int selectedIndex(int itemCount) { return Math.round(bounded(target, itemCount)); }
    public boolean dragging() { return dragging; }

    private void clamp(int itemCount) {
        position = bounded(position, itemCount);
        target = bounded(target, itemCount);
    }

    private static float bounded(float value, int itemCount) {
        return Math.clamp(value, 0f, Math.max(0, itemCount - 1));
    }
}
