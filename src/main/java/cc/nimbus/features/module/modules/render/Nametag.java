package cc.nimbus.features.module.modules.render;

import cc.nimbus.core.Core;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.render.EventRender2D;
import cc.nimbus.features.client.ClientSettings;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.utils.render.Render3DUtils;
import cc.nimbus.utils.render.PlayerProjectionCache;
import cc.nimbus.utils.render.font.NimbusFonts;
import cc.nimbus.utils.render.skija.api.SkijaRenderer;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.PathBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
    private final Map<Integer, LabelCache> labels = new HashMap<>();
    private ClientLevel labelLevel;

    @EventTarget
    private void onRender2D(EventRender2D event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.gui.screen() != null) return;
        Render3DUtils.WorldProjection projection = Render3DUtils.worldProjection();
        if (projection == null) return;

        SkijaRenderer skija = Core.INSTANCE.getRenderManager().skija();
        float guiScale = event.getGraphics().guiWidth() > 0
                ? skija.width() / (float) event.getGraphics().guiWidth() : 1f;
        long now = System.currentTimeMillis();
        ClientSettings settings = Core.INSTANCE.getConfigManager().clientSettings();
        List<Entry> entries = new ArrayList<>();
        if (labelLevel != minecraft.level) {
            labels.clear();
            labelLevel = minecraft.level;
        }
        long gameTime = minecraft.level.getGameTime();
        int colorIndex = 0;

        for (PlayerProjectionCache.Entry projectedPlayer : PlayerProjectionCache.entries(minecraft, projection)) {
            if (!(minecraft.level.getEntity(projectedPlayer.entityId()) instanceof Player player)) continue;
            if (player == minecraft.player) continue;
            Render3DUtils.ProjectedBox projected = projectedPlayer.bounds();

            float espWidth = projected.width() * 0.5f * skija.width();
            if (espWidth <= 2f) continue;
            LabelCache label = label(player, minecraft.player, skija, guiScale, gameTime);
            float naturalWidth = label.naturalWidth;
            float relativeScale = Math.max(MIN_SCALE, Math.min(1f, espWidth / naturalWidth));
            float scale = guiScale * relativeScale;
            float width = Math.max(espWidth, naturalWidth * relativeScale);
            float height = HEIGHT * scale;
            entries.add(new Entry(
                    (projected.left() + projected.right()) * 0.5f,
                    projected.top(), width, height, scale, label.name, label.metadata,
                    settings.accentColor(colorIndex++, now)
            ));
        }
        if (entries.isEmpty()) return;

        List<Entry> snapshot = List.copyOf(entries);
        skija.hud().submit(frame -> {
            try (Paint backgroundPaint = new Paint().setAntiAlias(true).setColor(BACKGROUND_COLOR)) {
                for (Entry entry : snapshot) {
                    float centerX = screen(entry.centerX, frame.width());
                    float y = screen(entry.top, frame.height()) - entry.height - GAP * guiScale;
                    float x = centerX - entry.width * 0.5f;
                    float radius = RADIUS * entry.scale;
                    try (PathBuilder builder = new PathBuilder()) {
                        builder.moveTo(x + radius, y);
                        builder.lineTo(x + entry.width - radius, y);
                        builder.quadTo(x + entry.width, y, x + entry.width, y + radius);
                        builder.lineTo(x + entry.width, y + entry.height);
                        builder.lineTo(x, y + entry.height);
                        builder.lineTo(x, y + radius);
                        builder.quadTo(x, y, x + radius, y);
                        builder.closePath();
                        try (Path background = builder.detach()) {
                            frame.canvas().drawPath(background, backgroundPaint);
                        }
                    }
                    frame.draw().rect(x, y + entry.height - 2f * entry.scale,
                            entry.width, 2f * entry.scale,
                            0xE6000000 | (entry.accent & 0x00FFFFFF));
                    frame.text().center(NAME_FONT, NAME_SIZE * entry.scale, entry.name,
                            centerX, y + 4f * entry.scale, 0xFFF4F7F8);
                    frame.text().center(META_FONT, META_SIZE * entry.scale, entry.metadata,
                            centerX, y + 20f * entry.scale, 0xFFB8C0C7);
                }
            }
        });
    }

    @Override
    protected void onDisable() {
        labels.clear();
        labelLevel = null;
    }

    private LabelCache label(Player player, Player viewer, SkijaRenderer skija,
                             float guiScale, long gameTime) {
        LabelCache cached = labels.computeIfAbsent(player.getId(), ignored -> new LabelCache());
        if (cached.tick == gameTime && Float.compare(cached.guiScale, guiScale) == 0) return cached;

        String name = player.getName().getString();
        String metadata = String.format(Locale.ROOT, "ID %d  ·  %.1fm",
                player.getId(), viewer.distanceTo(player));
        if (!name.equals(cached.name) || !metadata.equals(cached.metadata)
                || Float.compare(cached.guiScale, guiScale) != 0) {
            cached.name = name;
            cached.metadata = metadata;
            cached.guiScale = guiScale;
            cached.naturalWidth = Math.max(
                    skija.text().width(NAME_FONT, NAME_SIZE * guiScale, name),
                    skija.text().width(META_FONT, META_SIZE * guiScale, metadata)
            ) + PADDING_X * guiScale * 2f;
        }
        cached.tick = gameTime;
        return cached;
    }

    private static float screen(float normalized, float size) {
        return (normalized + 1f) * 0.5f * size;
    }

    private record Entry(float centerX, float top, float width, float height, float scale,
                         String name, String metadata, int accent) { }

    private static final class LabelCache {
        private long tick = Long.MIN_VALUE;
        private float guiScale = Float.NaN;
        private float naturalWidth;
        private String name = "";
        private String metadata = "";
    }
}
