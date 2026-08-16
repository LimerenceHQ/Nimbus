package cc.nimbus.event.impl.render;

import cc.nimbus.event.Cancelable;
import net.minecraft.client.gui.GuiGraphics;

public class EventRender2D extends Cancelable {
    private final GuiGraphics graphics;

    public EventRender2D(GuiGraphics graphics) {
        this.graphics = graphics;
    }

    public GuiGraphics getGraphics() {
        return graphics;
    }
}
