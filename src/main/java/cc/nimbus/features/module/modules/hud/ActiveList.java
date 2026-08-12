package cc.nimbus.features.module.modules.hud;

import cc.nimbus.core.Core;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.render.EventRender2D;
import cc.nimbus.features.client.ClientSettings;
import cc.nimbus.features.hud.Draggable;
import cc.nimbus.features.hud.HudAnchor;
import cc.nimbus.features.hud.HudRect;
import cc.nimbus.features.hud.HudSize;
import cc.nimbus.features.hud.HudViewport;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.BooleanSetting;
import cc.nimbus.features.module.api.setting.impl.ColorSetting;
import cc.nimbus.utils.render.skija.api.SkijaRenderer;
import cc.nimbus.utils.render.font.NimbusFonts;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.PathBuilder;
import io.github.humbleui.types.Point;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ModuleInfo(
        name = "ArrayList",
        displayName = "Active List",
        alias = {"ActiveList"},
        description = "Displays enabled modules.",
        category = ModuleCategory.HUD,
        enableOnStartUp = true
)
public final class ActiveList extends BasicModule {
    private static final String FONT = NimbusFonts.BODY_MEDIUM;
    private static final float FONT_SIZE = 15f;
    private static final float ROW_HEIGHT = 25f;
    private static final float PADDING_X = 8f;
    private static final float ACCENT_WIDTH = 2.5f;
    private static final float CORNER_RADIUS = 5f;
    private static final int BACKGROUND_COLOR = 0x5C171A1E;
    private static final int TEXT_COLOR = 0xFFF4F7F8;
    private static final int SUFFIX_COLOR = 0xFF9EA8B2;

    public final BooleanSetting showSuffix = new BooleanSetting("Show suffix", true);
    public final BooleanSetting background = new BooleanSetting("Background", true);
    public final BooleanSetting rightRect = new BooleanSetting("Right rect", true);
    public final ColorSetting accent = new ColorSetting("Accent", 0xFF82A9FF, this::customStyle);
    private HudSize layoutSize = new HudSize(2f, 2f);
    private final Map<String, Entry> displayedEntries = new HashMap<>();
    private final Map<String, Float> entryAnimations = new HashMap<>();
    private long lastFrameNanos;
    private float rightRectProgress;

    private final Draggable drag = new Draggable("ActiveList", 0.98, 0.02) {
        @Override
        public HudSize getSize() {
            return layoutSize;
        }
    };

    @EventTarget
    private void onRender2D(EventRender2D event) {
        SkijaRenderer skija = Core.INSTANCE.getRenderManager().skija();
        HudViewport viewport = Core.INSTANCE.getHudManager().viewport(skija.width(), skija.height());
        long nowNanos = System.nanoTime();
        float delta = lastFrameNanos == 0L ? 1f / 60f
                : Math.min(.05f, (nowNanos - lastFrameNanos) / 1_000_000_000f);
        lastFrameNanos = nowNanos;
        reconcileEntries(activeEntries(skija), delta);
        if (displayedEntries.isEmpty()) {
            layoutSize = new HudSize(2f, 2f);
            return;
        }

        HudAnchor anchor = drag.getAnchor();
        List<RenderEntry> entries = layoutEntries(anchor);
        float totalWidth = maxWidth(entries);
        float totalHeight = Math.max(2f, (float) entries.stream().mapToDouble(RenderEntry::height).sum());
        layoutSize = new HudSize(totalWidth, totalHeight);
        HudRect bounds = drag.getRect(viewport);
        float scale = drag.getRenderScale(viewport);
        boolean drawBackground = background.getValue();
        ClientSettings clientSettings = Core.INSTANCE.getConfigManager().clientSettings();
        rightRectProgress = animate(rightRectProgress, rightRect.getValue() ? 1f : 0f,
                1f - (float) Math.exp(-delta * 14f));
        long colorTime = System.currentTimeMillis();
        int[] accentColors = accentColors(clientSettings, entries.size(), anchor, colorTime);

        if (drawBackground) {
            try (Path regions = backgroundPath(entries, totalWidth, totalHeight,
                    bounds.x(), bounds.y(), scale)) {
                skija.postProcess().add(regions);
            }
        }

        List<RenderEntry> frameEntries = List.copyOf(entries);
        skija.hud().submit(frame -> drag.render(frame, viewport, local -> {
            try (Path surface = backgroundPath(frameEntries, totalWidth, totalHeight,
                    0f, 0f, 1f);
                 Paint backgroundPaint = new Paint().setAntiAlias(true).setColor(BACKGROUND_COLOR);
                 Paint accentPaint = new Paint().setAntiAlias(true)) {
                if (drawBackground) local.canvas().drawPath(surface, backgroundPaint);
                float decorWidth = ACCENT_WIDTH * rightRectProgress;
                if (decorWidth > .01f) {
                    accentPaint.setShader(local.effects().linearGradient(
                            new Point(totalWidth - decorWidth, 0f),
                            new Point(totalWidth - decorWidth, totalHeight), accentColors));
                    local.draw().rect(totalWidth - decorWidth, 0f,
                            decorWidth, totalHeight, accentPaint);
                }
                for (int index = 0; index < frameEntries.size(); index++) {
                    RenderEntry rendered = frameEntries.get(index);
                    Entry entry = rendered.entry();
                    float entryProgress = rendered.progress();
                    float rowY = rendered.y();
                    float rowX = totalWidth - entry.width();
                    float offset = anchor.isRight() ? (1f - entryProgress) * 8f : -(1f - entryProgress) * 8f;
                    int textColor = multiplyAlpha(TEXT_COLOR, entryProgress);
                    int suffixColor = multiplyAlpha(SUFFIX_COLOR, entryProgress);
                    String content = entry.name() + entry.suffix();
                    float visualCenterX = (local.text().visualLeft(FONT, FONT_SIZE, content)
                            + local.text().visualRight(FONT, FONT_SIZE, content)) * .5f;
                    float textCenterX = rowX + (entry.width() - decorWidth) * .5f + offset;
                    float labelX = textCenterX - visualCenterX;
                    float textY = rowY + rendered.height() * .5f
                            - local.text().visualCenterY(FONT, FONT_SIZE, content);
                    local.text().draw(FONT, FONT_SIZE, entry.name(), labelX, textY, textColor);
                    if (!entry.suffix().isEmpty()) {
                        local.text().draw(FONT, FONT_SIZE, entry.suffix(),
                                labelX + entry.nameWidth(), textY, suffixColor);
                    }
                }
            }
        }));
    }

