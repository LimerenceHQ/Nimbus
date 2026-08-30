package cc.nimbus.mixin.render;

import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import cc.nimbus.event.EventBus;
import cc.nimbus.event.impl.render.EventRender3D;
import cc.nimbus.utils.render.Render3DUtils;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
    private Matrix4f nimbus$worldView;

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void nimbus$captureWorldView(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker, boolean outline,
                                         Camera camera, GameRenderer gameRenderer, Matrix4f viewMatrix,
                                         Matrix4f projection, CallbackInfo ci) {
        nimbus$worldView = new Matrix4f(viewMatrix);
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void nimbus$render(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker, boolean outline,
                               Camera camera, GameRenderer gameRenderer, Matrix4f viewMatrix,
                               Matrix4f projection, CallbackInfo ci) {
        Matrix4f worldView = nimbus$worldView;
        if (worldView == null) return;
        Render3DUtils.captureWorldProjectionMatrix(projection);
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        Render3DUtils.finishWorldProjection(worldView, camera.getPosition(), partialTick);
        PoseStack eventStack = new PoseStack();
        eventStack.mulPose(worldView);
        EventBus.call(new EventRender3D(eventStack, partialTick));
    }
}
