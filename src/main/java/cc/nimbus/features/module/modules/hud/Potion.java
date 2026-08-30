package cc.nimbus.features.module.modules.hud;

import cc.nimbus.core.Core;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.render.EventRender2D;
import cc.nimbus.features.hud.Draggable;
import cc.nimbus.features.hud.HudAnchor;
import cc.nimbus.features.hud.HudRect;
import cc.nimbus.features.hud.HudSize;
import cc.nimbus.features.hud.HudViewport;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.utils.render.font.NimbusFonts;
import cc.nimbus.utils.render.skija.api.SkijaFrame;
import cc.nimbus.utils.render.skija.api.SkijaRenderer;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ModuleInfo(
        name = "Potion",
        displayName = "Potion",
        description = "Shows active potion effects and their remaining duration.",
        category = ModuleCategory.HUD
)
public final class Potion extends BasicModule {
    private static final String FONT = NimbusFonts.BODY_MEDIUM;
    private static final String TIME_FONT = NimbusFonts.BODY_MEDIUM;
    private static final float MAX_WIDTH = 230f;
    private static final float ROW_HEIGHT = 19f;
    private static final float ICON_SIZE = 18f;
    private static final float CONTENT_PADDING = 5f;
    private static final float TEXT_GAP = 5f;
    private static final float TIME_GAP = 4f;
    private static final float SLIDE_MARGIN = 24f;
    private static final String TIME_SLOT = "(99:59)";

    private HudSize layoutSize = new HudSize(2f, 2f);
    private final Map<String, EffectLine> displayedLines = new LinkedHashMap<>();
    private final Map<String, Float> lineAnimations = new HashMap<>();
    private long lastFrameNanos;

    private final Draggable drag = new Draggable("PotionHUD", 0.98, 0.38) {
        @Override
        public HudSize getSize() {
            return layoutSize;
        }
    };

    @EventTarget
    private void onRender2D(EventRender2D event) {
        if (event.isPost()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            resetAnimationState();
            return;
        }
        if (minecraft.screen != null && !(minecraft.screen instanceof ChatScreen)) return;

        List<MobEffectInstance> effects = new ArrayList<>();
        for (MobEffectInstance effect : minecraft.player.getActiveEffects()) {
            if (effect.isInfiniteDuration() || effect.getDuration() > 0) effects.add(effect);
        }
        SkijaRenderer skija = Core.INSTANCE.getRenderManager().skija();
        HudViewport viewport = Core.INSTANCE.getHudManager().viewport(skija.width(), skija.height());
        effects.sort(Comparator.comparingInt(Potion::displayLength)
                .thenComparing(Potion::effectKey));
        List<EffectLine> activeLines = effects.stream()
                .map(effect -> new EffectLine(
                        effectKey(effect),
                        displayName(effect),
                        duration(effect),
                        0xFF000000 | effect.getEffect().value().getColor(),
                        icon(effect)))
                .toList();

        float delta = frameDelta();
        float animationSpeed = 1f - (float) Math.exp(-14f * delta);
        Set<String> activeKeys = new HashSet<>();
        for (EffectLine line : activeLines) {
            activeKeys.add(line.key());
            displayedLines.put(line.key(), line);
            lineAnimations.put(line.key(), animate(
                    lineAnimations.getOrDefault(line.key(), 0f), 1f, animationSpeed));
        }
        Iterator<Map.Entry<String, EffectLine>> iterator = displayedLines.entrySet().iterator();
        while (iterator.hasNext()) {
            String key = iterator.next().getKey();
            if (activeKeys.contains(key)) continue;
            float progress = animate(lineAnimations.getOrDefault(key, 1f), 0f, animationSpeed);
            lineAnimations.put(key, progress);
            if (progress <= .002f) {
                iterator.remove();
                lineAnimations.remove(key);
            }
        }

        List<EffectLine> lines = new ArrayList<>(activeLines);
        for (EffectLine line : displayedLines.values()) {
            if (!activeKeys.contains(line.key())) lines.add(line);
        }
        lines.sort(Comparator.comparingInt(Potion::lineDisplayLength)
                .thenComparing(EffectLine::key));
        if (lines.isEmpty()) {
            layoutSize = new HudSize(2f, 2f);
            return;
        }

        float layoutWidth = measureWidth(skija, lines);
        List<AnimatedLine> snapshot = lines.stream()
                .map(line -> new AnimatedLine(line,
                        lineAnimations.getOrDefault(line.key(), 1f)))
                .toList();
        float layoutHeight = Math.max(2f, lines.size() * ROW_HEIGHT);
        layoutSize = new HudSize(layoutWidth, layoutHeight);
        HudAnchor anchor = drag.getAnchor();
        HudRect bounds = drag.getRect(viewport);
        float renderScale = drag.getRenderScale(viewport);
        float slideDistance = anchor.isRight()
                ? (viewport.width() - bounds.x()) / renderScale + SLIDE_MARGIN
                : bounds.right() / renderScale + SLIDE_MARGIN;
        skija.hud().submit(frame -> drag.render(frame, viewport,
                local -> draw(local, snapshot, layoutWidth, anchor.isRight(), slideDistance)));
    }

    @Override
    protected void onDisable() {
        resetAnimationState();
    }

