package cc.nimbus.features.module.modules.render;

import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.NumberSetting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Set;

@ModuleInfo(name = "Old Hitting", description = "Uses a compact classic first-person swing.", category = ModuleCategory.Render)
public final class OldHitting extends BasicModule {
    private static final Set<Item> IGNORED_USE_ITEMS = Set.of(
            Items.GOLDEN_APPLE,
            Items.ENCHANTED_GOLDEN_APPLE,
            Items.SNOWBALL,
            Items.EGG,
            Items.ENDER_PEARL,
            Items.EXPERIENCE_BOTTLE,
            Items.SPLASH_POTION,
            Items.LINGERING_POTION,
            Items.WIND_CHARGE,
            Items.FIREWORK_ROCKET,
            Items.TRIDENT
    );

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

    public boolean shouldIgnoreUse(Player player) {
        if (player == null) return false;
        if (player.isUsingItem() && player.getUsedItemHand() == InteractionHand.OFF_HAND) {
            return isIgnoredUseItem(player.getUseItem());
        }
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.options != null && minecraft.options.keyUse.isDown()
                && isIgnoredUseItem(player.getOffhandItem());
    }

    private static boolean isIgnoredUseItem(ItemStack stack) {
        return IGNORED_USE_ITEMS.contains(stack.getItem());
    }
}
