package cc.nimbus.utils.mixin;

import net.minecraft.client.model.EntityModel;

public interface LivingEntityRendererAccess {
    EntityModel<?> nimbus$getModel();
}