    private void resetAnimationState() {
        layoutSize = new HudSize(2f, 2f);
        displayedLines.clear();
        lineAnimations.clear();
        lastFrameNanos = 0L;
    }

    private static void draw(SkijaFrame frame, List<AnimatedLine> lines,
                             float layoutWidth, boolean rightAligned, float slideDistance) {
        float availableWidth = Math.max(1f, layoutWidth - CONTENT_PADDING * 2f);
        float timeSlotWidth = frame.text().width(TIME_FONT, 12.5f, TIME_SLOT);
        float y = 0f;
        try (Paint imagePaint = new Paint().setAntiAlias(true)) {
            for (AnimatedLine animated : lines) {
                EffectLine line = animated.line();
                float progress = smooth(animated.progress());
                float rowCenterY = y + ROW_HEIGHT * .5f;
                String time = "(" + line.time() + ")";
                float nameColumnWidth = Math.max(1f,
                        availableWidth - ICON_SIZE - TEXT_GAP - TIME_GAP - timeSlotWidth);
                String visibleName = ellipsize(frame, line.name(), FONT, 13f, nameColumnWidth);
                float slide = (rightAligned ? 1f : -1f) * (1f - progress) * slideDistance;
                float nameWidth = frame.text().width(FONT, 13f, visibleName);
                float timeWidth = frame.text().width(TIME_FONT, 12.5f, time);
                float rowWidth = ICON_SIZE + TEXT_GAP + nameWidth + TIME_GAP + timeWidth;
                float iconX = (rightAligned ? layoutWidth - rowWidth : CONTENT_PADDING) + slide;
                float nameX = iconX + ICON_SIZE + TEXT_GAP;
                float timeX = nameX + nameWidth + TIME_GAP;
                Image icon = frame.images().resource(line.icon());
                if (icon != null && !icon.isClosed()) {
                    imagePaint.setAlpha(Math.round(255f * progress));
                    frame.draw().image(icon, iconX, rowCenterY - ICON_SIZE * .5f,
                            ICON_SIZE, ICON_SIZE, imagePaint);
                }
                float nameY = rowCenterY - frame.text().visualCenterY(FONT, 13f, visibleName);
                float timeY = rowCenterY - frame.text().visualCenterY(TIME_FONT, 12.5f, time);
                frame.text().drawWithShadow(FONT, 13f, visibleName, nameX, nameY,
                        multiplyAlpha(line.color(), progress));
                frame.text().drawWithShadow(TIME_FONT, 12.5f, time, timeX, timeY,
                        multiplyAlpha(0xFFE1E4E7, progress));
                y += ROW_HEIGHT;
            }
        }
    }

    private float frameDelta() {
        long now = System.nanoTime();
        float delta = lastFrameNanos == 0L ? 1f / 60f
                : Math.min(.05f, (now - lastFrameNanos) / 1_000_000_000f);
        lastFrameNanos = now;
        return delta;
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

    private static float measureWidth(SkijaRenderer skija, List<EffectLine> lines) {
        float width = 2f;
        for (EffectLine line : lines) {
            float timeSlotWidth = skija.text().width(TIME_FONT, 12.5f, TIME_SLOT);
            float rowWidth = CONTENT_PADDING * 2f + ICON_SIZE + TEXT_GAP
                    + skija.text().width(FONT, 13f, line.name())
                    + TIME_GAP + timeSlotWidth;
            width = Math.max(width, Math.min(MAX_WIDTH, rowWidth));
        }
        return width;
    }

    private static String ellipsize(SkijaFrame frame, String value, String font, float size, float width) {
        if (frame.text().width(font, size, value) <= width) return value;
        String result = value;
        while (result.length() > 1 && frame.text().width(font, size, result + "…") > width) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "…";
    }

    private static ResourceLocation icon(MobEffectInstance effect) {
        ResourceLocation key = BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value());
        if (key == null) key = ResourceLocation.fromNamespaceAndPath("minecraft", "speed");
        return ResourceLocation.fromNamespaceAndPath(key.getNamespace(),
                "textures/mob_effect/" + key.getPath() + ".png");
    }

    private static String displayName(MobEffectInstance effect) {
        String name = effect.getEffect().value().getDisplayName().getString();
        return effect.getAmplifier() > 0 ? name + " " + (effect.getAmplifier() + 1) : name;
    }

    private static String effectKey(MobEffectInstance effect) {
        ResourceLocation key = BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value());
        return key == null ? displayName(effect) : key.toString();
    }

    private static int displayLength(MobEffectInstance effect) {
        return displayName(effect).codePointCount(0, displayName(effect).length());
    }

    private static int lineDisplayLength(EffectLine line) {
        return line.name().codePointCount(0, line.name().length());
    }

    private static String duration(MobEffectInstance effect) {
        if (effect.isInfiniteDuration()) return "∞";
        int seconds = Math.max(0, effect.getDuration() / 20);
        int minutes = seconds / 60;
        int remainder = seconds % 60;
        return minutes + ":" + (remainder < 10 ? "0" : "") + remainder;
    }

    private record EffectLine(String key, String name, String time, int color, ResourceLocation icon) {
    }

    private record AnimatedLine(EffectLine line, float progress) {
    }
}
