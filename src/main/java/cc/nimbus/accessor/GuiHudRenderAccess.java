package cc.nimbus.accessor;

import net.minecraft.client.gui.GuiGraphics;

public interface GuiHudRenderAccess {
    void nimbus$resetHudExtraction();

    void nimbus$extractHud(GuiGraphics graphics);
}
