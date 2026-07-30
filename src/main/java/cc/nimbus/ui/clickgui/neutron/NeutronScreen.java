package cc.nimbus.ui.clickgui.neutron;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import cc.nimbus.utils.render.skija.SkijaRenderTarget;
import cc.nimbus.utils.render.Render2DUtils;

public final class NeutronScreen extends Screen {
    private static final SkijaRenderTarget TARGET = new SkijaRenderTarget("neutron");
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

    public NeutronScreen() { super(Component.literal("Nimbus Neutron")); }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        int framebufferWidth = minecraft.getWindow().getWidth();
        int framebufferHeight = minecraft.getWindow().getHeight();
        TARGET.ensure(framebufferWidth, framebufferHeight);

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
        graphics.blit(
                RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
                TARGET.identifier(),
                0, 0, 0F, framebufferHeight,
                graphics.guiWidth(), graphics.guiHeight(),
                framebufferWidth, -framebufferHeight,
                framebufferWidth, framebufferHeight
        );    }

    public void renderExtractedFrame() {
        if (!framePending) return;
        framePending = false;
        TARGET.clear();
        Render2DUtils.beginFrame();
        TARGET.render(canvas -> ui.render("Neutron", canvas,
                extractedFramebufferWidth, extractedFramebufferHeight,
                extractedMouseX, extractedMouseY,
                extractedLeftDown, extractedRightDown,
                extractedLeftClicked, extractedRightClicked,
                extractedScrollDelta));
        Render2DUtils.drainPendingCloses();
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
    @Override public void removed() { super.removed(); }

    private static float toFramebufferX(double x, int guiWidth, int framebufferWidth) {
        return guiWidth > 0 ? (float) (x * framebufferWidth / guiWidth) : (float) x;
    }

    private static float toFramebufferY(double y, int guiHeight, int framebufferHeight) {
        return guiHeight > 0 ? (float) (y * framebufferHeight / guiHeight) : (float) y;
    }

    public static void closeRenderer() {
        TARGET.close();
    }
}
