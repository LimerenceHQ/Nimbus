package cc.nimbus.event.impl.player;

import cc.nimbus.event.Cancelable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class EventSlowMovement extends Cancelable {
    private final BlockState state;
    private final Vec3 stuckSpeedMultiplier;

    public EventSlowMovement(BlockState state, Vec3 stuckSpeedMultiplier) {
        this.state = state;
        this.stuckSpeedMultiplier = stuckSpeedMultiplier;
    }

    public Vec3 getStuckSpeedMultiplier() {
        return stuckSpeedMultiplier;
    }

    public BlockState getState() {
        return state;
    }
}
