package cc.nimbus.event.impl.player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import cc.nimbus.event.Cancelable;
public class EventInBlock extends Cancelable { private final BlockState state; private Vec3 stuckSpeedMultiplier; public EventInBlock(BlockState state,Vec3 multiplier){this.state=state;this.stuckSpeedMultiplier=multiplier;} public BlockState getState(){return state;} public Vec3 getStuckSpeedMultiplier(){return stuckSpeedMultiplier;} public void setStuckSpeedMultiplier(Vec3 value){stuckSpeedMultiplier=value;} }
