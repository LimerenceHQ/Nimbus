package cc.nimbus.utils.render;

import cc.nimbus.utils.render.skija.api.SkijaFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class Render2DUtils {

    private Render2DUtils() {
    }

    public static void drawEspFrame(SkijaFrame frame, Render3DUtils.ProjectedBox box,
                                    boolean drawBox, String mode, boolean fill, float lineWidth,
                                    int color, boolean drawArmor, float armorRatio,
                                    boolean drawHealth, float healthRatio) {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(box, "box");
        float left = screen(box.left(), frame.width());
        float top = screen(box.top(), frame.height());
        float right = screen(box.right(), frame.width());
        float bottom = screen(box.bottom(), frame.height());
        float width = right - left;
        float height = bottom - top;
        if (width < 2f || height < 2f) return;

        if (drawBox) {
            if (fill) frame.draw().rect(left, top, width, height, withAlpha(color, 0x18));
            float horizontal = width / ("Corner B".equals(mode) ? 3f : 4f);
            float vertical = "Corner B".equals(mode) ? width / 4f : height / 4f;
            switch (mode) {
                case "Corner A", "Corner B" -> {
                    espLine(frame, left, top, left + horizontal, top, lineWidth, color);
                    espLine(frame, right - horizontal, top, right, top, lineWidth, color);
                    espLine(frame, left, bottom, left + horizontal, bottom, lineWidth, color);
                    espLine(frame, right - horizontal, bottom, right, bottom, lineWidth, color);
                    espLine(frame, left, top, left, top + vertical, lineWidth, color);
                    espLine(frame, right, top, right, top + vertical, lineWidth, color);
                    espLine(frame, left, bottom - vertical, left, bottom, lineWidth, color);
                    espLine(frame, right, bottom - vertical, right, bottom, lineWidth, color);
                }
                case "Split" -> {
                    espLine(frame, left, top, left, bottom, lineWidth, color);
                    espLine(frame, right, top, right, bottom, lineWidth, color);
                    espLine(frame, left, top, left + horizontal, top, lineWidth, color);
                    espLine(frame, right - horizontal, top, right, top, lineWidth, color);
                    espLine(frame, left, bottom, left + horizontal, bottom, lineWidth, color);
                    espLine(frame, right - horizontal, bottom, right, bottom, lineWidth, color);
                }
                default -> {
                    espLine(frame, left, top, right, top, lineWidth, color);
                    espLine(frame, right, top, right, bottom, lineWidth, color);
                    espLine(frame, right, bottom, left, bottom, lineWidth, color);
                    espLine(frame, left, bottom, left, top, lineWidth, color);
                }
            }
        }

        float barHeight = Math.max(1f, height - 2f);
        if (drawArmor) drawVerticalBar(frame, left - 5f, top + 1f, barHeight, armorRatio, color);
        if (drawHealth) {
            drawVerticalBar(frame, right + 3f, top + 1f, barHeight, healthRatio,
                    healthColor(healthRatio));
        }
    }

    public static void drawEspDetails(GuiGraphicsExtractor graphics, Player player,
                                      Render3DUtils.ProjectedBox box,
                                      boolean showEquipment, boolean showEffects) {
        Objects.requireNonNull(graphics, "graphics");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(box, "box");
        List<ItemStack> equipment = new ArrayList<>(6);
        if (showEquipment) {
            for (EquipmentSlot slot : List.of(
                    EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                    EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND)) {
                ItemStack stack = player.getItemBySlot(slot);
                if (!stack.isEmpty()) equipment.add(stack);
            }
        }
        List<MobEffectInstance> effects = showEffects ? player.getActiveEffects().stream()
                .filter(MobEffectInstance::isVisible).limit(6).toList() : List.of();
        if (equipment.isEmpty() && effects.isEmpty()) return;

        float center = screen((box.left() + box.right()) * 0.5f, graphics.guiWidth());
        float y = screen(box.bottom(), graphics.guiHeight());

        if (!equipment.isEmpty()) {
            int guiScale = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
            float size = 8f;
            float scale = size / 16f;
            float startX = Math.round((center - equipment.size() * size * 0.5f) * guiScale)
                    / (float) guiScale;
            float itemY = Math.round((y - scale) * guiScale) / (float) guiScale;
            var pose = graphics.pose();
            pose.pushMatrix();
            try {
                pose.translate(startX, itemY);
                pose.scale(scale, scale);
                for (int index = 0; index < equipment.size(); index++) {
                    graphics.item(equipment.get(index), index * 16, 0, player.getId() + index);
                }
            } finally {
                pose.popMatrix();
            }
            y += size;
        }

        if (!effects.isEmpty()) {
            Minecraft minecraft = Minecraft.getInstance();
            List<String> labels = new ArrayList<>(effects.size());
            y += 1f;
            for (MobEffectInstance effect : effects) {
                String duration;
                if (effect.isInfiniteDuration()) {
                    duration = "\u221E";
                } else {
                    int seconds = Math.max(0, effect.getDuration() / 20);
                    int remainder = seconds % 60;
                    duration = seconds / 60 + ":" + (remainder < 10 ? "0" : "") + remainder;
                }
                String level = effect.getAmplifier() > 0 ? " " + (effect.getAmplifier() + 1) : "";
                String text = effect.getEffect().value().getDisplayName().getString()
                        + level + " " + duration;
                labels.add(text);
            }
            float textScale = 0.45f;
            for (int index = 0; index < effects.size(); index++) {
                MobEffectInstance effect = effects.get(index);
                String text = labels.get(index);
                int color = 0xFF000000 | effect.getEffect().value().getColor();
                var pose = graphics.pose();
                pose.pushMatrix();
                try {
                    pose.translate(center, y);
                    pose.scale(textScale, textScale);
                    graphics.text(minecraft.font, text, -minecraft.font.width(text) / 2, 0, color, true);
                } finally {
                    pose.popMatrix();
                }
                y += (minecraft.font.lineHeight + 1f) * textScale;
            }
        }
    }

    public static void drawLogo(SkijaFrame frame,
                                          float x, float y, float size,
                                          float originX, float originY,
                                          float scale, int color) {
        Objects.requireNonNull(frame, "frame");
        if (!Float.isFinite(size) || size <= 0f) {
            throw new IllegalArgumentException("size must be finite and positive");
        }
        if (!Float.isFinite(scale) || scale <= 0f) {
            throw new IllegalArgumentException("scale must be finite and positive");
        }

        float unit = size / 24f;
        x -= .5f * unit;
        logoRect(frame, originX, originY, scale, x + 9f * unit, y + 3f * unit, 7f * unit, 3f * unit, color);
        logoRect(frame, originX, originY, scale, x + 6f * unit, y + 6f * unit, 13f * unit, 3f * unit, color);
        logoRect(frame, originX, originY, scale, x + 3f * unit, y + 9f * unit, 19f * unit, 7f * unit, color);
        float rainX = x - unit;
        logoRain(frame, originX, originY, scale, rainX + 7f * unit, y + 18f * unit, unit, color);
        logoRain(frame, originX, originY, scale, rainX + 12f * unit, y + 18f * unit, unit, color);
        logoRain(frame, originX, originY, scale, rainX + 17f * unit, y + 18f * unit, unit, color);
    }

    private static void logoRain(SkijaFrame frame,
                                 float originX, float originY, float scale,
                                 float x, float y, float unit, int color) {
        logoRect(frame, originX, originY, scale, x, y, 3f * unit, 2f * unit, color);
        logoRect(frame, originX, originY, scale, x - unit, y + 2f * unit, 3f * unit, 2f * unit, color);
        logoRect(frame, originX, originY, scale, x - 2f * unit, y + 4f * unit, 3f * unit, unit, color);
    }

    private static void logoRect(SkijaFrame frame,
                                  float originX, float originY, float scale,
                                  float x, float y, float width, float height, int color) {
        float left = snapLocal(x, originX, scale);
        float top = snapLocal(y, originY, scale);
        float right = snapLocal(x + width, originX, scale);
        float bottom = snapLocal(y + height, originY, scale);
        frame.draw().rect(left, top, right - left, bottom - top, color);
    }

    private static float snapLocal(float value, float origin, float scale) {
        return (Math.round(origin + value * scale) - origin) / scale;
    }

    private static void espLine(SkijaFrame frame, float x1, float y1, float x2, float y2,
                                float width, int color) {
        frame.draw().line(x1, y1, x2, y2, width + 1.8f, 0xA0000000);
        frame.draw().line(x1, y1, x2, y2, width, color);
    }

    private static void drawVerticalBar(SkijaFrame frame, float x, float y, float height,
                                        float ratio, int color) {
        float value = Math.clamp(ratio, 0f, 1f);
        frame.draw().roundedRect(x, y, 2f, height, 1f, 0xA0000000);
        float filled = height * value;
        if (filled > 0f) {
            frame.draw().roundedRect(x, y + height - filled, 2f, filled, 1f, color);
        }
    }

    private static int healthColor(float ratio) {
        float value = Math.clamp(ratio, 0f, 1f);
        int red = Math.round(255f * (1f - value));
        int green = Math.round(220f * value);
        return 0xFF000000 | red << 16 | green << 8 | 0x48;
    }

    private static float screen(float normalized, float size) {
        return (normalized + 1f) * 0.5f * size;
    }

    private static int withAlpha(int color, int alpha) {
        return color & 0x00FFFFFF | Math.clamp(alpha, 0, 255) << 24;
    }
}
