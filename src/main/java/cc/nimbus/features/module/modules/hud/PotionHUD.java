package cc.nimbus.features.module.modules.hud;

import cc.nimbus.core.Core;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.render.EventRender2D;
import cc.nimbus.features.hud.Draggable;
import cc.nimbus.features.hud.HudSize;
import cc.nimbus.features.hud.HudViewport;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.utils.render.font.NimbusFonts;
import cc.nimbus.utils.render.skija.api.SkijaFrame;
import cc.nimbus.utils.render.skija.api.SkijaRenderer;
import io.github.humbleui.skija.Image;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@ModuleInfo(
        name = "PotionHUD",
        displayName = "Potion HUD",
        description = "Shows active potion effects and their remaining duration.",
        category = ModuleCategory.HUD
)
public final class PotionHUD extends BasicModule {
    private static final String FONT = NimbusFonts.BODY_MEDIUM;
    private static final String TIME_FONT = NimbusFonts.BODY_MEDIUM;
    private static final float MAX_WIDTH = 230f;
    private static final float ROW_HEIGHT = 19f;
    private static final float ICON_SIZE = 18f;
    private static final float CONTENT_PADDING = 5f;
    private static final float TEXT_GAP = 5f;
    private static final float TIME_GAP = 4f;

    private HudSize layoutSize = new HudSize(2f, 2f);

    private final Draggable drag = new Draggable("PotionHUD", 0.98, 0.38) {
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
        if (minecraft.screen != null && !(minecraft.screen instanceof ChatScreen)) return;

        List<MobEffectInstance> effects = new ArrayList<>();
        for (MobEffectInstance effect : minecraft.player.getActiveEffects()) {
            if (effect.isInfiniteDuration() || effect.getDuration() > 0) effects.add(effect);
        }
        effects.sort(Comparator.comparingInt(PotionHUD::displayLength));
        if (effects.isEmpty()) {
            layoutSize = new HudSize(2f, 2f);
            return;
        }

        SkijaRenderer skija = Core.INSTANCE.getRenderManager().skija();
        HudViewport viewport = Core.INSTANCE.getHudManager().viewport(skija.width(), skija.height());
        List<EffectLine> lines = effects.stream()
                .map(effect -> new EffectLine(
                        displayName(effect),
                        duration(effect),
                        0xFF000000 | effect.getEffect().value().getColor(),
                        icon(effect)))
                .toList();
        float layoutWidth = measureWidth(skija, lines);
        float layoutHeight = Math.max(ROW_HEIGHT, lines.size() * ROW_HEIGHT);
        layoutSize = new HudSize(layoutWidth, layoutHeight);
        boolean rightAligned = drag.getAnchor().isRight();
        skija.hud().submit(frame -> drag.render(frame, viewport,
                local -> draw(local, lines, layoutWidth, rightAligned)));
    }

    @Override
    protected void onDisable() {
        layoutSize = new HudSize(2f, 2f);
    }

    private static void draw(SkijaFrame frame, List<EffectLine> lines,
                             float layoutWidth, boolean rightAligned) {
        float availableWidth = layoutWidth - CONTENT_PADDING * 2f;
        float y = 0f;
        for (EffectLine line : lines) {
            String time = "(" + line.time() + ")";
            float timeWidth = frame.text().width(TIME_FONT, 12.5f, time);
            float nameWidth = Math.max(1f,
                    availableWidth - ICON_SIZE - TEXT_GAP - TIME_GAP - timeWidth);
            String visibleName = ellipsize(frame, line.name(), FONT, 13f, nameWidth);
            float rowWidth = ICON_SIZE + TEXT_GAP
                    + frame.text().width(FONT, 13f, visibleName)
                    + TIME_GAP + timeWidth;
            float rowX = rightAligned ? layoutWidth - CONTENT_PADDING - rowWidth : CONTENT_PADDING;
            float iconX = rowX;
            float nameX = iconX + ICON_SIZE + TEXT_GAP;
            Image icon = frame.images().resource(line.icon());
            if (icon != null && !icon.isClosed()) {
                frame.draw().image(icon, iconX, y + (ROW_HEIGHT - ICON_SIZE) * .5f, ICON_SIZE, ICON_SIZE);
            }
            float nameY = y + ROW_HEIGHT * .5f - frame.text().visualCenterY(FONT, 13f, visibleName);
            float timeX = nameX + frame.text().width(FONT, 13f, visibleName) + TIME_GAP;
            float timeY = y + ROW_HEIGHT * .5f - frame.text().visualCenterY(TIME_FONT, 12.5f, time);
            frame.text().drawWithShadow(FONT, 13f, visibleName, nameX, nameY, line.color());
            frame.text().drawWithShadow(TIME_FONT, 12.5f, time, timeX, timeY, 0xFFE1E4E7);
            y += ROW_HEIGHT;
        }
    }

    private static float measureWidth(SkijaRenderer skija, List<EffectLine> lines) {
        float width = 2f;
        for (EffectLine line : lines) {
            String time = "(" + line.time() + ")";
            float rowWidth = CONTENT_PADDING * 2f + ICON_SIZE + TEXT_GAP
                    + skija.text().width(FONT, 13f, line.name())
                    + TIME_GAP + skija.text().width(TIME_FONT, 12.5f, time);
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

    private static Identifier icon(MobEffectInstance effect) {
        Identifier key = BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value());
        if (key == null) key = Identifier.withDefaultNamespace("speed");
        return Identifier.fromNamespaceAndPath(key.getNamespace(),
                "textures/mob_effect/" + key.getPath() + ".png");
    }

    private static String displayName(MobEffectInstance effect) {
        String name = effect.getEffect().value().getDisplayName().getString();
        return effect.getAmplifier() > 0 ? name + " " + (effect.getAmplifier() + 1) : name;
    }

    private static int displayLength(MobEffectInstance effect) {
        return displayName(effect).codePointCount(0, displayName(effect).length());
    }

    private static String duration(MobEffectInstance effect) {
        if (effect.isInfiniteDuration()) return "∞";
        int seconds = Math.max(0, effect.getDuration() / 20);
        int minutes = seconds / 60;
        int remainder = seconds % 60;
        return minutes + ":" + (remainder < 10 ? "0" : "") + remainder;
    }

    private record EffectLine(String name, String time, int color, Identifier icon) {
    }
}
