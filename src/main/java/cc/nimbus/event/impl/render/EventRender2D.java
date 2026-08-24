package cc.nimbus.event.impl.render;

import cc.nimbus.event.Cancelable;
import cc.nimbus.event.EventType;
import net.minecraft.client.gui.GuiGraphics;

public class EventRender2D extends Cancelable {
    private final GuiGraphics graphics;
    private final EventType type;

    public EventRender2D(GuiGraphics graphics) {
        this(EventType.PRE, graphics);
    }

    public EventRender2D(EventType type, GuiGraphics graphics) {
        this.type = type;
        this.graphics = graphics;
    }

    public GuiGraphics getGraphics() {
        return graphics;
    }

    public EventType getType() {
        return type;
    }

    public boolean isPre() {
        return type == EventType.PRE;
    }

    public boolean isPost() {
        return type == EventType.POST;
    }
}
