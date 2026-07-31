package cc.nimbus.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import cc.nimbus.event.EventBus;
import cc.nimbus.event.impl.render.EventRender3D;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
    @Inject(method = "render", at = @At("RETURN"))
    private void nimbus$render(GraphicsResourceAllocator allocator, DeltaTracker delta, boolean outline, CameraRenderState camera, Matrix4fc modelView, GpuBufferSlice fog, Vector4f fogColor, boolean sky, CallbackInfo ci) {
        PoseStack stack = new PoseStack();
        stack.mulPose(modelView);
        EventBus.call(new EventRender3D(stack, delta.getGameTimeDeltaPartialTick(false)));
    }
}
