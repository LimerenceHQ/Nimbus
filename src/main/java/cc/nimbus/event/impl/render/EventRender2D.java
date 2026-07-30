package cc.nimbus.event.impl.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import cc.nimbus.event.Cancelable;

public class EventRender2D extends Cancelable {
    private final GuiGraphicsExtractor graphics;
    public EventRender2D(GuiGraphicsExtractor graphics) { this.graphics = graphics; }
    public GuiGraphicsExtractor getGraphics() { return graphics; }
}