    @Override
    protected void onDisable() {
        displayedEntries.clear();
        entryAnimations.clear();
        lastFrameNanos = 0L;
        rightRectProgress = 0f;
    }

    private List<Entry> activeEntries(SkijaRenderer skija) {
        List<Entry> entries = new ArrayList<>();
        for (BasicModule module : Core.INSTANCE.getModuleManager().getModules()) {
            if (!module.isEnabled() || module.isHidden() || module == this) continue;
            String name = Core.INSTANCE.getI18nManager().get(
                    module.getDisplayNameTranslationKey(), module.getDisplayName());
            String suffix = showSuffix.getValue() && !module.getSuffix().isBlank()
                    ? " " + module.getSuffix() : "";
            float nameWidth = skija.text().width(FONT, FONT_SIZE, name);
            float textWidth = nameWidth + skija.text().width(FONT, FONT_SIZE, suffix);
            entries.add(new Entry(module.getName(), name, suffix, nameWidth,
                    Math.max(48f, textWidth + PADDING_X * 2f + ACCENT_WIDTH)));
        }
        return entries;
    }

    private void reconcileEntries(List<Entry> active, float delta) {
        Set<String> activeKeys = new HashSet<>();
        float speed = 1f - (float) Math.exp(-delta * 12f);
        for (Entry entry : active) {
            activeKeys.add(entry.key());
            displayedEntries.put(entry.key(), entry);
            entryAnimations.put(entry.key(), animate(
                    entryAnimations.getOrDefault(entry.key(), 0f), 1f, speed));
        }
        for (String key : List.copyOf(displayedEntries.keySet())) {
            if (activeKeys.contains(key)) continue;
            float progress = animate(entryAnimations.getOrDefault(key, 1f), 0f, speed);
            if (progress <= .002f) {
                displayedEntries.remove(key);
                entryAnimations.remove(key);
            } else {
                entryAnimations.put(key, progress);
            }
        }
    }

    private List<RenderEntry> layoutEntries(HudAnchor anchor) {
        List<Entry> sorted = new ArrayList<>(displayedEntries.values());
        sorted.sort(Comparator.comparingDouble(Entry::width).reversed()
                .thenComparing(Entry::name, String.CASE_INSENSITIVE_ORDER));
        float totalHeight = 0f;
        for (Entry entry : sorted) totalHeight += ROW_HEIGHT * smooth(entryAnimations.getOrDefault(entry.key(), 1f));
        List<RenderEntry> result = new ArrayList<>(sorted.size());
        float cursor = anchor.isBottom() ? totalHeight : 0f;
        for (Entry entry : sorted) {
            float progress = smooth(entryAnimations.getOrDefault(entry.key(), 1f));
            float height = ROW_HEIGHT * progress;
            float y;
            if (anchor.isBottom()) {
                cursor -= height;
                y = cursor;
            } else {
                y = cursor;
                cursor += height;
            }
            result.add(new RenderEntry(entry, progress, y, height));
        }
        return result;
    }

