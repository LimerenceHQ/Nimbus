package cc.nimbus.event.impl.render;

import net.minecraft.client.gui.GuiGraphics;
import cc.nimbus.event.Cancelable;

public class EventRender2D extends Cancelable {
    private final GuiGraphics graphics;
    public EventRender2D(GuiGraphics graphics) { this.graphics = graphics; }
    public GuiGraphics getGraphics() { return graphics; }
}
