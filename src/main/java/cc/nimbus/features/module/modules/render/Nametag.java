package cc.nimbus.features.module.modules.render;

import cc.nimbus.core.Core;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.render.EventRender2D;
import cc.nimbus.features.client.ClientSettings;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.utils.render.Render3DUtils;
import cc.nimbus.utils.render.font.NimbusFonts;
import cc.nimbus.utils.render.skija.api.SkijaRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@ModuleInfo(name = "Nametag", description = "Shows player IDs and distance above players.", category = ModuleCategory.Render)
public final class Nametag extends BasicModule {
    private static final String NAME_FONT = NimbusFonts.BODY_STRONG;
    private static final String META_FONT = NimbusFonts.BODY_MEDIUM;
    private static final float NAME_SIZE = 15f;
    private static final float META_SIZE = 11.5f;
    private static final float HEIGHT = 36f;
    private static final float PADDING_X = 9f;
    private static final float RADIUS = 4f;

    @EventTarget
    private void onRender2D(EventRender2D event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.gui.screen() != null) return;
        Render3DUtils.WorldProjection projection = Render3DUtils.worldProjection();
        if (projection == null) return;

        SkijaRenderer skija = Core.INSTANCE.getRenderManager().skija();
        float guiScale = event.getGraphics().guiWidth() > 0
                ? skija.width() / (float) event.getGraphics().guiWidth() : 1f;
        float nameSize = NAME_SIZE * guiScale;
        float metaSize = META_SIZE * guiScale;
        float height = HEIGHT * guiScale;
        float paddingX = PADDING_X * guiScale;
        float partialTick = projection.partialTick();
        long now = System.currentTimeMillis();
        ClientSettings settings = Core.INSTANCE.getConfigManager().clientSettings();
        List<Entry> entries = new ArrayList<>();
        int colorIndex = 0;

        for (Player player : minecraft.level.players()) {
            if (player == minecraft.player || player.isSpectator() || player.isRemoved()) continue;
            double x = Mth.lerp(partialTick, player.xOld, player.getX());
            double y = Mth.lerp(partialTick, player.yOld, player.getY());
            double z = Mth.lerp(partialTick, player.zOld, player.getZ());
            AABB bounds = player.getBoundingBox().move(
                    x - player.getX(), y - player.getY(), z - player.getZ()
            ).inflate(0.03, 0.08, 0.03);
            Render3DUtils.ProjectedBox projected = Render3DUtils.projectAabb(bounds, projection);
            if (projected == null) continue;

            String name = player.getName().getString();
            String metadata = String.format(Locale.ROOT, "ID %d  ·  %.1fm",
                    player.getId(), minecraft.player.distanceTo(player));
            float width = Math.max(
                    skija.text().width(NAME_FONT, nameSize, name),
                    skija.text().width(META_FONT, metaSize, metadata)
            ) + paddingX * 2f;
            entries.add(new Entry(
                    (projected.left() + projected.right()) * 0.5f,
                    projected.top(), width, height, name, metadata,
                    settings.accentColor(colorIndex++, now)
            ));
        }
        if (entries.isEmpty()) return;

        List<Entry> snapshot = List.copyOf(entries);
        skija.hud().submit(frame -> {
            for (Entry entry : snapshot) {
                float centerX = screen(entry.centerX, frame.width());
                float y = screen(entry.top, frame.height()) - entry.height - 5f * guiScale;
                float x = centerX - entry.width * 0.5f;
                frame.draw().roundedRect(x, y, entry.width, entry.height,
                        RADIUS * guiScale, 0x70171A1E);
                frame.draw().roundedRect(x, y + entry.height - 2f * guiScale,
                        entry.width, 2f * guiScale, RADIUS * guiScale,
                        0xE6000000 | (entry.accent & 0x00FFFFFF));
                frame.text().center(NAME_FONT, nameSize, entry.name,
                        centerX, y + 4f * guiScale, 0xFFF4F7F8);
                frame.text().center(META_FONT, metaSize, entry.metadata,
                        centerX, y + 20f * guiScale, 0xFFB8C0C7);
            }
        });
    }

    private static float screen(float normalized, float size) {
        return (normalized + 1f) * 0.5f * size;
    }

    private record Entry(float centerX, float top, float width, float height,
                         String name, String metadata, int accent) { }
}
