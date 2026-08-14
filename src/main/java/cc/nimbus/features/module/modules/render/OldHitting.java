package cc.nimbus.features.module.modules.render;

import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.NumberSetting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

@ModuleInfo(name = "Old Hitting", description = "Uses a compact classic first-person swing.", category = ModuleCategory.Render)
public final class OldHitting extends BasicModule {
    public final NumberSetting x = new NumberSetting("X", 0, -2, 2, 0.05);
    public final NumberSetting y = new NumberSetting("Y", 0, -2, 2, 0.05);
    public final NumberSetting z = new NumberSetting("Z", 0, -2, 2, 0.05);
    public final NumberSetting scale = new NumberSetting("Scale", 1, 0.1, 4, 0.05);

    public void transform(PoseStack matrices, float side, float equipProgress, float swingProgress,
                          HumanoidArm arm) {
        matrices.translate(side * 0.56f, -0.52f + equipProgress * -0.6f, -0.71999997f);
        matrices.translate(x.getValue().floatValue(), y.getValue().floatValue(), z.getValue().floatValue());

        float swingCurve = Mth.sin(swingProgress * swingProgress * Mth.PI);
        float swingRoot = Mth.sin(Mth.sqrt(swingProgress) * Mth.PI);
        matrices.mulPose(Axis.YP.rotationDegrees(side * (45.0f + swingCurve * -20.0f)));
        matrices.mulPose(Axis.ZP.rotationDegrees(side * swingRoot * -20.0f));
        matrices.mulPose(Axis.XP.rotationDegrees(swingRoot * -80.0f));
        matrices.mulPose(Axis.YP.rotationDegrees(side * -45.0f));

        float itemScale = scale.getValue().floatValue() * 0.9f;
        matrices.scale(itemScale, itemScale, itemScale);
        matrices.translate(-0.2f, 0.126f, 0.2f);
        matrices.mulPose(Axis.XP.rotationDegrees(-102.25f));
        matrices.mulPose((arm == HumanoidArm.RIGHT ? Axis.YP : Axis.YN).rotationDegrees(15.0f));
        matrices.mulPose((arm == HumanoidArm.RIGHT ? Axis.ZP : Axis.ZN).rotationDegrees(80.0f));
    }
}
