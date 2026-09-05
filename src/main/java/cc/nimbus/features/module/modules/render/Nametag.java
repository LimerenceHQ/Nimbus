package cc.nimbus.features.module.modules.render;

import cc.nimbus.core.Core;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.render.EventRender2D;
import cc.nimbus.features.client.ClientSettings;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.modules.combat.BowAssistant;
import cc.nimbus.features.module.modules.player.AntiBot;
import cc.nimbus.features.module.modules.player.Friend;
import cc.nimbus.utils.render.Render3DUtils;
import cc.nimbus.utils.render.PlayerProjectionCache;
import cc.nimbus.utils.render.font.FontSpec;
import cc.nimbus.utils.render.font.NimbusFonts;
import cc.nimbus.utils.render.skija.api.SkijaFrame;
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
    private static final FontSpec NAME_FONT = NimbusFonts.BODY_STRONG;
    private static final FontSpec META_FONT = NimbusFonts.BODY_MEDIUM;
    private static final float NAME_SIZE = 15f;
    private static final float META_SIZE = 11.5f;
    private static final float HEIGHT = 42f;
    private static final float PADDING_X = 9f;
    private static final float RADIUS = 4f;
    private static final float MIN_SCALE = 0.3f;
    private static final float GAP = 3f;
    private static final float META_Y = 24f;
    private static final float INDICATOR_GAP = 4f;
    private static final String INDICATOR_DOT = "·";
    private static final int BACKGROUND_COLOR = 0x70171A1E;
    private static final long TEXT_TRANSITION_NANOS = 180_000_000L;
    private final Map<Integer, LabelCache> labels = new HashMap<>();
    private ClientLevel labelLevel;
    private long labelsCleanupTick = Long.MIN_VALUE;

    @EventTarget
    private void onRender2D(EventRender2D event) {
        if (event.isPost()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.screen != null) return;
        Render3DUtils.WorldProjection projection = Render3DUtils.worldProjection();
        if (projection == null) return;

        SkijaRenderer skija = Core.INSTANCE.getRenderManager().skija();
        float guiScale = event.getGraphics().guiWidth() > 0
                ? skija.width() / (float) event.getGraphics().guiWidth() : 1f;
        long now = System.currentTimeMillis();
        long nowNanos = System.nanoTime();
        ClientSettings settings = Core.INSTANCE.getConfigManager().clientSettings();
        List<Entry> entries = new ArrayList<>();
        if (labelLevel != minecraft.level) {
            labels.clear();
            labelLevel = minecraft.level;
        }
        long gameTime = minecraft.level.getGameTime();
        if (labelsCleanupTick != gameTime) {
            labels.entrySet().removeIf(entry -> {
                var entity = minecraft.level.getEntity(entry.getKey());
                return !(entity instanceof Player player) || player.isRemoved();
            });
            labelsCleanupTick = gameTime;
        }
        TargetIndicators targetIndicators = targetIndicators();
        ItemWarn itemWarn = Core.INSTANCE.getModuleManager().getModule(ItemWarn.class);
        AntiBot antiBot = Core.INSTANCE.getModuleManager().getModule(AntiBot.class);
        Friend friend = Core.INSTANCE.getModuleManager().getModule(Friend.class);
        String friendLabel = friend != null && friend.isEnabled()
                ? Core.INSTANCE.getI18nManager().get(
                friend.getDisplayNameTranslationKey(), friend.getDisplayName()) : "";
        int colorIndex = 0;

        for (PlayerProjectionCache.Entry projectedPlayer : PlayerProjectionCache.entries(minecraft, projection)) {
            if (!(minecraft.level.getEntity(projectedPlayer.entityId()) instanceof Player player)) continue;
            if (player == minecraft.player) continue;
            if (antiBot != null && antiBot.isBot(player)) continue;
            Render3DUtils.ProjectedBox projected = projectedPlayer.bounds();

            float espWidth = projected.width() * 0.5f * skija.width();
            if (espWidth <= 2f) continue;
            TargetIndicator targetIndicator = friend != null && friend.isEnabled() && Friend.isFriend(player)
                    ? new TargetIndicator(friendLabel, 0xFF96C077)
                    : targetIndicators.label(player.getId());
            ItemWarn.Warning warning = itemWarn == null
                    ? ItemWarn.detect(minecraft.player, player)
                    : itemWarn.nametagWarning(minecraft.player, player);
            LabelCache label = label(player, minecraft.player, skija, guiScale, gameTime,
                    targetIndicator.text(), targetIndicator.color(), warning, nowNanos);
            LabelVisual current = label.currentVisual();
            LabelVisual previous = label.previousVisual(nowNanos);
            float transition = label.transitionProgress(nowNanos);
            float naturalWidth = Math.max(current.naturalWidth(),
                    previous == null ? 0f : previous.naturalWidth());
            float relativeScale = Math.max(MIN_SCALE, Math.min(1f, espWidth / naturalWidth));
            float scale = guiScale * relativeScale;
            float width = Math.max(espWidth, naturalWidth * relativeScale);
            entries.add(new Entry(
                    (projected.left() + projected.right()) * 0.5f, projected.top(), width,
                    HEIGHT * scale, scale, current, previous, transition,
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
                    drawLabelText(frame, entry, centerX, y);
                }
            }
        });
    }

    @Override
    protected void onDisable() {
        labels.clear();
        labelLevel = null;
        labelsCleanupTick = Long.MIN_VALUE;
    }

    private LabelCache label(Player player, Player viewer, SkijaRenderer skija,
                             float guiScale, long gameTime, String targetIndicator, int targetColor,
                             ItemWarn.Warning warning, long nowNanos) {
        LabelCache cached = labels.computeIfAbsent(player.getId(), ignored -> new LabelCache());
        if (cached.tick == gameTime && Float.compare(cached.guiScale, guiScale) == 0
                && targetIndicator.equals(cached.targetIndicator)
                && warning.key().equals(cached.warning.key())) return cached;

        String name = player.getName().getString();
        int distanceTenths = Math.round(viewer.distanceTo(player) * 10f);
        boolean textChanged = !name.equals(cached.name)
                || distanceTenths != cached.distanceTenths
                || !targetIndicator.equals(cached.targetIndicator)
                || Float.compare(cached.guiScale, guiScale) != 0;
        String metadata = textChanged
                ? String.format(Locale.ROOT, "ID %d  ·  %.1fm", player.getId(), distanceTenths / 10f)
                : cached.metadata;
        boolean contentChanged = !name.equals(cached.name)
                || !targetIndicator.equals(cached.targetIndicator)
                || !warning.key().equals(cached.warning.key());
        if (!name.equals(cached.name) || !metadata.equals(cached.metadata)
                || !targetIndicator.equals(cached.targetIndicator)
                || Float.compare(cached.guiScale, guiScale) != 0) {
            if (contentChanged && !cached.name.isEmpty()) {
                cached.previous = cached.currentVisual();
                cached.transitionStartedNanos = nowNanos;
            }
            cached.name = name;
            cached.metadata = metadata;
            cached.guiScale = guiScale;
            cached.targetColor = targetColor;
            cached.warning = warning;
            String indicator = targetIndicator;
            cached.metadataWidth = skija.text().width(META_FONT, META_SIZE * guiScale, metadata);
            cached.indicatorWidth = indicator.isEmpty() ? 0f
                    : skija.text().width(META_FONT, META_SIZE * guiScale, INDICATOR_DOT)
                    + INDICATOR_GAP * guiScale
                    + skija.text().width(META_FONT, META_SIZE * guiScale, indicator);
            float warningWidth = skija.text().width(NAME_FONT, NAME_SIZE * guiScale, warning.label());
            cached.warningWidth = warningWidth;
            cached.naturalWidth = Math.max(
                    warningWidth + skija.text().width(NAME_FONT, NAME_SIZE * guiScale, name),
                    cached.metadataWidth + cached.indicatorWidth
            ) + PADDING_X * guiScale * 2f;
        }
        cached.distanceTenths = distanceTenths;
        cached.tick = gameTime;
        cached.targetIndicator = targetIndicator;
        return cached;
    }

    private static void drawLabelText(SkijaFrame frame, Entry entry, float centerX, float y) {
        float progress = smoothStep(entry.transition());
        if (entry.previous() != null && progress < 1f) {
            drawLabelVisual(frame, entry, centerX, y, entry.previous(),
                    1f - progress, -3f * progress * entry.scale());
        }
        drawLabelVisual(frame, entry, centerX, y, entry.current(),
                progress, 3f * (1f - progress) * entry.scale());
    }

    private static void drawLabelVisual(SkijaFrame frame, Entry entry, float centerX, float y,
                                        LabelVisual visual, float opacity, float offsetY) {
        if (opacity <= 0f) return;
        int nameColor = multiplyAlpha(0xFFF4F7F8, opacity);
        int metadataColor = multiplyAlpha(0xFFB8C0C7, opacity);
        int indicatorColor = multiplyAlpha(visual.indicatorColor(), opacity);
        float nameSize = NAME_SIZE * entry.scale();
        float metadataSize = META_SIZE * entry.scale();
        float visualScale = entry.scale() / entry.guiScale();
        float metadataWidth = visual.metadataWidth() * visualScale;
        float indicatorWidth = visual.indicatorWidth() * visualScale;
        float metadataX = centerX - (metadataWidth + indicatorWidth) * 0.5f;

        float warningWidth = visual.warningWidth() * visualScale;
        float nameWidth = frame.text().width(NAME_FONT, nameSize, visual.name());
        float nameStart = centerX - (warningWidth + nameWidth) * 0.5f;
        if (!visual.warning().isEmpty()) {
            frame.text().draw(NAME_FONT, nameSize, visual.warning(), nameStart,
                    y + 4f * entry.scale() + offsetY,
                    multiplyAlpha(visual.warningColor(), opacity));
            nameStart += warningWidth;
        }
        frame.text().draw(NAME_FONT, nameSize, visual.name(), nameStart,
                y + 4f * entry.scale() + offsetY, nameColor);
        frame.text().draw(META_FONT, metadataSize, visual.metadata(),
                metadataX, y + META_Y * entry.scale() + offsetY, metadataColor);
        if (!visual.indicator().isEmpty()) {
            float dotWidth = frame.text().width(META_FONT, metadataSize, INDICATOR_DOT);
            frame.text().draw(META_FONT, metadataSize, INDICATOR_DOT,
                    metadataX + metadataWidth, y + META_Y * entry.scale() + offsetY, metadataColor);
            frame.text().draw(META_FONT, metadataSize, visual.indicator(),
                    metadataX + metadataWidth + dotWidth + INDICATOR_GAP * entry.scale(),
                    y + META_Y * entry.scale() + offsetY,
                    indicatorColor);
        }
    }

    private static float smoothStep(float value) {
        float t = Math.clamp(value, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private static int multiplyAlpha(int color, float opacity) {
        int alpha = Math.round(((color >>> 24) & 0xFF) * Math.clamp(opacity, 0f, 1f));
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private TargetIndicators targetIndicators() {
        Projectile projectile = Core.INSTANCE.getModuleManager().getModule(Projectile.class);
        BowAssistant bowAssistant = Core.INSTANCE.getModuleManager().getModule(BowAssistant.class);

        int projectileTargetId = projectile != null && projectile.isEnabled()
                ? projectile.getTargetEntityId() : -1;
        int bowTargetId = bowAssistant != null && bowAssistant.isEnabled() && bowAssistant.getTarget() != null
                ? bowAssistant.getTarget().getId() : -1;
        String projectileLabel = projectileTargetId == -1 ? "" : Core.INSTANCE.getI18nManager().get(
                projectile.getDisplayNameTranslationKey(), projectile.getDisplayName());
        String bowLabel = bowTargetId == -1 ? "" : Core.INSTANCE.getI18nManager().get(
                bowAssistant.getDisplayNameTranslationKey(), bowAssistant.getDisplayName());
        return new TargetIndicators(projectileTargetId, bowTargetId, projectileLabel, bowLabel);
    }

    private static float screen(float normalized, float size) {
        return (normalized + 1f) * 0.5f * size;
    }

    private record Entry(float centerX, float top, float width, float height, float scale,
                         LabelVisual current, LabelVisual previous, float transition, int accent) {
        private float guiScale() {
            return current.guiScale();
        }
    }

    private record TargetIndicators(int projectileTargetId, int bowTargetId,
                                    String projectileLabel, String bowLabel) {
        private TargetIndicator label(int entityId) {
            boolean projectile = projectileTargetId == entityId;
            boolean bow = bowTargetId == entityId;
            if (projectile && bow) return new TargetIndicator(projectileLabel + " / " + bowLabel, 0xFFFFFFFF);
            if (projectile) return new TargetIndicator(projectileLabel, 0xFFFFFFFF);
            return bow ? new TargetIndicator(bowLabel, 0xFFFFFFFF) : new TargetIndicator("", 0xFFFFFFFF);
        }
    }

    private record TargetIndicator(String text, int color) {
    }

    private static final class LabelCache {
        private long tick = Long.MIN_VALUE;
        private float guiScale = Float.NaN;
        private int distanceTenths = Integer.MIN_VALUE;
        private float naturalWidth;
        private float metadataWidth;
        private float indicatorWidth;
        private float warningWidth;
        private String name = "";
        private String metadata = "";
        private String targetIndicator = "";
        private int targetColor = 0xFFFFFFFF;
        private ItemWarn.Warning warning = ItemWarn.detect(null, null);
        private LabelVisual previous;
        private long transitionStartedNanos = Long.MIN_VALUE;

        private LabelVisual currentVisual() {
            String indicator = targetIndicator;
            return new LabelVisual(name, warning.label(), metadata, indicator, metadataWidth, indicatorWidth,
                    this.warningWidth, naturalWidth, guiScale, targetColor, warning.color());
        }

        private LabelVisual previousVisual(long nowNanos) {
            if (previous == null) return null;
            if (transitionProgress(nowNanos) >= 1f) {
                previous = null;
                transitionStartedNanos = Long.MIN_VALUE;
                return null;
            }
            return previous;
        }

        private float transitionProgress(long nowNanos) {
            if (previous == null || transitionStartedNanos == Long.MIN_VALUE) return 1f;
            return Math.clamp((nowNanos - transitionStartedNanos) / (float) TEXT_TRANSITION_NANOS, 0f, 1f);
        }
    }

    private record LabelVisual(String name, String warning, String metadata, String indicator,
                               float metadataWidth, float indicatorWidth, float warningWidth,
                               float naturalWidth, float guiScale, int indicatorColor, int warningColor) { }
}
