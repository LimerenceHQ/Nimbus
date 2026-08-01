package cc.nimbus.features.module.modules.hud;

import cc.nimbus.core.Core;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.render.EventRender2D;
import cc.nimbus.features.hud.Draggable;
import cc.nimbus.features.hud.HudAnchor;
import cc.nimbus.features.hud.HudRect;
import cc.nimbus.features.hud.HudSize;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.BooleanSetting;
import cc.nimbus.features.module.api.setting.impl.ColorSetting;
import cc.nimbus.utils.render.skija.api.SkijaRenderer;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Path;
import io.github.humbleui.types.Rect;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@ModuleInfo(
        name = "ArrayList",
        displayName = "Active List",
        alias = {"ActiveList"},
        description = "Displays enabled modules.",
        category = ModuleCategory.HUD,
        enableOnStartUp = true
)
public final class ActiveList extends BasicModule {
    private static final String FONT = "SarasaUiSC-SemiBold";
    private static final float FONT_SIZE = 15f;
    private static final float ROW_HEIGHT = 25f;
    private static final float PADDING_X = 8f;
    private static final float ACCENT_WIDTH = 2f;
    private static final float ROW_OVERLAP = 0.75f;
    private static final int BACKGROUND_COLOR = 0x5C171A1E;
    private static final int TEXT_COLOR = 0xFFF4F7F8;
    private static final int SUFFIX_COLOR = 0xFF9EA8B2;

    public final BooleanSetting showSuffix = new BooleanSetting("Show suffix", true);
    public final BooleanSetting background = new BooleanSetting("Background", true);
    public final ColorSetting accent = new ColorSetting("Accent", 0xFF82A9FF);
    private HudSize layoutSize = new HudSize(2f, 2f);

    private final Draggable drag = new Draggable("ActiveList", 0.98, 0.02) {
        @Override
        public HudSize getSize() {
            return layoutSize;
        }
    };

    @EventTarget
    private void onRender2D(EventRender2D event) {
        List<Entry> entries = entries();
        float totalWidth = maxWidth(entries);
        float totalHeight = Math.max(2f, entries.size() * ROW_HEIGHT);
        layoutSize = new HudSize(totalWidth, totalHeight);
        if (entries.isEmpty()) return;

        SkijaRenderer skija = Core.INSTANCE.getRenderManager().skija();
        HudAnchor anchor = drag.getAnchor();
        HudRect bounds = drag.getRect(skija.width(), skija.height());
        float scale = (float) drag.getScale();
        boolean drawBackground = background.getValue();
        int accentColor = accent.getValue();

        if (drawBackground) {
            try (Path regions = backgroundPath(
                    entries, anchor, totalWidth, totalHeight, bounds.x(), bounds.y(), scale)) {
                skija.postProcess().add(regions);
            }
        }

        List<Entry> frameEntries = List.copyOf(entries);
        skija.hud().submit(frame -> drag.render(frame, local -> {
            try (Path backgroundPath = backgroundPath(
                    frameEntries, anchor, totalWidth, totalHeight, 0f, 0f, 1f);
                 Paint backgroundPaint = new Paint().setAntiAlias(true).setColor(BACKGROUND_COLOR);
                 Paint textPaint = new Paint().setAntiAlias(true).setColor(TEXT_COLOR);
                 Paint suffixPaint = new Paint().setAntiAlias(true).setColor(SUFFIX_COLOR)) {
                if (drawBackground) local.canvas().drawPath(backgroundPath, backgroundPaint);
                float accentX = anchor.isRight() ? totalWidth - ACCENT_WIDTH : 0f;
                local.draw().rect(accentX, 0f, ACCENT_WIDTH, totalHeight, accentColor);
                float textY = (ROW_HEIGHT - local.text().height(FONT, FONT_SIZE)) * 0.5f;
                for (int index = 0; index < frameEntries.size(); index++) {
                    Entry entry = frameEntries.get(index);
                    float rowX = rowX(anchor, totalWidth, entry.width());
                    float rowY = rowY(anchor, totalHeight, index);
                    float labelX = rowX + PADDING_X + (anchor.isRight() ? 0f : ACCENT_WIDTH);
                    local.text().draw(FONT, FONT_SIZE, entry.name(), labelX, rowY + textY, textPaint);
                    if (!entry.suffix().isEmpty()) {
                        local.text().draw(FONT, FONT_SIZE, entry.suffix(),
                                labelX + entry.nameWidth(), rowY + textY, suffixPaint);
                    }
                }
            }
        }));
    }

    private List<Entry> entries() {
        SkijaRenderer skija = Core.INSTANCE.getRenderManager().skija();
        List<Entry> entries = new ArrayList<>();
        for (BasicModule module : Core.INSTANCE.getModuleManager().getModules()) {
            if (!module.isEnabled() || module.isHidden() || module == this) continue;
            String name = module.getDisplayName();
            String suffix = showSuffix.getValue() && !module.getSuffix().isBlank()
                    ? " " + module.getSuffix() : "";
            float nameWidth = skija.text().width(FONT, FONT_SIZE, name);
            float textWidth = nameWidth + skija.text().width(FONT, FONT_SIZE, suffix);
            entries.add(new Entry(name, suffix, nameWidth,
                    textWidth + PADDING_X * 2f + ACCENT_WIDTH));
        }
        entries.sort(Comparator.comparingDouble(Entry::width).reversed()
                .thenComparing(Entry::name, String.CASE_INSENSITIVE_ORDER));
        return entries;
    }

    private static float maxWidth(List<Entry> entries) {
        return Math.max(2f, entries.stream().map(Entry::width).max(Float::compare).orElse(2f));
    }

    private static float rowX(HudAnchor anchor, float totalWidth, float rowWidth) {
        return anchor.isRight() ? totalWidth - rowWidth : 0f;
    }

    private static float rowY(HudAnchor anchor, float totalHeight, int index) {
        return anchor.isBottom()
                ? totalHeight - (index + 1) * ROW_HEIGHT
                : index * ROW_HEIGHT;
    }

    private static Path backgroundPath(
            List<Entry> entries,
            HudAnchor anchor,
            float totalWidth,
            float totalHeight,
            float originX,
            float originY,
            float scale
    ) {
        Path path = new Path();
        for (int index = 0; index < entries.size(); index++) {
            Entry entry = entries.get(index);
            float rowX = rowX(anchor, totalWidth, entry.width());
            float rowY = rowY(anchor, totalHeight, index);
            float rowHeight = Math.min(ROW_HEIGHT + ROW_OVERLAP, totalHeight - rowY);
            path.addRect(Rect.makeXYWH(
                    originX + rowX * scale,
                    originY + rowY * scale,
                    entry.width() * scale,
                    rowHeight * scale
            ));
        }
        return path;
    }

    private record Entry(String name, String suffix, float nameWidth, float width) {}
}
