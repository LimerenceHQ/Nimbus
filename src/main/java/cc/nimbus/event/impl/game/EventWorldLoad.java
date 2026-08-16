package cc.nimbus.event.impl.game;

import cc.nimbus.event.Cancelable;
import net.minecraft.client.multiplayer.ClientLevel;

public class EventWorldLoad extends Cancelable {
    private final ClientLevel world;

    public EventWorldLoad(ClientLevel world) {
        this.world = world;
    }

    public ClientLevel getWorld() {
        return world;
    }
}
