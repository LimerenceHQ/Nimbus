package cc.nimbus.features.module.modules.render;

import cc.nimbus.core.Core;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.render.EventRender2D;
import cc.nimbus.features.client.ClientSettings;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.BooleanSetting;
import cc.nimbus.features.module.api.setting.impl.ModeSetting;
import cc.nimbus.features.module.api.setting.impl.NumberSetting;
import cc.nimbus.utils.render.Render2DUtils;
import cc.nimbus.utils.render.Render3DUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

@ModuleInfo(name = "ESP", description = "Highlights other players.", category = ModuleCategory.Render)
public final class ESP extends BasicModule {
    public final BooleanSetting box = new BooleanSetting("Box", true);
    public final ModeSetting boxMode = new ModeSetting(
            "Box Mode", new String[]{"Box", "Corner A", "Corner B", "Split"}, "Box", box::getValue
    );
    public final BooleanSetting boxFill = new BooleanSetting("Box Fill", false, box::getValue);
    public final NumberSetting lineWidth = new NumberSetting("Line Width", 1.4, 0.5, 3, 0.1, box::getValue);
    public final BooleanSetting healthBar = new BooleanSetting("Health Bar", true);
    public final BooleanSetting armorBar = new BooleanSetting("Armor Bar", true);
    public final BooleanSetting equipment = new BooleanSetting("Equipment", true);
    public final BooleanSetting potionEffects = new BooleanSetting("Potion Effects", true);
    public final BooleanSetting showSelf = new BooleanSetting("Show Self", false);

    @EventTarget
    private void onRender2D(EventRender2D event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.gui.screen() != null) return;

        Render3DUtils.WorldProjection projection = Render3DUtils.worldProjection();
        if (projection == null) return;
        float partialTick = projection.partialTick();
        long now = System.currentTimeMillis();
        ClientSettings settings = Core.INSTANCE.getConfigManager().clientSettings();
        List<Entry> entries = new ArrayList<>();
        int colorIndex = 0;
        for (Player player : minecraft.level.players()) {
            boolean self = player == minecraft.player;
            if (self && (!showSelf.getValue() || minecraft.options.getCameraType().isFirstPerson())) continue;
            if (player.isSpectator() || player.isRemoved()) continue;
            double x = Mth.lerp(partialTick, player.xOld, player.getX());
            double y = Mth.lerp(partialTick, player.yOld, player.getY());
            double z = Mth.lerp(partialTick, player.zOld, player.getZ());
            AABB bounds = player.getBoundingBox().move(
                    x - player.getX(), y - player.getY(), z - player.getZ()
            ).inflate(0.04, 0.08, 0.04);
            Render3DUtils.ProjectedBox projected = Render3DUtils.projectAabb(bounds, projection);
            if (projected == null || projected.width() <= 0f || projected.height() <= 0f) continue;

            float health = player.getMaxHealth() <= 0f ? 0f : player.getHealth() / player.getMaxHealth();
            float armor = player.getArmorValue() / 20f;
            entries.add(new Entry(projected, Math.clamp(armor, 0f, 1f),
                    Math.clamp(health, 0f, 1f), settings.accentColor(colorIndex++, now)));
            Render2DUtils.drawEspDetails(event.getGraphics(), player, projected,
                    equipment.getValue(), potionEffects.getValue());
        }
        if (entries.isEmpty()) return;

        boolean drawBox = box.getValue();
        String mode = boxMode.getValue();
        boolean fill = boxFill.getValue();
        float strokeWidth = lineWidth.getValue().floatValue();
        boolean drawArmor = armorBar.getValue();
        boolean drawHealth = healthBar.getValue();
        List<Entry> snapshot = List.copyOf(entries);
        Core.INSTANCE.getRenderManager().skija().hud().submit(frame -> {
            for (Entry entry : snapshot) {
                Render2DUtils.drawEspFrame(frame, entry.bounds(), drawBox, mode, fill, strokeWidth,
                        entry.color(), drawArmor, entry.armor(), drawHealth, entry.health());
            }
        });
    }

    private record Entry(Render3DUtils.ProjectedBox bounds, float armor, float health, int color) { }
}
