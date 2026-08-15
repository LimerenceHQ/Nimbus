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
    private static final float MIN_SCALE = 0.3f;
    private static final float GAP = 3f;
    private static final int BACKGROUND_COLOR = 0x70171A1E;

    @EventTarget
    private void onRender2D(EventRender2D event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.gui.screen() != null) return;
        Render3DUtils.WorldProjection projection = Render3DUtils.worldProjection();
        if (projection == null) return;

        SkijaRenderer skija = Core.INSTANCE.getRenderManager().skija();
        float guiScale = event.getGraphics().guiWidth() > 0
                ? skija.width() / (float) event.getGraphics().guiWidth() : 1f;
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

            float espWidth = projected.width() * 0.5f * skija.width();
            if (espWidth <= 2f) continue;
            float distance = minecraft.player.distanceTo(player);
            String name = player.getName().getString();
            String metadata = String.format(Locale.ROOT, "ID %d  ·  %.1fm",
                    player.getId(), distance);
            float naturalWidth = Math.max(
                    skija.text().width(NAME_FONT, NAME_SIZE * guiScale, name),
                    skija.text().width(META_FONT, META_SIZE * guiScale, metadata)
            ) + PADDING_X * guiScale * 2f;
            float relativeScale = Math.max(MIN_SCALE, Math.min(1f, espWidth / naturalWidth));
            float scale = guiScale * relativeScale;
            float width = Math.max(espWidth, naturalWidth * relativeScale);
            float height = HEIGHT * scale;
            entries.add(new Entry(
                    (projected.left() + projected.right()) * 0.5f,
                    projected.top(), width, height, scale, name, metadata,
                    settings.accentColor(colorIndex++, now)
            ));
        }
        if (entries.isEmpty()) return;

        List<Entry> snapshot = List.copyOf(entries);
        skija.hud().submit(frame -> {
            for (Entry entry : snapshot) {
                float centerX = screen(entry.centerX, frame.width());
                float y = screen(entry.top, frame.height()) - entry.height - GAP * guiScale;
                float x = centerX - entry.width * 0.5f;
                float radius = RADIUS * entry.scale;
                frame.draw().roundedRect(x, y, entry.width, entry.height,
                        radius, BACKGROUND_COLOR);
                frame.draw().rect(x, y + radius, entry.width,
                        entry.height - radius, BACKGROUND_COLOR);
                frame.draw().rect(x, y + entry.height - 2f * entry.scale,
                        entry.width, 2f * entry.scale,
                        0xE6000000 | (entry.accent & 0x00FFFFFF));
                frame.text().center(NAME_FONT, NAME_SIZE * entry.scale, entry.name,
                        centerX, y + 4f * entry.scale, 0xFFF4F7F8);
                frame.text().center(META_FONT, META_SIZE * entry.scale, entry.metadata,
                        centerX, y + 20f * entry.scale, 0xFFB8C0C7);
            }
        });
    }

    private static float screen(float normalized, float size) {
        return (normalized + 1f) * 0.5f * size;
    }

    private record Entry(float centerX, float top, float width, float height, float scale,
                         String name, String metadata, int accent) { }
}
