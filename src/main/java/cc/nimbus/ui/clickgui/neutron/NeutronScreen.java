package cc.nimbus.ui.clickgui.neutron;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import cc.nimbus.core.Core;
import cc.nimbus.utils.render.skija.api.SkijaLayer;
import cc.nimbus.ui.clickgui.neutron.widget.UiPaints;

public final class NeutronScreen extends Screen {
    private final SkijaLayer layer;
    private final NeutronUI ui = new NeutronUI();
    private double mouseX;
    private double mouseY;
    private boolean leftDown;
    private boolean rightDown;
    private boolean leftClicked;
    private boolean rightClicked;
    private double scrollDelta;
    private int extractedFramebufferWidth;
    private int extractedFramebufferHeight;
    private float extractedMouseX;
    private float extractedMouseY;
    private boolean extractedLeftDown;
    private boolean extractedRightDown;
    private boolean extractedLeftClicked;
    private boolean extractedRightClicked;
    private double extractedScrollDelta;
    private boolean framePending;
    private boolean resourcesReleased;

    public NeutronScreen() {
        super(Component.literal("Nimbus Neutron"));
        UiPaints.open();
        layer = Core.INSTANCE.getRenderManager().skija().layer("neutron");
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        int framebufferWidth = minecraft.getWindow().getWidth();
        int framebufferHeight = minecraft.getWindow().getHeight();
        layer.extract(graphics, framebufferWidth, framebufferHeight);

        extractedFramebufferWidth = framebufferWidth;
        extractedFramebufferHeight = framebufferHeight;
        extractedMouseX = toFramebufferX(this.mouseX, graphics.guiWidth(), framebufferWidth);
        extractedMouseY = toFramebufferY(this.mouseY, graphics.guiHeight(), framebufferHeight);
        extractedLeftDown = leftDown;
        extractedRightDown = rightDown;
        extractedLeftClicked = leftClicked;
        extractedRightClicked = rightClicked;
        extractedScrollDelta = scrollDelta;
        framePending = true;

        scrollDelta = 0;
        leftClicked = false;
        rightClicked = false;
    }

    public void renderExtractedFrame() {
        if (!framePending) return;
        framePending = false;
        layer.clear();
        layer.render(frame -> ui.render("Neutron", frame,
                extractedFramebufferWidth, extractedFramebufferHeight,
                extractedMouseX, extractedMouseY,
                extractedLeftDown, extractedRightDown,
                extractedLeftClicked, extractedRightClicked,
                extractedScrollDelta));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (minecraft.level == null) {
            extractPanorama(graphics, partialTick);
        }
    }

    @Override public void mouseMoved(double x, double y) { mouseX=x; mouseY=y; }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        mouseX=event.x(); mouseY=event.y();
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) { leftDown=true; leftClicked=true; return true; }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) { rightDown=true; rightClicked=true; return true; }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            int framebufferWidth = minecraft.getWindow().getWidth();
            int framebufferHeight = minecraft.getWindow().getHeight();
            return ui.middleClick(
                    toFramebufferX(mouseX, width, framebufferWidth),
                    toFramebufferY(mouseY, height, framebufferHeight)
            );
        }
        return super.mouseClicked(event,doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) { leftDown=false; return true; }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) { rightDown=false; return true; }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        mouseX=x; mouseY=y; scrollDelta += vertical; return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (ui.handleKeyInput(event.key(),event.scancode(),event.modifiers())) return true;
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        char[] chars=Character.toChars(event.codepoint());
        boolean handled=false;
        for(char value:chars) handled |= ui.handleCharInput(value,0);
        return handled || super.charTyped(event);
    }

    @Override public boolean preeditUpdated(PreeditEvent event) { return false; }
    @Override public boolean isPauseScreen() { return false; }
    @Override
    public void removed() {
        if (!resourcesReleased) {
            resourcesReleased = true;
            layer.close();
            UiPaints.close();
        }
        super.removed();
    }

    private static float toFramebufferX(double x, int guiWidth, int framebufferWidth) {
        return guiWidth > 0 ? (float) (x * framebufferWidth / guiWidth) : (float) x;
    }

    private static float toFramebufferY(double y, int guiHeight, int framebufferHeight) {
        return guiHeight > 0 ? (float) (y * framebufferHeight / guiHeight) : (float) y;
    }

}
