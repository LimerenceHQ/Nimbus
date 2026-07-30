package cc.nimbus.event.impl.game;

import net.minecraft.client.gui.screens.Screen;
import cc.nimbus.event.Cancelable;

public class EventScreenChange extends Cancelable {
    private final Screen screen;
    public EventScreenChange(Screen screen) { this.screen = screen; }
    public Screen getScreen() { return screen; }
}
