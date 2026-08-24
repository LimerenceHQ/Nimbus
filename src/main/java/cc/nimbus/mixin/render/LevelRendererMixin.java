package cc.nimbus.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
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
    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void nimbus$render(PoseStack stack, float partialTick, long finishTimeNano, boolean outline, Camera camera,
                               GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projection, CallbackInfo ci) {
        Render3DUtils.captureWorldProjectionMatrix(projection);
        Render3DUtils.finishWorldProjection(stack.last().pose(), camera.getPosition(), partialTick);
        EventBus.call(new EventRender3D(stack, partialTick));
    }
}
