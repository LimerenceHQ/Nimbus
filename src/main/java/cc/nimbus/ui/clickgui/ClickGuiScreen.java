package cc.nimbus.ui.clickgui;

import cc.nimbus.core.Core;
import cc.nimbus.ui.clickgui.component.UiContext;
import cc.nimbus.ui.clickgui.theme.ClickGuiTheme;
import cc.nimbus.ui.screen.AbstractSkijaScreen;
import cc.nimbus.utils.render.skija.api.SkijaFrame;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;
import net.minecraft.client.gui.GuiGraphics;
import cc.nimbus.ui.input.PreeditEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class ClickGuiScreen extends AbstractSkijaScreen {
    private static final float PREFERRED_UI_SCALE = 1.34f;
    private static final long ENTER_NANOS = 340_000_000L;
    private static final long EXIT_NANOS = 280_000_000L;
    private final ClickGuiView view;
    private final long openedAtNanos = System.nanoTime();
    private long lastFrameNanos = System.nanoTime();
    private long closingAtNanos;
    private boolean closeScheduled;
    private boolean closeReady;
    private float scale = 1f;
    private float originX;
    private float originY;
    private float logicalWidth;
    private float logicalHeight;
    private UiContext lastContext;

    public ClickGuiScreen(ClickGuiSession session) {
        super(Component.literal("Nimbus ClickGUI"), "click-gui");
        view = new ClickGuiView(session);
    }

    @Override
    protected void renderSkija(SkijaFrame frame) {
        int width = framebufferWidth();
        int height = framebufferHeight();
        logicalWidth = Math.max(width / PREFERRED_UI_SCALE, ClickGuiView.MIN_WIDTH);
        logicalHeight = Math.max(height / PREFERRED_UI_SCALE, ClickGuiView.MIN_HEIGHT);
        scale = Math.min(PREFERRED_UI_SCALE, Math.min(width / logicalWidth, height / logicalHeight));
        originX = (width - logicalWidth * scale) * .5f;
        originY = (height - logicalHeight * scale) * .5f;
        long now = System.nanoTime();
        float delta = Math.clamp((now - lastFrameNanos) / 1_000_000_000f, 0f, .05f);
        lastFrameNanos = now;
        float closeProgress = closeProgress(now);
        float visibility = visibility(now, closeProgress);
        float offsetX = closingAtNanos == 0L ? 0f : -18f * smooth(closeProgress);
        float offsetY = closingAtNanos == 0L ? (1f - visibility) * 12f : 0f;

        frame.postProcess().add(RRect.makeXYWH(0f, 0f, width, height, 0f));
        frame.postProcess().draw();

        Canvas canvas = frame.canvas();
        int save = canvas.save();
        try {
            canvas.translate(originX, originY);
            canvas.scale(scale, scale);
            int layer = canvas.saveLayerAlpha(Rect.makeXYWH(0f, 0f, logicalWidth, logicalHeight),
                    Math.round(255f * visibility));
            canvas.translate(offsetX, offsetY);
            lastContext = new UiContext(frame,
                    ClickGuiTheme.from(Core.INSTANCE.getConfigManager().clientSettings()),
                    logicalX(framebufferMouseX()) - offsetX, logicalY(framebufferMouseY()) - offsetY, delta,
                    System.currentTimeMillis(), logicalWidth, logicalHeight);
            try {
                view.render(lastContext, logicalWidth, logicalHeight, closeProgress);
            } finally {
                canvas.restoreToCount(layer);
            }
        } finally {
            canvas.restoreToCount(save);
        }
        if (closingAtNanos != 0L && closeProgress >= 1f) closeReady = true;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (!acceptsInput() || lastContext == null) return true;
        return view.mouseClicked(logicalX(framebufferX(x)), logicalY(framebufferY(y)), button, lastContext);
    }

    @Override public void mouseMoved(double x, double y) {
        if (acceptsInput()) view.mouseMoved(logicalX(framebufferX(x)), logicalY(framebufferY(y)));
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (acceptsInput()) view.mouseReleased();
        return true;
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (!acceptsInput()) return true;
        return view.mouseScrolled(logicalX(framebufferX(x)), logicalY(framebufferY(y)), vertical);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (closingAtNanos != 0L) return true;
        if (keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) { requestClose(); return true; }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (!view.cancelTransient()) requestClose();
            return true;
        }
        return view.keyPressed(keyCode, scanCode, modifiers) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public boolean charTyped(char codePoint, int modifiers) {
        return !acceptsInput() || view.charTyped(codePoint, modifiers) || super.charTyped(codePoint, modifiers);
    }
    public boolean preeditUpdated(PreeditEvent event) {
        return !acceptsInput() || view.preeditUpdated(event);
    }
    @Override public void onClose() { requestClose(); }
    @Override protected void extractedSkijaFramePresented() {
        if (!closeReady || closeScheduled) return;
        closeScheduled = true;
        minecraft.schedule(this::finishClose);
    }
    @Override protected void removedSkijaScreen() { view.close(); }
    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    private float logicalX(float value) { return (value - originX) / scale; }
    private float logicalY(float value) { return (value - originY) / scale; }

    private boolean acceptsInput() {
        return closingAtNanos == 0L && System.nanoTime() - openedAtNanos >= ENTER_NANOS * 3L / 4L;
    }

    private void requestClose() {
        if (closingAtNanos == 0L) closingAtNanos = System.nanoTime();
    }

    private void finishClose() {
        if (minecraft.screen == this) super.onClose();
    }

    private float visibility(long now, float closeProgress) {
        if (closingAtNanos != 0L) {
            return 1f - smooth(Math.clamp((closeProgress - .68f) / .32f, 0f, 1f));
        }
        return easeOutCubic(Math.clamp((now - openedAtNanos) / (float) ENTER_NANOS, 0f, 1f));
    }

    private float closeProgress(long now) {
        if (closingAtNanos == 0L) return 0f;
        return Math.clamp((now - closingAtNanos) / (float) EXIT_NANOS, 0f, 1f);
    }

    private static float smooth(float value) {
        return value * value * (3f - 2f * value);
    }

    private static float easeOutCubic(float value) {
        float inverse = 1f - value;
        return 1f - inverse * inverse * inverse;
    }
}
