package cc.nimbus.features.module.modules.hud;

import cc.nimbus.core.Core;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.render.EventRender2D;
import cc.nimbus.features.hud.Draggable;
import cc.nimbus.features.hud.HudRect;
import cc.nimbus.features.hud.HudSize;
import cc.nimbus.features.hud.HudViewport;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.utils.render.skija.api.SkijaRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

@ModuleInfo(
        name = "ArmorHUD",
        displayName = "Armor HUD",
        description = "Shows the armor worn by the local player.",
        category = ModuleCategory.HUD
)
public final class ArmorHUD extends BasicModule {
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    private static final float SLOT_SIZE = 18f;
    private static final float PADDING = 1f;

    private volatile HudSize layoutSize = new HudSize(2f, 2f);

    private final Draggable drag = new Draggable("ArmorHUD", 0.5, 0.86) {
        @Override
        public HudSize getSize() {
            return layoutSize;
        }
    };

    @EventTarget
    private void onRender2D(EventRender2D event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            layoutSize = new HudSize(2f, 2f);
            return;
        }

        List<ItemStack> armor = wornArmor(minecraft);
        layoutSize = armor.isEmpty()
                ? new HudSize(2f, 2f)
                : new HudSize(armor.size() * SLOT_SIZE, SLOT_SIZE);
        if (armor.isEmpty()) return;

        GuiGraphics graphics = event.getGraphics();
        SkijaRenderer skija = Core.INSTANCE.getRenderManager().skija();
        HudViewport viewport = Core.INSTANCE.getHudManager().viewport(skija.width(), skija.height());
        HudRect bounds = drag.getRect(viewport);
        float framebufferScale = skija.width() / (float) Math.max(1, graphics.guiWidth());
        float renderScale = drag.getRenderScale(viewport);
        float itemScale = renderScale / Math.max(.0001f, framebufferScale);
        float originX = bounds.x() / framebufferScale;
        float originY = bounds.y() / framebufferScale;

        var pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(originX, originY, 0f);
            pose.scale(itemScale, itemScale, 1f);
            for (int index = 0; index < armor.size(); index++) {
                graphics.renderItem(armor.get(index), index * (int) SLOT_SIZE + (int) PADDING,
                        (int) PADDING, minecraft.player.getId() + index);
            }
        } finally {
            pose.popPose();
        }
    }

    private static List<ItemStack> wornArmor(Minecraft minecraft) {
        List<ItemStack> armor = new ArrayList<>(ARMOR_SLOTS.length);
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = minecraft.player.getItemBySlot(slot);
            if (!stack.isEmpty()) armor.add(stack);
        }
        return armor;
    }
}
