package cc.nimbus.ui.screen;

import cc.nimbus.core.Core;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class UnsupportedPlatformScreen extends Screen {
    private final String reason;

    public UnsupportedPlatformScreen(String reason) {
        super(Component.literal(tr("nimbus.unsupported.title", "Nimbus renderer unavailable")));
        this.reason = reason == null
                ? tr("nimbus.unsupported.unknown", "Unknown compatibility error.") : reason;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal(tr("nimbus.unsupported.close", "Close")),
                        button -> onClose())
                .bounds(width / 2 - 60, height / 2 + 42, 120, 20)
                .build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), 0xF014171A);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int centerX = graphics.guiWidth() / 2;
        int centerY = graphics.guiHeight() / 2;
        graphics.centeredText(font, title, centerX, centerY - 42, 0xFFFF6B6B);
        graphics.centeredText(font, reason, centerX, centerY - 12, 0xFFE6E9ED);
        graphics.centeredText(font, tr("nimbus.unsupported.requirement",
                "Supported: Windows x64 with the OpenGL backend"), centerX, centerY + 8, 0xFF9AA4AE);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String tr(String key, String fallback) {
        return Core.INSTANCE.isInitialized() ? Core.INSTANCE.getI18nManager().get(key, fallback) : fallback;
    }
}
