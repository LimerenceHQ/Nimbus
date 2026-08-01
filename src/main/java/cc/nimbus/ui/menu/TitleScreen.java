package cc.nimbus.ui.menu;

import cc.nimbus.ui.alt.AccountScreen;
import cc.nimbus.core.Core;
import cc.nimbus.utils.render.skija.api.SkijaFrame;
import cc.nimbus.utils.render.skija.api.SkijaLayer;
import io.github.humbleui.skija.ClipMode;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.FilterTileMode;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.ImageFilter;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.types.Rect;
import io.github.humbleui.types.RRect;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class TitleScreen extends Screen {
    private static final int BUTTON_WIDTH = 300;
    private static final int BUTTON_HEIGHT = 40;
    private static final int BUTTON_GAP = 10;
    private final SkijaLayer layer;
    private int framebufferWidth;
    private int framebufferHeight;
    private float extractedMouseX;
    private float extractedMouseY;
    private boolean framePending;


    public TitleScreen() {
        super(Component.literal("Nimbus"));
        layer = Core.INSTANCE.getRenderManager().skija().layer("main-menu");
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        extractPanorama(graphics, partialTick);
        extractBlurredBackground(graphics);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        framebufferWidth = minecraft.getWindow().getWidth();
        framebufferHeight = minecraft.getWindow().getHeight();
        layer.extract(graphics, framebufferWidth, framebufferHeight);
        extractedMouseX = toFramebufferX(mouseX, graphics.guiWidth(), framebufferWidth);
        extractedMouseY = toFramebufferY(mouseY, graphics.guiHeight(), framebufferHeight);
        framePending = true;
    }

    public void renderExtractedFrame() {
        if (!framePending) return;
        framePending = false;
        layer.render(frame -> render(frame, framebufferWidth, framebufferHeight));
    }

    private void render(SkijaFrame frame, int width, int height) {
        Canvas canvas = frame.canvas();
        try (Paint paint = new Paint().setAntiAlias(true)) {
            float scale = Math.min(1F, Math.min(width / 1100F, height / 700F));
            float center = width * .5F;
            float top = height * .5F - 145F * scale;
            frame.text().center("HarmonyOS_Sans_SC-Medium", 46, "Nimbus", center, top + 26F * scale, 0xFFFFFFFF);
            String[] labels = {"Singleplayer", "Multiplayer", "Alt Manager", "Settings"};
            for (int i = 0; i < labels.length; i++) {
                float x = center - BUTTON_WIDTH * scale / 2F;
                float y = top + 78F * scale + i * (BUTTON_HEIGHT + BUTTON_GAP) * scale;
                boolean hover = extractedMouseX >= x && extractedMouseX <= x + BUTTON_WIDTH * scale
                        && extractedMouseY >= y && extractedMouseY <= y + BUTTON_HEIGHT * scale;
                float buttonWidth = BUTTON_WIDTH * scale;
                float buttonHeight = BUTTON_HEIGHT * scale;
                Image background = frame.images().scene();
                if (background != null && !background.isClosed()) {
                    int save = canvas.save();
                    try {
                        canvas.clipRRect(RRect.makeXYWH(x, y, buttonWidth, buttonHeight, 8F * scale), ClipMode.INTERSECT, true);
                        try (ImageFilter blur = ImageFilter.makeBlur(10F, 10F, FilterTileMode.CLAMP)) {
                            paint.setImageFilter(blur);
                            paint.setColor(0xA0FFFFFF);
                            canvas.drawImageRect(background, Rect.makeXYWH(x, y, buttonWidth, buttonHeight),
                                    Rect.makeXYWH(x, y, buttonWidth, buttonHeight), paint);
                        } finally {
                            paint.setImageFilter(null);
                        }
                    } finally {
                        canvas.restoreToCount(save);
                    }
                }
                paint.setColor(hover ? 0x50E8EAE8 : 0x28E8EAE8);
                canvas.drawRRect(RRect.makeXYWH(x, y, buttonWidth, buttonHeight, 8F * scale), paint);
                frame.text().center("HarmonyOS_Sans_SC-Regular", 19,
                        labels[i], center, y + 11F * scale, 0xD817201D);
            }
            frame.text().draw("HarmonyOS_Sans_SC-Regular", 19, "Nimbus 1.0", 18, height - 28, 0x99FFFFFF);
            String version = "Minecraft " + net.minecraft.SharedConstants.getCurrentVersion().name();
            frame.text().draw("HarmonyOS_Sans_SC-Regular", 19, version,
                    width - frame.text().width("HarmonyOS_Sans_SC-Regular", 19, version) - 18, height - 28,
                    0x99FFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(event, doubleClick);
        int renderWidth = minecraft.getWindow().getWidth();
        int renderHeight = minecraft.getWindow().getHeight();
        float scale = Math.min(1F, Math.min(renderWidth / 1100F, renderHeight / 700F));
        float top = renderHeight * .5F - 145F * scale;
        float left = renderWidth * .5F - BUTTON_WIDTH * scale / 2F;
        float clickX = toFramebufferX(event.x(), width, renderWidth);
        float clickY = toFramebufferY(event.y(), height, renderHeight);
        for (int i = 0; i < 4; i++) {
            float y = top + 78F * scale + i * (BUTTON_HEIGHT + BUTTON_GAP) * scale;
            if (clickX >= left && clickX <= left + BUTTON_WIDTH * scale && clickY >= y && clickY <= y + BUTTON_HEIGHT * scale) {
                activate(i); return true;
            }
        }
        if (clickX >= renderWidth - 56 && clickY <= 56) { onClose(); return true; }
        return super.mouseClicked(event, doubleClick);
    }

    private void activate(int index) {
        switch (index) {
            case 0 -> minecraft.setScreenAndShow(new SelectWorldScreen(this));
            case 1 -> minecraft.setScreenAndShow(new JoinMultiplayerScreen(this));
            case 2 -> minecraft.setScreenAndShow(new AccountScreen(this));
            case 3 -> minecraft.setScreenAndShow(new OptionsScreen(this, minecraft.options, true));
        }
    }

    @Override public boolean keyPressed(KeyEvent event) { if (event.key() == GLFW.GLFW_KEY_ESCAPE) return true; return super.keyPressed(event); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void removed() { layer.releaseTarget(); super.removed(); }
    @Override public void onClose() { minecraft.setScreenAndShow(null); }
    private static float toFramebufferX(double x, int guiWidth, int framebufferWidth) { return guiWidth > 0 ? (float) (x * framebufferWidth / guiWidth) : (float) x; }
    private static float toFramebufferY(double y, int guiHeight, int framebufferHeight) { return guiHeight > 0 ? (float) (y * framebufferHeight / guiHeight) : (float) y; }
}
