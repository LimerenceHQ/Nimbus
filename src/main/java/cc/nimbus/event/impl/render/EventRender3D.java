package cc.nimbus.event.impl.render;

import cc.nimbus.event.Cancelable;
import com.mojang.blaze3d.vertex.PoseStack;

public class EventRender3D extends Cancelable {
    private final PoseStack poseStack;
    private final float partialTick;

    public EventRender3D(PoseStack poseStack, float partialTick) {
        this.poseStack = poseStack;
        this.partialTick = partialTick;
    }

    public PoseStack getPoseStack() {
        return poseStack;
    }

    public float getPartialTick() {
        return partialTick;
    }
}