    private static float maxWidth(List<RenderEntry> entries) {
        return Math.max(2f, entries.stream().map(entry -> entry.entry().width())
                .max(Float::compare).orElse(2f));
    }

    private boolean customStyle() {
        return Core.INSTANCE.isInitialized()
                && Core.INSTANCE.getConfigManager().clientSettings().customStyle();
    }

    private int[] accentColors(ClientSettings settings, int entryCount, HudAnchor anchor, long timeMillis) {
        int stopCount = Math.max(2, entryCount);
        int[] colors = new int[stopCount];
        for (int visualIndex = 0; visualIndex < stopCount; visualIndex++) {
            int colorIndex = anchor.isBottom() ? stopCount - visualIndex - 1 : visualIndex;
            colors[visualIndex] = customStyle()
                    ? accent.getValue() : settings.accentColor(colorIndex, timeMillis);
        }
        return colors;
    }

    private static Path backgroundPath(List<RenderEntry> entries,
                                      float totalWidth, float totalHeight,
                                      float originX, float originY, float scale) {
        List<RenderEntry> ordered = new ArrayList<>(entries);
        ordered.sort(Comparator.comparingDouble(RenderEntry::y));
        PathBuilder path = new PathBuilder();
        if (ordered.isEmpty()) return path.build();
        RenderEntry first = ordered.getFirst();
        RenderEntry last = ordered.getLast();
        float top = originY + first.y() * scale;
        float bottom = originY + (last.y() + last.height()) * scale;
        float right = originX + totalWidth * scale;
        float firstX = animatedLeft(ordered, 0, totalWidth, originX, scale);
        float outerRadius = Math.min(CORNER_RADIUS * scale, totalHeight * scale * .5f);
        float firstRadius = outerRadius;
        path.moveTo(firstX + firstRadius, top);
        path.lineTo(right, top);
        path.lineTo(right, bottom);
        float lastX = animatedLeft(ordered, ordered.size() - 1, totalWidth, originX, scale);
        float lastRadius = outerRadius;
        path.lineTo(lastX + lastRadius, bottom);
        path.quadTo(lastX, bottom, lastX, bottom - lastRadius);
        for (int index = ordered.size() - 1; index > 0; index--) {
            RenderEntry lower = ordered.get(index);
            RenderEntry upper = ordered.get(index - 1);
            float boundary = originY + lower.y() * scale;
            float lowerX = animatedLeft(ordered, index, totalWidth, originX, scale);
            float upperX = animatedLeft(ordered, index - 1, totalWidth, originX, scale);
            float distance = Math.abs(lowerX - upperX);
            if (distance < .01f) {
                path.lineTo(lowerX, boundary);
                continue;
            }
            float radius = Math.min(CORNER_RADIUS * scale, distance * .5f);
            float direction = Math.signum(upperX - lowerX);
            path.lineTo(lowerX, boundary + radius);
            path.quadTo(lowerX, boundary, lowerX + direction * radius, boundary);
            path.lineTo(upperX - direction * radius, boundary);
            path.quadTo(upperX, boundary, upperX, boundary - radius);
        }
        float topRadius = outerRadius;
        path.lineTo(firstX, top + topRadius);
        path.quadTo(firstX, top, firstX + topRadius, top);
        path.closePath();
        return path.build();
    }

    private static float animatedLeft(List<RenderEntry> entries, int index,
                                      float totalWidth, float originX, float scale) {
        RenderEntry entry = entries.get(index);
        float own = totalWidth - entry.entry().width();
        float adjacent = own;
        if (index + 1 < entries.size()) {
            adjacent = totalWidth - entries.get(index + 1).entry().width();
        } else if (index > 0) {
            adjacent = totalWidth - entries.get(index - 1).entry().width();
        }
        float left = adjacent + (own - adjacent) * entry.progress();
        return originX + left * scale;
    }

    private static float animate(float value, float target, float speed) {
        float result = value + (target - value) * Math.clamp(speed, 0f, 1f);
        return Math.abs(result - target) < .002f ? target : result;
    }

    private static float smooth(float value) {
        float clamped = Math.clamp(value, 0f, 1f);
        return clamped * clamped * (3f - 2f * clamped);
    }

    private static int multiplyAlpha(int color, float opacity) {
        int alpha = Math.round((color >>> 24) * Math.clamp(opacity, 0f, 1f));
        return color & 0x00FFFFFF | alpha << 24;
    }

    private record Entry(String key, String name, String suffix, float nameWidth, float width) {}
    private record RenderEntry(Entry entry, float progress, float y, float height) {}
}
