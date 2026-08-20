package cc.nimbus.features.module.modules.hud;

import cc.nimbus.core.Core;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.render.EventRender2D;
import cc.nimbus.features.client.ClientSettings;
import cc.nimbus.features.hud.Draggable;
import cc.nimbus.features.hud.HudRect;
import cc.nimbus.features.hud.HudSize;
import cc.nimbus.features.hud.HudViewport;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.ColorSetting;
import cc.nimbus.features.module.api.setting.impl.submodule.ModeValue;
import cc.nimbus.features.module.api.setting.impl.TextSetting;
import cc.nimbus.utils.render.Render2DUtils;
import cc.nimbus.utils.render.skija.api.SkijaRenderer;
import cc.nimbus.utils.render.font.NimbusFonts;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.types.Point;
import io.github.humbleui.types.RRect;

@ModuleInfo(
        name = "Watermark",
        description = "Displays a draggable Nimbus watermark.",
        category = ModuleCategory.HUD,
        enableOnStartUp = true
)
public final class WaterMark extends BasicModule {
    private static final String FONT = NimbusFonts.DISPLAY;
    private static final float FONT_SIZE = 16f;
    private static final float HEIGHT = 34f;
    private static final float LOGO_SIZE = 44f;
    private static final float COMPACT_LOGO_SIZE = 24f;
    private static final float LOGO_GAP = 5f;
    private static final float PADDING_X = 12f;
    private static final float ACCENT_WIDTH = 3f;
    private static final float RADIUS = 2f;
    private static final int BACKGROUND_COLOR = 0x66171A1E;
    private static final int TEXT_COLOR = 0xFFF4F7F8;

    public final ModeValue mode = new ModeValue("Mode", new String[]{"Logo", "Logo + Text"}, "Logo + Text");
    public final TextSetting text = new TextSetting("Text", "Nimbus", null, false, false, null, 32, () -> Core.INSTANCE.isInitialized() && Core.INSTANCE.getConfigManager().clientSettings().customStyle() && ! mode.is("Logo"));
    public final ColorSetting accent = new ColorSetting("Accent", 0xFF52C7B8, () -> Core.INSTANCE.isInitialized() && Core.INSTANCE.getConfigManager().clientSettings().customStyle());
    private final Draggable drag = new Draggable("Watermark", 0.02, 0.02) {
        @Override
        public HudSize getSize() {
            if (mode.is("Logo")) return new HudSize(LOGO_SIZE, LOGO_SIZE);
            SkijaRenderer skija = Core.INSTANCE.getRenderManager().skija();
            return new HudSize(
                    COMPACT_LOGO_SIZE + LOGO_GAP
                            + skija.text().width(FONT, FONT_SIZE, displayText())
                            + PADDING_X * 2f + ACCENT_WIDTH, HEIGHT
            );
        }
    };

    @EventTarget
    private void onRender2D(EventRender2D event) {
        SkijaRenderer skija = Core.INSTANCE.getRenderManager().skija();
        HudViewport viewport = Core.INSTANCE.getHudManager().viewport(skija.width(), skija.height());
        HudSize size = drag.getSize();
        HudRect bounds = drag.getRect(viewport);
        float scale = drag.getRenderScale(viewport);
        ClientSettings clientSettings = Core.INSTANCE.getConfigManager().clientSettings();
        long colorTime = System.currentTimeMillis();
        boolean customStyle = Core.INSTANCE.isInitialized() && clientSettings.customStyle();
        int[] accentColors = customStyle
                ? new int[]{accent.getValue(), accent.getValue()}
                : new int[]{clientSettings.accentColor(0, colorTime), clientSettings.accentColor(1, colorTime)};
        int[] logoColors = customStyle
                ? new int[]{accent.getValue(), accent.getValue()}
                : new int[]{clientSettings.accentColor(0, colorTime), clientSettings.accentColor(-1, colorTime)};

        if (!mode.is("Logo")) {
            skija.postProcess().add(RRect.makeXYWH(
                    bounds.x(), bounds.y(), bounds.width(), bounds.height(), RADIUS * scale
            ));
        }

        skija.hud().submit(frame -> drag.render(frame, viewport, local -> {
            if (mode.is("Logo")) {
                Render2DUtils.drawLogo(local, 0f, 0f, LOGO_SIZE,
                        bounds.x(), bounds.y(), scale, logoColors);
                return;
            }

            local.draw().roundedRect(0f, 0f, size.width(), size.height(), RADIUS, BACKGROUND_COLOR);
            try (Paint accentPaint = new Paint().setAntiAlias(true)) {
                accentPaint.setShader(local.effects().linearGradient(
                        new Point(0f, 0f), new Point(0f, size.height()), accentColors));
                local.draw().rect(0f, 0f, ACCENT_WIDTH, size.height(), accentPaint);
            }
            float logoX = PADDING_X + ACCENT_WIDTH;
            float logoY = (size.height() - COMPACT_LOGO_SIZE) * .5f;
            Render2DUtils.drawLogo(local, logoX, logoY, COMPACT_LOGO_SIZE,
                    bounds.x(), bounds.y(), scale, logoColors);

            float textX = logoX + COMPACT_LOGO_SIZE + LOGO_GAP;
            float textY = (size.height() - local.text().height(FONT, FONT_SIZE)) * 0.5f;
            local.text().draw(FONT, FONT_SIZE, displayText(), textX, textY, TEXT_COLOR);
        }));
    }

    private String displayText() {
        if (!Core.INSTANCE.isInitialized()) {return "Nimbus";}
        ClientSettings settings = Core.INSTANCE.getConfigManager().clientSettings();
        if (settings.customStyle()) {
            String value = text.getValue();
            if (value != null && !value.isBlank()) {return value.strip();}
        }
        return Core.INSTANCE.getConfigManager().clientSettings().displayName();
    }
}
