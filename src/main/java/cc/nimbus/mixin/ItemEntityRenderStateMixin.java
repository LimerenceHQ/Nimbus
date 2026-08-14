package cc.nimbus.mixin;

import cc.nimbus.features.module.modules.render.item.PhysicalItemRenderState;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ItemEntityRenderState.class)
public abstract class ItemEntityRenderStateMixin implements PhysicalItemRenderState {
    @Unique
    private boolean nimbus$grounded;

    @Override
    public boolean nimbus$isGrounded() {
        return nimbus$grounded;
    }

    @Override
    public void nimbus$setGrounded(boolean grounded) {
        nimbus$grounded = grounded;
    }
}
