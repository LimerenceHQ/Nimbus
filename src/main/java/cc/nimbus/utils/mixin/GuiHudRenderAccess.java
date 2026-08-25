package cc.nimbus.utils.mixin;

import net.minecraft.client.gui.GuiGraphics;

public interface GuiHudRenderAccess {
    void nimbus$resetHudExtraction();

    void nimbus$extractHud(GuiGraphics graphics);
}
