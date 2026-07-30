package cc.nimbus.event.impl.render;
import com.mojang.blaze3d.vertex.PoseStack;
public class Render3DEvent extends EventRender3D { public Render3DEvent(PoseStack poseStack,float partialTick){super(poseStack,partialTick);} public Render3DEvent(PoseStack poseStack){this(poseStack,0f);} }
