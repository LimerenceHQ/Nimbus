package cc.nimbus.event.impl.player;
import net.minecraft.world.phys.Vec3;
import cc.nimbus.event.Cancelable;
public class EventAttackSlowdown extends Cancelable { private Vec3 reduce; private boolean sprint; public EventAttackSlowdown(Vec3 reduce){this.reduce=reduce;} public EventAttackSlowdown(boolean sprint){this.sprint=sprint;} public Vec3 getReduce(){return reduce;} public void setReduce(Vec3 value){reduce=value;} public boolean isSprint(){return sprint;} public void setSprint(boolean value){sprint=value;} }
