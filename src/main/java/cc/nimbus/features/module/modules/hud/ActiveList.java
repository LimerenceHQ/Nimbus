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
import io.github.humbleui.skija.ClipMode;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.PathBuilder;
import io.github.humbleui.types.Point;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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
    private static final float PATH_PIXEL_EPSILON = .75f;
    private static final float TEXT_REVEAL_START = .58f;
    private static final int BACKGROUND_COLOR = 0x5C171A1E;
    private static final int TEXT_COLOR = 0xFFF4F7F8;
    private static final int SUFFIX_COLOR = 0xFF9EA8B2;

    public final BooleanSetting showSuffix = new BooleanSetting("Show suffix", true);
    public final BooleanSetting hideVisuals = new BooleanSetting("Hide visuals", false);
    public final BooleanSetting background = new BooleanSetting("Background", true);
    public final BooleanSetting rightRect = new BooleanSetting("Right rect", true);
    public final ColorSetting accent = new ColorSetting("Accent", 0xFF82A9FF, this::customStyle);
    private HudSize layoutSize = new HudSize(2f, 2f);
    private final Map<String, Entry> displayedEntries = new HashMap<>();
    private final Map<String, Float> entryAnimations = new HashMap<>();
    private List<Entry> activeEntries = List.of();
    private Set<String> activeKeys = Set.of();
    private long nextContentRefreshNanos;
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
        if (nowNanos >= nextContentRefreshNanos) {
            refreshActiveEntries(skija);
            nextContentRefreshNanos = nowNanos + 50_000_000L;
        }
        reconcileEntries(delta);
        if (displayedEntries.isEmpty()) {
            layoutSize = new HudSize(2f, 2f);
            return;
        }

        HudAnchor anchor = drag.getAnchor();
        List<RenderEntry> entries = layoutEntries(anchor);
        float totalWidth = 2f;
        float totalHeight = 0f;
        for (RenderEntry entry : entries) {
            totalWidth = Math.max(totalWidth, entry.entry().width());
            totalHeight += entry.height();
        }
        totalHeight = Math.max(2f, totalHeight);
        float layoutWidth = totalWidth;
        float layoutHeight = totalHeight;
        layoutSize = new HudSize(layoutWidth, layoutHeight);
        HudRect bounds = drag.getRect(viewport);
        float scale = drag.getRenderScale(viewport);
        boolean drawBackground = background.getValue();
        ClientSettings clientSettings = Core.INSTANCE.getConfigManager().clientSettings();
        rightRectProgress = animate(rightRectProgress, rightRect.getValue() ? 1f : 0f,
                1f - (float) Math.exp(-delta * 14f));
        long colorTime = System.currentTimeMillis();
        int[] accentColors = accentColors(clientSettings, entries.size(), anchor, colorTime);
        List<RenderEntry> pathEntries = orderedByY(entries).stream()
                .filter(entry -> entry.height() * scale > PATH_PIXEL_EPSILON)
                .toList();

        if (drawBackground) {
            try (Path regions = backgroundPath(pathEntries, layoutWidth, layoutHeight,
                    bounds.x(), bounds.y(), scale, anchor.isRight())) {
                skija.postProcess().add(regions);
            }
        }

        skija.hud().submit(frame -> drag.render(frame, viewport, local -> {
            try (Path surface = backgroundPath(pathEntries, layoutWidth, layoutHeight,
                    0f, 0f, 1f, anchor.isRight());
                 Paint backgroundPaint = new Paint().setAntiAlias(true).setColor(BACKGROUND_COLOR);
                 Paint accentPaint = new Paint().setAntiAlias(true);
                 Paint textPaint = new Paint().setAntiAlias(true)) {
                if (drawBackground) local.canvas().drawPath(surface, backgroundPaint);
                float decorWidth = ACCENT_WIDTH * rightRectProgress;
                if (decorWidth > .01f) {
                    float decorX = anchor.isRight() ? layoutWidth - decorWidth : 0f;
                    accentPaint.setShader(local.effects().linearGradient(
                            new Point(decorX, 0f), new Point(decorX, layoutHeight), accentColors));
                    int clipSave = local.canvas().save();
                    try {
                        local.canvas().clipPath(surface, ClipMode.INTERSECT, true);
                        local.draw().rect(decorX, 0f, decorWidth, layoutHeight, accentPaint);
                    } finally {
                        local.canvas().restoreToCount(clipSave);
                    }
                }
                for (RenderEntry rendered : entries) {
                    Entry entry = rendered.entry();
                    float textProgress = rendered.textProgress();
                    float rowY = rendered.y();
                    float rowX = anchor.isRight() ? layoutWidth - entry.width() : 0f;
                    float offset = anchor.isRight() ? (1f - textProgress) * 6f : -(1f - textProgress) * 6f;
                    int textColor = multiplyAlpha(TEXT_COLOR, textProgress);
                    int suffixColor = multiplyAlpha(SUFFIX_COLOR, textProgress);
                    float textAreaX = rowX + (anchor.isRight() ? 0f : decorWidth);
                    float textCenterX = textAreaX + (entry.width() - decorWidth) * .5f + offset;
                    float labelX = textCenterX - entry.visualCenterX();
                    float textY = rowY + ROW_HEIGHT * .5f - entry.visualCenterY();
                    String content = entry.name() + entry.suffix();
                    float baseline = local.text().baselineFromTop(FONT, FONT_SIZE, content, textY);
                    textPaint.setColor(textColor);
                    local.text().drawAtBaseline(FONT, FONT_SIZE, entry.name(), labelX, baseline, textPaint);
                    if (!entry.suffix().isEmpty()) {
                        textPaint.setColor(suffixColor);
                        local.text().drawAtBaseline(FONT, FONT_SIZE, entry.suffix(),
                                labelX + entry.nameWidth(), baseline, textPaint);
                    }
                }
            }
        }));
    }

    @Override
    protected void onDisable() {
        displayedEntries.clear();
        entryAnimations.clear();
        activeEntries = List.of();
        activeKeys = Set.of();
        nextContentRefreshNanos = 0L;
        lastFrameNanos = 0L;
        rightRectProgress = 0f;
    }

    private void refreshActiveEntries(SkijaRenderer skija) {
        List<Entry> entries = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (BasicModule module : Core.INSTANCE.getModuleManager().getModules()) {
            if (!module.isEnabled() || module.isHidden() || module == this) continue;
            if (hideVisuals.getValue()
                    && (module.getCategory() == ModuleCategory.Render
                    || module.getCategory() == ModuleCategory.HUD)) continue;
            String name = Core.INSTANCE.getI18nManager().get(
                    module.getDisplayNameTranslationKey(), module.getDisplayName());
            String suffix = showSuffix.getValue() && !module.getSuffix().isBlank()
                    ? " " + module.getSuffix() : "";
            float nameWidth = skija.text().width(FONT, FONT_SIZE, name);
            float textWidth = nameWidth + skija.text().width(FONT, FONT_SIZE, suffix);
            String content = name + suffix;
            String key = module.getName();
            keys.add(key);
            entries.add(new Entry(key, name, suffix, nameWidth,
                    Math.max(48f, textWidth + PADDING_X * 2f + ACCENT_WIDTH),
                    (skija.text().visualLeft(FONT, FONT_SIZE, content)
                            + skija.text().visualRight(FONT, FONT_SIZE, content)) * .5f,
                    skija.text().visualCenterY(FONT, FONT_SIZE, content)));
        }
        activeEntries = List.copyOf(entries);
        activeKeys = Set.copyOf(keys);
    }

    private void reconcileEntries(float delta) {
        float speed = 1f - (float) Math.exp(-delta * 12f);
        for (Entry entry : activeEntries) {
            displayedEntries.put(entry.key(), entry);
            entryAnimations.put(entry.key(), animate(
                    entryAnimations.getOrDefault(entry.key(), 0f), 1f, speed));
        }
        Iterator<Map.Entry<String, Entry>> iterator = displayedEntries.entrySet().iterator();
        while (iterator.hasNext()) {
            String key = iterator.next().getKey();
            if (activeKeys.contains(key)) continue;
            float progress = animate(entryAnimations.getOrDefault(key, 1f), 0f, speed);
            if (progress <= .002f) {
                iterator.remove();
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
        for (Entry entry : sorted) {
            totalHeight += ROW_HEIGHT * backgroundProgress(entryAnimations.getOrDefault(entry.key(), 1f));
        }
        List<RenderEntry> result = new ArrayList<>(sorted.size());
        float cursor = anchor.isBottom() ? totalHeight : 0f;
        for (Entry entry : sorted) {
            float progress = entryAnimations.getOrDefault(entry.key(), 1f);
            float backgroundProgress = backgroundProgress(progress);
            float textProgress = textProgress(progress);
            float height = ROW_HEIGHT * backgroundProgress;
            float y;
            if (anchor.isBottom()) {
                cursor -= height;
                y = cursor;
            } else {
                y = cursor;
                cursor += height;
            }
            result.add(new RenderEntry(entry, backgroundProgress, textProgress, y, height));
        }
        return result;
    }

    private static List<RenderEntry> orderedByY(List<RenderEntry> entries) {
        List<RenderEntry> ordered = new ArrayList<>(entries);
        ordered.sort(Comparator.comparingDouble(RenderEntry::y));
        return ordered;
    }

    private boolean customStyle() {
        return Core.INSTANCE.isInitialized()
                && Core.INSTANCE.getConfigManager().clientSettings().customStyle();
    }

    private int[] accentColors(ClientSettings settings, int entryCount, HudAnchor anchor, long timeMillis) {
        int stopCount = Math.max(2, entryCount);
        int[] colors = new int[stopCount];
        boolean custom = customStyle();
        for (int visualIndex = 0; visualIndex < stopCount; visualIndex++) {
            int colorIndex = anchor.isBottom() ? stopCount - visualIndex - 1 : visualIndex;
            colors[visualIndex] = custom
                    ? accent.getValue() : settings.accentColor(colorIndex, timeMillis);
        }
        return colors;
    }

    private static Path backgroundPath(List<RenderEntry> entries,
                                      float totalWidth, float totalHeight,
                                      float originX, float originY, float scale,
                                      boolean rightAnchored) {
        try (PathBuilder path = new PathBuilder()) {
            if (entries.isEmpty()) return path.build();
            RenderEntry first = entries.getFirst();
            RenderEntry last = entries.getLast();
            float top = originY + first.y() * scale;
            float bottom = originY + (last.y() + last.height()) * scale;
            float right = originX + totalWidth * scale;
            float firstX = animatedLeft(entries, 0, totalWidth, originX, scale);
            float outerRadius = Math.min(CORNER_RADIUS * scale, (bottom - top) * .5f);
            float firstRadius = outerRadius;
            path.moveTo(orientedX(firstX + firstRadius, originX, right, rightAnchored), top);
            path.lineTo(orientedX(right, originX, right, rightAnchored), top);
            path.lineTo(orientedX(right, originX, right, rightAnchored), bottom);
            float lastX = animatedLeft(entries, entries.size() - 1, totalWidth, originX, scale);
            float lastRadius = outerRadius;
            path.lineTo(orientedX(lastX + lastRadius, originX, right, rightAnchored), bottom);
            path.quadTo(orientedX(lastX, originX, right, rightAnchored), bottom,
                    orientedX(lastX, originX, right, rightAnchored), bottom - lastRadius);
            for (int index = entries.size() - 1; index > 0; index--) {
                RenderEntry lower = entries.get(index);
                RenderEntry upper = entries.get(index - 1);
                float boundary = originY + lower.y() * scale;
                float lowerX = animatedLeft(entries, index, totalWidth, originX, scale);
                float upperX = animatedLeft(entries, index - 1, totalWidth, originX, scale);
                float distance = Math.abs(lowerX - upperX);
                if (distance < .01f) {
                    path.lineTo(orientedX(lowerX, originX, right, rightAnchored), boundary);
                    continue;
                }
                float maxRadius = Math.min(CORNER_RADIUS * scale, distance * .5f);
                float lowerRadius = Math.min(maxRadius, lower.height() * scale * .5f);
                float upperRadius = Math.min(maxRadius, upper.height() * scale * .5f);
                float direction = Math.signum(upperX - lowerX);
                path.lineTo(orientedX(lowerX, originX, right, rightAnchored), boundary + lowerRadius);
                if (lowerRadius > .01f) {
                    path.quadTo(orientedX(lowerX, originX, right, rightAnchored), boundary,
                            orientedX(lowerX + direction * lowerRadius, originX, right, rightAnchored), boundary);
                }
                path.lineTo(orientedX(upperX - direction * upperRadius,
                        originX, right, rightAnchored), boundary);
                if (upperRadius > .01f) {
                    path.quadTo(orientedX(upperX, originX, right, rightAnchored), boundary,
                            orientedX(upperX, originX, right, rightAnchored), boundary - upperRadius);
                }
            }
            float topRadius = outerRadius;
            path.lineTo(orientedX(firstX, originX, right, rightAnchored), top + topRadius);
            path.quadTo(orientedX(firstX, originX, right, rightAnchored), top,
                    orientedX(firstX + topRadius, originX, right, rightAnchored), top);
            path.closePath();
            return path.build();
        }
    }

    private static float orientedX(float x, float left, float right, boolean rightAnchored) {
        return rightAnchored ? x : left + right - x;
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

    private static float backgroundProgress(float progress) {
        return smooth(progress / TEXT_REVEAL_START);
    }

    private static float textProgress(float progress) {
        return smooth((progress - TEXT_REVEAL_START) / (1f - TEXT_REVEAL_START));
    }

    private static int multiplyAlpha(int color, float opacity) {
        int alpha = Math.round((color >>> 24) * Math.clamp(opacity, 0f, 1f));
        return color & 0x00FFFFFF | alpha << 24;
    }

    private record Entry(String key, String name, String suffix, float nameWidth, float width,
                         float visualCenterX, float visualCenterY) {}
    private record RenderEntry(Entry entry, float progress, float textProgress, float y, float height) {}
}
