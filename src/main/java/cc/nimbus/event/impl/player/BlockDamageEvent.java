package cc.nimbus.event.impl.player;
import cc.nimbus.event.Cancelable;
public class BlockDamageEvent extends Cancelable {
    private final Object pos, direction;
    public BlockDamageEvent(Object pos, Object direction) { this.pos=pos; this.direction=direction; }
    public Object getPos() { return pos; }
    public Object getDirection() { return direction; }
}
