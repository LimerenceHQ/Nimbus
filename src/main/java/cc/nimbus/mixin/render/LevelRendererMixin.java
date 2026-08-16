package cc.nimbus.mixin.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.Camera;
import org.joml.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import cc.nimbus.event.EventBus;
import cc.nimbus.event.impl.render.EventRender3D;
import cc.nimbus.utils.render.Render3DUtils;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void nimbus$render(GraphicsResourceAllocator allocator, DeltaTracker delta, boolean outline, Camera camera,
                               Matrix4f modelView, Matrix4f projection, Matrix4f frustum, GpuBufferSlice fog,
                               Vector4f fogColor, boolean sky, CallbackInfo ci) {
        PoseStack stack = new PoseStack();
        stack.mulPose(modelView);
        float partialTick = delta.getGameTimeDeltaPartialTick(false);
        Render3DUtils.finishWorldProjection(modelView, camera.position(), partialTick);
        EventBus.call(new EventRender3D(stack, partialTick));
    }
}
