package cc.nimbus.ui.menu;

import cc.nimbus.core.Core;
import cc.nimbus.ui.alt.AccountScreen;
import cc.nimbus.ui.screen.AbstractSkijaScreen;
import cc.nimbus.utils.render.skija.api.PostProcessOptions;
import cc.nimbus.utils.render.skija.api.SkijaFrame;
import cc.nimbus.utils.render.font.NimbusFonts;
import io.github.humbleui.skija.ClipMode;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.FilterTileMode;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.types.Rect;
import io.github.humbleui.types.RRect;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class TitleScreen extends AbstractSkijaScreen {
    private static final int BUTTON_WIDTH = 300;
    private static final int BUTTON_HEIGHT = 40;
    private static final int BUTTON_GAP = 10;
    public TitleScreen() {
        super(Component.literal("Nimbus"), "main-menu");
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderPanorama(graphics, partialTick);
        renderBlurredBackground();
    }

    @Override
    protected void renderSkija(SkijaFrame frame) {
        int width = framebufferWidth();
        int height = framebufferHeight();
        Canvas canvas = frame.canvas();
        try (Paint paint = new Paint().setAntiAlias(true)) {
            float scale = Math.min(1F, Math.min(width / 1100F, height / 700F));
            float center = width * .5F;
            float top = height * .5F - 145F * scale;
            frame.text().center(NimbusFonts.DISPLAY, 46, "Nimbus", center, top + 26F * scale, 0xFFFFFFFF);
            String[] labels = {
                    tr("nimbus.menu.singleplayer", "Singleplayer"),
                    tr("nimbus.menu.multiplayer", "Multiplayer"),
                    tr("nimbus.menu.alt_manager", "Alt Manager"),
                    tr("nimbus.menu.settings", "Settings")
            };
            for (int i = 0; i < labels.length; i++) {
                float x = center - BUTTON_WIDTH * scale / 2F;
                float y = top + 78F * scale + i * (BUTTON_HEIGHT + BUTTON_GAP) * scale;
                boolean hover = framebufferMouseX() >= x && framebufferMouseX() <= x + BUTTON_WIDTH * scale
                        && framebufferMouseY() >= y && framebufferMouseY() <= y + BUTTON_HEIGHT * scale;
                float buttonWidth = BUTTON_WIDTH * scale;
                float buttonHeight = BUTTON_HEIGHT * scale;
                Image background = frame.images().scene();
                if (background != null && !background.isClosed()) {
                    int save = canvas.save();
                    try {
                        canvas.clipRRect(RRect.makeXYWH(x, y, buttonWidth, buttonHeight, 8F * scale), ClipMode.INTERSECT, true);
                        paint.setImageFilter(frame.effects().imageBlur(10F, 10F, FilterTileMode.CLAMP));
                        paint.setColor(0xA0FFFFFF);
                        canvas.drawImageRect(background, Rect.makeXYWH(x, y, buttonWidth, buttonHeight),
                                Rect.makeXYWH(x, y, buttonWidth, buttonHeight), paint);
                        paint.setImageFilter(null);
                    } finally {
                        canvas.restoreToCount(save);
                    }
                }
                paint.setColor(hover ? 0x50E8EAE8 : 0x28E8EAE8);
                canvas.drawRRect(RRect.makeXYWH(x, y, buttonWidth, buttonHeight, 8F * scale), paint);
                frame.text().center(NimbusFonts.BODY, 19,
                        labels[i], center, y + 11F * scale, 0xD817201D);
            }
            frame.text().draw(NimbusFonts.BODY, 19, "Nimbus 1.0", 18, height - 28, 0x99FFFFFF);
            String version = "Minecraft " + net.minecraft.SharedConstants.getCurrentVersion().getName();
            frame.text().draw(NimbusFonts.BODY, 19, version,
                    width - frame.text().width(NimbusFonts.BODY, 19, version) - 18, height - 28,
                    0x99FFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(double x, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(x, mouseY, button);
        int renderWidth = minecraft.getWindow().getWidth();
        int renderHeight = minecraft.getWindow().getHeight();
        float scale = Math.min(1F, Math.min(renderWidth / 1100F, renderHeight / 700F));
        float top = renderHeight * .5F - 145F * scale;
        float left = renderWidth * .5F - BUTTON_WIDTH * scale / 2F;
        float clickX = framebufferX(x);
        float clickY = framebufferY(mouseY);
        for (int i = 0; i < 4; i++) {
            float y = top + 78F * scale + i * (BUTTON_HEIGHT + BUTTON_GAP) * scale;
            if (clickX >= left && clickX <= left + BUTTON_WIDTH * scale && clickY >= y && clickY <= y + BUTTON_HEIGHT * scale) {
                activate(i); return true;
            }
        }
        if (clickX >= renderWidth - 56 && clickY <= 56) { onClose(); return true; }
        return super.mouseClicked(x, mouseY, button);
    }

    private void activate(int index) {
        switch (index) {
            case 0 -> minecraft.setScreen(new SelectWorldScreen(this));
            case 1 -> minecraft.setScreen(new JoinMultiplayerScreen(this));
            case 2 -> minecraft.setScreen(new AccountScreen(this));
            case 3 -> minecraft.setScreen(new OptionsScreen(this, minecraft.options));
        }
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) { if (keyCode == GLFW.GLFW_KEY_ESCAPE) return true; return super.keyPressed(keyCode, scanCode, modifiers); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.setScreen(null); }

    @Override
    public int requestedSceneCaptureRate(PostProcessOptions options) {
        int refreshRate = minecraft.getWindow().getRefreshRate();
        return refreshRate > 0 ? refreshRate : 60;
    }

    private static String tr(String key, String fallback) {
        return Core.INSTANCE.getI18nManager().get(key, fallback);
    }
}
