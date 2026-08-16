package cc.nimbus.event.impl.game;

import cc.nimbus.event.Cancelable;
import net.minecraft.client.gui.screens.Screen;

public class EventScreenChange extends Cancelable {
    private final Screen screen;

    public EventScreenChange(Screen screen) {
        this.screen = screen;
    }

    public Screen getScreen() {
        return screen;
    }
}
