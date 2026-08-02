package cc.nimbus.ui.clickgui;

import cc.nimbus.core.Core;
import cc.nimbus.ui.clickgui.component.UiContext;
import cc.nimbus.ui.clickgui.theme.ClickGuiTheme;
import cc.nimbus.ui.screen.AbstractSkijaScreen;
import cc.nimbus.utils.render.skija.api.SkijaFrame;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.types.RRect;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class ClickGuiScreen extends AbstractSkijaScreen {
    private static final float PREFERRED_UI_SCALE = 1.34f;
    private final ClickGuiView view;
    private long lastFrameNanos = System.nanoTime();
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

        frame.postProcess().add(RRect.makeXYWH(0f, 0f, width, height, 0f));
        frame.postProcess().draw();

        Canvas canvas = frame.canvas();
        int save = canvas.save();
        try {
            canvas.translate(originX, originY);
            canvas.scale(scale, scale);
            lastContext = new UiContext(frame,
                    ClickGuiTheme.from(Core.INSTANCE.getConfigManager().clientSettings()),
                    logicalX(framebufferMouseX()), logicalY(framebufferMouseY()), delta,
                    System.currentTimeMillis());
            view.render(lastContext, logicalWidth, logicalHeight);
        } finally {
            canvas.restoreToCount(save);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (lastContext == null) return true;
        return view.mouseClicked(logicalX(framebufferX(event.x())), logicalY(framebufferY(event.y())),
                event.button(), lastContext);
    }

    @Override public void mouseMoved(double x, double y) { view.mouseMoved(logicalX(framebufferX(x)), logicalY(framebufferY(y))); }
    @Override public boolean mouseReleased(MouseButtonEvent event) { view.mouseReleased(); return true; }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return view.mouseScrolled(logicalX(framebufferX(x)), logicalY(framebufferY(y)), vertical);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_RIGHT_SHIFT) { onClose(); return true; }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (!view.cancelTransient()) onClose();
            return true;
        }
        return view.keyPressed(event) || super.keyPressed(event);
    }

    @Override public boolean charTyped(CharacterEvent event) { return view.charTyped(event) || super.charTyped(event); }
    @Override public boolean preeditUpdated(PreeditEvent event) { return view.preeditUpdated(event) || super.preeditUpdated(event); }
    @Override protected void removedSkijaScreen() { view.close(); }
    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Nimbus post-processing is the only background effect for this screen.
    }

    private float logicalX(float value) { return (value - originX) / scale; }
    private float logicalY(float value) { return (value - originY) / scale; }
}
