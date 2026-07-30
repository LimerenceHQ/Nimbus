package cc.nimbus.event.impl.game;
import net.minecraft.client.multiplayer.ClientLevel;
import cc.nimbus.event.Cancelable;
public class EventWorldLoad extends Cancelable { private final ClientLevel world; public EventWorldLoad(ClientLevel world){this.world=world;} public ClientLevel getWorld(){return world;} }
