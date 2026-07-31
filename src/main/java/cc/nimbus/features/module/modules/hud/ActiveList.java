package cc.nimbus.features.module.modules.hud;

import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.types.RRect;
import net.minecraft.client.Minecraft;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.render.EventRender2D;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.BooleanSetting;
import cc.nimbus.features.module.api.setting.impl.ColorSetting;
import cc.nimbus.features.module.api.setting.impl.ModeSetting;
import cc.nimbus.features.module.api.setting.impl.NumberSetting;
import cc.nimbus.features.module.api.setting.impl.modes.Modes;
import cc.nimbus.utils.render.skija.api.SkijaRenderer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import cc.nimbus.core.Core;

@ModuleInfo(name = "ArrayList", displayName = "ArrayList", alias = {"ActiveList"}, description = "Shows enabled modules.", category = ModuleCategory.HUD)
public final class ActiveList extends BasicModule {
    private static final String FONT = "HarmonyOS_Sans_SC-Medium";
    private static final float FONT_SIZE = 16f;
    private static final float ROW_HEIGHT = 27f;
    private static final float PADDING_X = 7f;

    public final ModeSetting position = new ModeSetting("Position",
            new Modes("Top left", "Top right", "Bottom left", "Bottom right"), "Top right");
    public final ModeSetting letterCase = new ModeSetting("Char case",
            new Modes("Normal", "Lowercase", "Uppercase"), "Normal");
    public final NumberSetting margin = new NumberSetting("Margin", 12, 0, 80, 1);
    public final BooleanSetting background = new BooleanSetting("Background", true);
    public final BooleanSetting rounded = new BooleanSetting("Round", true, background::getValue);
    public final BooleanSetting accentLine = new BooleanSetting("Right line", true);
    public final BooleanSetting hideRender = new BooleanSetting("Hide Render", false);
    public final BooleanSetting showSuffix = new BooleanSetting("Show suffix", true);
    public final ColorSetting textColor = new ColorSetting("Text color", 0xFFF1F5F7);
    public final ColorSetting accentColor = new ColorSetting("Accent color", 0xFF52C7B8);

    @EventTarget
    private void onRender2D(EventRender2D event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;

        SkijaRenderer skija = Core.INSTANCE.getRenderManager().skija();
        float screenWidth = skija.width();
        float screenHeight = skija.height();
        if (screenWidth <= 0 || screenHeight <= 0) return;

        List<Entry> entries = new ArrayList<>();
        for (BasicModule module : Core.INSTANCE.getModuleManager().getModules()) {
            if (!module.isEnabled() || module.isHidden() || module == this) continue;
            if (hideRender.getValue()
                    && (module.getCategory() == ModuleCategory.Render || module.getCategory() == ModuleCategory.HUD)) {
                continue;
            }
            String label = format(module.getDisplayName());
            String suffix = showSuffix.getValue() && !module.getSuffix().isBlank()
                    ? " " + module.getSuffix() : "";
            float width = skija.text().width(FONT, FONT_SIZE, label + suffix) + PADDING_X * 2f;
            entries.add(new Entry(label, suffix, width));
        }
        entries.sort(Comparator.comparingDouble(Entry::width).reversed());
        if (entries.isEmpty()) return;

        boolean right = position.getValue().endsWith("right");
        boolean down = position.getValue().startsWith("Top");
        float edge = margin.getValue().floatValue();
        float startY = down ? edge : screenHeight - edge - ROW_HEIGHT;
        List<Row> rows = new ArrayList<>(entries.size());
        for (int index = 0; index < entries.size(); index++) {
            Entry entry = entries.get(index);
            float x = right ? screenWidth - edge - entry.width() : edge;
            float y = down ? startY + index * ROW_HEIGHT : startY - index * ROW_HEIGHT;
            float radius = rounded.getValue() ? 5f : 0f;
            rows.add(new Row(entry, x, y, radius));
            if (background.getValue()) skija.postProcess().add(RRect.makeXYWH(x, y, entry.width(), ROW_HEIGHT, radius));
        }

        skija.hud().submit(frame -> {
            Canvas canvas = frame.canvas();
            try (Paint backgroundPaint = new Paint().setAntiAlias(true).setColor(0xA8171A1E);
                 Paint textPaint = new Paint().setAntiAlias(true).setColor(textColor.getValue());
                 Paint suffixPaint = new Paint().setAntiAlias(true).setColor(0xFF9AA4AE);
                 Paint linePaint = new Paint().setAntiAlias(true).setColor(accentColor.getValue())) {
                for (Row row : rows) {
                    if (background.getValue()) {
                        canvas.drawRRect(RRect.makeXYWH(row.x(), row.y(), row.entry().width(), ROW_HEIGHT, row.radius()), backgroundPaint);
                    }
                    if (accentLine.getValue()) {
                        float lineX = right ? row.x() + row.entry().width() - 2f : row.x();
                        canvas.drawRect(io.github.humbleui.types.Rect.makeXYWH(lineX, row.y(), 2f, ROW_HEIGHT), linePaint);
                    }
                    float textX = row.x() + PADDING_X;
                    float textY = row.y() + (ROW_HEIGHT - frame.text().height(FONT, FONT_SIZE)) * 0.5f;
                    frame.text().drawWithShadow(FONT, FONT_SIZE, row.entry().name(), textX, textY, textPaint);
                    if (!row.entry().suffix().isEmpty()) {
                        float nameWidth = frame.text().width(FONT, FONT_SIZE, row.entry().name());
                        frame.text().drawWithShadow(FONT, FONT_SIZE, row.entry().suffix(),
                                textX + nameWidth, textY, suffixPaint);
                    }
                }
            }
        });
    }

    private String format(String value) {
        return switch (letterCase.getValue()) {
            case "Lowercase" -> value.toLowerCase(Locale.ROOT);
            case "Uppercase" -> value.toUpperCase(Locale.ROOT);
            default -> value;
        };
    }

    private record Entry(String name, String suffix, float width) {}
    private record Row(Entry entry, float x, float y, float radius) {}
}
