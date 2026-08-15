package cc.nimbus.ui.clickgui.tool;

import cc.nimbus.core.Core;
import cc.nimbus.features.client.ClientSettings;
import cc.nimbus.features.client.ThemePreset;
import cc.nimbus.features.module.api.setting.Setting;
import cc.nimbus.ui.clickgui.component.SettingControls;
import cc.nimbus.ui.clickgui.component.UiContext;
import cc.nimbus.ui.clickgui.component.UiRect;
import cc.nimbus.ui.clickgui.theme.ClickGuiTheme;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.types.Point;
import io.github.humbleui.types.Rect;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.PreeditEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ClientSettingsTool implements ToolPage {
    private static final float PRESET_AREA_HEIGHT = 100f;
    private static final float PRESET_GAP = 8f;
    private static final float PRESET_INSET = 1f;
    private static final float PRESET_CONTENT_PADDING = 8f;
    private static final float PRESET_CARD_HEIGHT = 30f;
    private static final int PRESET_COLUMNS = 4;

    private final SettingControls controls;
    private final List<TabHit> tabs = new ArrayList<>();
    private final List<PresetHit> presetHits = new ArrayList<>();
    private final Map<String, Float> presetSelectionAnimations = new HashMap<>();
    private final Map<String, Float> presetHoverAnimations = new HashMap<>();
    private Tab selected = Tab.GENERAL;
    private UiRect contentBounds;
    private float scroll;
    private float targetScroll;
    private float maximumScroll;
    private float indicatorX = Float.NaN;
    private float indicatorWidth;
    private float tabTransition = 1f;
    private float presetVisibility = 1f;
    private UiRect presetBounds;

    public ClientSettingsTool(SettingControls controls) {
        this.controls = controls;
    }

    @Override
    public void render(UiContext context, UiRect bounds) {
        tabs.clear();
        presetHits.clear();
        ToolHeader.render(context, bounds,
                context.translate("nimbus.client_settings.title", "Client Settings"),
                context.translate("nimbus.client_settings.description",
                        "Global identity and rendering preferences"));
        float tabY = bounds.y() + 67f;
        float tabWidth = (bounds.width() - 44f - 18f) / 4f;
        float selectedX = 0f;
        for (int index = 0; index < Tab.values().length; index++) {
            Tab tab = Tab.values()[index];
            UiRect tabBounds = new UiRect(bounds.x() + 22f + index * (tabWidth + 6f), tabY, tabWidth, 31f);
            boolean hover = tabBounds.contains(context.mouseX(), context.mouseY());
            context.centered(context.translate(tab.translationKey, tab.label), tabBounds, 11f,
                    tab == selected ? ClickGuiTheme.TEXT : ClickGuiTheme.MUTED);
            if (hover && tab != selected) {
                context.frame().draw().roundedRect(tabBounds.x() + tabBounds.width() * .3f,
                        tabBounds.y() + tabBounds.height() - 2f, tabBounds.width() * .4f, 2f, 1f,
                        ClickGuiTheme.HOVER);
            }
            if (tab == selected) selectedX = tabBounds.x();
            tabs.add(new TabHit(tabBounds, tab));
        }

        float speed = 1f - (float) Math.exp(-context.deltaSeconds() * 14f);
        if (!Float.isFinite(indicatorX)) {
            indicatorX = selectedX;
            indicatorWidth = tabWidth;
        }
        indicatorX = animate(indicatorX, selectedX, speed);
        indicatorWidth = animate(indicatorWidth, tabWidth, speed);
        context.frame().draw().roundedRect(indicatorX + indicatorWidth * .27f, tabY + 29f,
                indicatorWidth * .46f, 2.5f, 1.25f,
                context.theme().motionAccent(selected.ordinal() * 18, 0xD8));
        context.frame().draw().rect(bounds.x() + 22f, tabY + 34f, bounds.width() - 44f, 1f,
                0x12FFFFFF);

        contentBounds = new UiRect(bounds.x() + 22f, tabY + 43f, bounds.width() - 44f,
                bounds.height() - (tabY - bounds.y()) - 65f);
        List<Setting<?>> settings = settings(selected);
        ClientSettings clientSettings = Core.INSTANCE.getConfigManager().clientSettings();
        presetVisibility = animate(presetVisibility, clientSettings.usesThemePreset() ? 1f : 0f, speed);
        float presetHeight = selected == Tab.GENERAL ? PRESET_AREA_HEIGHT * presetVisibility : 0f;
        float contentHeight = presetHeight + controls.height(settings, contentBounds.width());
        maximumScroll = Math.max(0f, contentHeight - contentBounds.height());
        targetScroll = Math.clamp(targetScroll, -maximumScroll, 0f);
        scroll = animate(scroll, targetScroll, speed);
        tabTransition = animate(tabTransition, 1f, speed);
        int save = context.clip(contentBounds, 3f);
        try {
            int layer = context.frame().canvas().saveLayerAlpha(
                    Rect.makeXYWH(contentBounds.x(), contentBounds.y(), contentBounds.width(), contentBounds.height()),
                    Math.round((.55f + .45f * tabTransition) * 255f));
            try {
                float contentX = contentBounds.x() + (1f - tabTransition) * 10f;
                float contentY = contentBounds.y() + scroll;
                presetBounds = null;
                if (selected == Tab.GENERAL && presetHeight > .1f) {
                    presetBounds = new UiRect(contentX, contentY, contentBounds.width(), presetHeight);
                    int presetClip = context.clip(presetBounds, 0f);
                    int presetLayer = context.frame().canvas().saveLayerAlpha(
                            Rect.makeXYWH(contentX, contentY, contentBounds.width(), presetHeight),
                            Math.round(255f * presetVisibility));
                    try {
                        renderThemePresets(context, contentX, contentY, contentBounds.width());
                    } finally {
                        context.frame().canvas().restoreToCount(presetLayer);
                        context.restore(presetClip);
                    }
                }
                contentY += presetHeight;
                controls.render(context, settings, contentX, contentY, contentBounds.width(), contentBounds);
            } finally {
                context.frame().canvas().restoreToCount(layer);
            }
        } finally {
            context.restore(save);
        }
    }

    @Override
    public boolean mouseClicked(float x, float y, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            for (TabHit tab : tabs) {
                if (!tab.bounds().contains(x, y)) continue;
                if (selected != tab.tab()) {
                    selected = tab.tab();
                    scroll = 0f;
                    targetScroll = 0f;
                    tabTransition = 0f;
                    controls.clearTransient();
                }
                return true;
            }
            for (PresetHit preset : presetHits) {
                if (!preset.bounds().contains(x, y)
                        || contentBounds == null || !contentBounds.contains(x, y)
                        || presetBounds == null || !presetBounds.contains(x, y)) continue;
                Core.INSTANCE.getConfigManager().clientSettings().themePreset.setValue(preset.name());
                controls.clearTransient();
                return true;
            }
        }
        return contentBounds != null && contentBounds.contains(x, y) && controls.mouseClicked(x, y, button);
    }

    @Override public void mouseMoved(float x, float y, boolean leftDown) { controls.mouseMoved(x, y, leftDown); }
    @Override public void mouseReleased() { controls.mouseReleased(); }
    @Override public boolean keyPressed(KeyEvent event) { return controls.keyPressed(event); }
    @Override public boolean charTyped(CharacterEvent event) { return controls.charTyped(event); }
    @Override public boolean preeditUpdated(PreeditEvent event) { return controls.preeditUpdated(event); }
    @Override public boolean cancelTransient() { return controls.cancelTransient(); }

    @Override
    public boolean mouseScrolled(float x, float y, double delta) {
        if (contentBounds == null || !contentBounds.contains(x, y)) return false;
        targetScroll = Math.clamp(targetScroll + (float) delta * 28f, -maximumScroll, 0f);
        return true;
    }

    @Override public void closed() { controls.clearTransient(); }

    private static List<Setting<?>> settings(Tab tab) {
        ClientSettings value = Core.INSTANCE.getConfigManager().clientSettings();
        return switch (tab) {
            case GENERAL -> List.of(value.clientName, value.language, value.style, value.colorMode,
                    value.accent, value.secondaryAccent, value.rainbowSaturation, value.rainbowBrightness,
                    value.astolfoIndex, value.astolfoOffset, value.hudScale,
                    value.hudRefresh, value.hudRefreshLimit);
            case SURFACE -> List.of(value.postProcessing, value.splitRegions, value.texture, value.textureOpacity);
            case BLUR -> List.of(value.blurMode, value.blurRadius, value.blurPasses, value.downSample,
                    value.upSample, value.blurRefresh, value.blurRefreshLimit);
            case SHADOW -> List.of(value.shadow, value.shadowRadius, value.shadowOpacity,
                    value.shadowRefresh, value.shadowRefreshLimit);
        };
    }

    private enum Tab {
        GENERAL("General", "nimbus.client_settings.tab.general"),
        SURFACE("Surface", "nimbus.client_settings.tab.surface"),
        BLUR("Blur", "nimbus.client_settings.tab.blur"),
        SHADOW("Shadow", "nimbus.client_settings.tab.shadow");
        private final String label;
        private final String translationKey;
        Tab(String label, String translationKey) {
            this.label = label;
            this.translationKey = translationKey;
        }
    }
    private record TabHit(UiRect bounds, Tab tab) { }
    private record PresetHit(UiRect bounds, String name) { }

    private void renderThemePresets(UiContext context, float x, float y, float width) {
        ClientSettings settings = Core.INSTANCE.getConfigManager().clientSettings();
        context.textIn(context.translate(settings.themePreset.getTranslationKey(), "Theme Preset"),
                new UiRect(x + PRESET_INSET, y, width - PRESET_INSET * 2f, 22f),
                11f, ClickGuiTheme.TEXT);

        float gridWidth = width - PRESET_INSET * 2f;
        float cardWidth = (gridWidth - PRESET_GAP * (PRESET_COLUMNS - 1)) / PRESET_COLUMNS;
        float speed = 1f - (float) Math.exp(-context.deltaSeconds() * 11f);
        List<ThemePreset> presets = settings.themePresets();
        for (int index = 0; index < presets.size(); index++) {
            ThemePreset preset = presets.get(index);
            int column = index % PRESET_COLUMNS;
            int row = index / PRESET_COLUMNS;
            UiRect card = new UiRect(x + PRESET_INSET + column * (cardWidth + PRESET_GAP),
                    y + 24f + row * (PRESET_CARD_HEIGHT + PRESET_GAP), cardWidth, PRESET_CARD_HEIGHT);
            boolean selected = settings.themePreset.is(preset.name());
            boolean hover = card.contains(context.mouseX(), context.mouseY());
            float selection = animate(presetSelectionAnimations.getOrDefault(preset.name(), selected ? 1f : 0f),
                    selected ? 1f : 0f, speed);
            float hoverProgress = animate(presetHoverAnimations.getOrDefault(preset.name(), 0f),
                    hover ? 1f : 0f, speed);
            presetSelectionAnimations.put(preset.name(), selection);
            presetHoverAnimations.put(preset.name(), hoverProgress);

            context.frame().draw().roundedRect(card.x(), card.y(), card.width(), card.height(), 6f,
                    mix(0x14111316, 0x241B1E23, hoverProgress));
            if (selection > .001f) {
                context.frame().draw().roundedRect(card.x(), card.y(), card.width(), card.height(), 6f,
                        withAlpha(context.accentIn(card, 0x2C), selection));
            }
            context.frame().draw().outline(card.x(), card.y(), card.width(), card.height(), 6f, 1f,
                    mix(0x16FFFFFF, context.accentIn(card, 0x88), selection));

            UiRect swatch = new UiRect(card.x() + PRESET_CONTENT_PADDING,
                    card.y() + (card.height() - 16f) * .5f, 32f, 16f);
            int primary = opaque(settings.previewPrimary(preset));
            int secondary = opaque(settings.previewSecondary(preset));
            try (Paint paint = new Paint().setAntiAlias(true)) {
                paint.setShader(context.frame().effects().linearGradient(
                        new Point(swatch.x(), swatch.y()),
                        new Point(swatch.x() + swatch.width(), swatch.y()),
                        new int[]{primary, secondary}));
                context.frame().draw().roundedRect(swatch.x(), swatch.y(), swatch.width(), swatch.height(), 4f, paint);
            }
            context.frame().draw().outline(swatch.x(), swatch.y(), swatch.width(), swatch.height(),
                    4f, 1f, 0x24FFFFFF);
            String label = context.translate(settings.themePreset.getOptionTranslationKey(preset.name()),
                    preset.name());
            float labelX = swatch.x() + swatch.width() + 9f;
            float labelWidth = Math.max(10f, card.x() + card.width() - labelX - 12f);
            context.textIn(ellipsize(context, label, labelWidth),
                    new UiRect(labelX, card.y(), labelWidth, card.height()),
                    10f, mix(ClickGuiTheme.MUTED, ClickGuiTheme.TEXT, selection));
            if (selection > .001f) {
                context.frame().draw().roundedRect(card.x() + card.width() - 10f,
                        card.y() + (card.height() - 4f) * .5f, 4f, 4f, 2f,
                        withAlpha(context.accentIn(card, 0xD0), selection));
            }
            presetHits.add(new PresetHit(card, preset.name()));
        }
    }

    private static float animate(float value, float target, float speed) {
        float result = value + (target - value) * speed;
        return Math.abs(result - target) < .002f ? target : result;
    }

    private static int withAlpha(int color, float opacity) {
        int alpha = Math.round((color >>> 24) * Math.clamp(opacity, 0f, 1f));
        return color & 0x00FFFFFF | alpha << 24;
    }

    private static int opaque(int color) {
        return color | 0xFF000000;
    }

    private static int mix(int first, int second, float progress) {
        float value = Math.clamp(progress, 0f, 1f);
        int a = Math.round((first >>> 24) + ((second >>> 24) - (first >>> 24)) * value);
        int r = Math.round((first >> 16 & 0xFF) + ((second >> 16 & 0xFF) - (first >> 16 & 0xFF)) * value);
        int g = Math.round((first >> 8 & 0xFF) + ((second >> 8 & 0xFF) - (first >> 8 & 0xFF)) * value);
        int b = Math.round((first & 0xFF) + ((second & 0xFF) - (first & 0xFF)) * value);
        return a << 24 | r << 16 | g << 8 | b;
    }

    private static String ellipsize(UiContext context, String value, float width) {
        if (context.width(UiContext.FONT, 10f, value) <= width) return value;
        String result = value;
        while (!result.isEmpty() && context.width(UiContext.FONT, 10f, result + "...") > width) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "...";
    }
}
