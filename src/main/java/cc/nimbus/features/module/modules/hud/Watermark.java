package cc.nimbus.features.module.modules.hud;

import cc.nimbus.core.Core;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.render.EventRender2D;
import cc.nimbus.features.hud.Draggable;
import cc.nimbus.features.hud.HudRect;
import cc.nimbus.features.hud.HudSize;
import cc.nimbus.features.hud.HudViewport;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.ColorSetting;
import cc.nimbus.features.module.api.setting.impl.TextSetting;
import cc.nimbus.utils.render.skija.api.SkijaRenderer;
import cc.nimbus.utils.render.font.NimbusFonts;
import io.github.humbleui.types.RRect;

@ModuleInfo(
        name = "Watermark",
        description = "Displays a draggable Nimbus watermark.",
        category = ModuleCategory.HUD,
        enableOnStartUp = true
)
public final class Watermark extends BasicModule {
    private static final String FONT = NimbusFonts.DISPLAY;
    private static final float FONT_SIZE = 16f;
    private static final float HEIGHT = 34f;
    private static final float PADDING_X = 12f;
    private static final float ACCENT_WIDTH = 3f;
    private static final float RADIUS = 2f;
    private static final int BACKGROUND_COLOR = 0x66171A1E;
    private static final int TEXT_COLOR = 0xFFF4F7F8;

    public final TextSetting text = new TextSetting(
            "Text", "Nimbus", null, false, false, null, 32, this::customStyle
    );
    public final ColorSetting accent = new ColorSetting("Accent", 0xFF52C7B8, this::customStyle);

    private final Draggable drag = new Draggable("Watermark", 0.02, 0.02) {
        @Override
        public HudSize getSize() {
            SkijaRenderer skija = Core.INSTANCE.getRenderManager().skija();
            return new HudSize(
                    skija.text().width(FONT, FONT_SIZE, displayText()) + PADDING_X * 2f + ACCENT_WIDTH,
                    HEIGHT
            );
        }
    };

    @EventTarget
    private void onRender2D(EventRender2D event) {
        SkijaRenderer skija = Core.INSTANCE.getRenderManager().skija();
        HudViewport viewport = Core.INSTANCE.getHudManager().viewport(skija.width(), skija.height());
        String label = displayText();
        HudSize size = drag.getSize();
        HudRect bounds = drag.getRect(viewport);
        float scale = drag.getRenderScale(viewport);
        int accentColor = customStyle() ? accent.getValue() : Core.INSTANCE.getConfigManager().clientSettings().accentColor();

        skija.postProcess().add(RRect.makeXYWH(bounds.x(), bounds.y(), bounds.width(), bounds.height(), RADIUS * scale));

        skija.hud().submit(frame -> drag.render(frame, viewport, local -> {
            local.draw().roundedRect(0f, 0f, size.width(), size.height(), RADIUS, BACKGROUND_COLOR);
            local.draw().rect(0f, 0f, ACCENT_WIDTH, size.height(), accentColor);
            float textY = (size.height() - local.text().height(FONT, FONT_SIZE)) * 0.5f;
            local.text().draw(FONT, FONT_SIZE, label, PADDING_X + ACCENT_WIDTH, textY, TEXT_COLOR);
        }));
    }

    private String displayText() {
        if (!customStyle()) {
            return Core.INSTANCE.getConfigManager().clientSettings().displayName();
        }
        String value = text.getValue();
        return value == null || value.isBlank() ? "Nimbus" : value.strip();
    }

    private boolean customStyle() {
        return Core.INSTANCE.isInitialized()
                && Core.INSTANCE.getConfigManager().clientSettings().customStyle();
    }
}
