package cc.nimbus.module.impl.render;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.render.EventRender3D;
import cc.nimbus.module.BasicModule;
import cc.nimbus.module.ModuleCategory;
import cc.nimbus.module.ModuleInfo;
import cc.nimbus.settings.impl.BooleanSetting;
import cc.nimbus.settings.impl.ColorSetting;
import cc.nimbus.settings.impl.NumberSetting;
import cc.nimbus.utils.render.Render3DUtils;

import java.util.ArrayList;
import java.util.List;

@ModuleInfo(name = "ESP", description = "Highlights other players.", category = ModuleCategory.Render)
public final class ESP extends BasicModule {
    public final BooleanSetting filled = new BooleanSetting("Filled", true);
    public final BooleanSetting throughWalls = new BooleanSetting("Through walls", true);
    public final BooleanSetting renderSelf = new BooleanSetting("Render self", false);
    public final NumberSetting lineWidth = new NumberSetting("Line width", 1.5, 0.5, 5, 0.5);
    public final ColorSetting outlineColor = new ColorSetting("Outline color", 0xFF52C7B8);
    public final ColorSetting fillColor = new ColorSetting("Fill color", 0x3052C7B8, filled::getValue);

    @EventTarget
    private void onRender3D(EventRender3D event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.gui.screen() != null) return;
        Render3DUtils.DepthMode depth = throughWalls.getValue()
                ? Render3DUtils.DepthMode.NONE : Render3DUtils.DepthMode.NORMAL;
        List<AABB> boxes = new ArrayList<>();
        float partialTick = event.getPartialTick();
        for (Player player : minecraft.level.players()) {
            boolean firstPersonSelf = player == minecraft.player && minecraft.options.getCameraType().isFirstPerson();
            if ((player == minecraft.player && !renderSelf.getValue()) || firstPersonSelf
                    || player.isSpectator() || player.isRemoved()) continue;
            double x = Mth.lerp(partialTick, player.xOld, player.getX());
            double y = Mth.lerp(partialTick, player.yOld, player.getY());
            double z = Mth.lerp(partialTick, player.zOld, player.getZ());
            boxes.add(player.getBoundingBox().move(x - player.getX(), y - player.getY(), z - player.getZ()));
        }
        if (boxes.isEmpty()) return;
        if (filled.getValue()) {
            Render3DUtils.drawFilledBoxesWithDepth(event.getPoseStack(), boxes, fillColor.getValue(), depth);
        }
        Render3DUtils.drawBoxOutlinesWithDepth(event.getPoseStack(), boxes,
                outlineColor.getValue(), lineWidth.getValue().floatValue(), depth);
    }
}
