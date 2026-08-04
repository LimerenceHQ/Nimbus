package cc.nimbus.ui.clickgui.tool;

import cc.nimbus.core.Core;
import cc.nimbus.features.client.ClientSettings;
import cc.nimbus.features.module.api.setting.Setting;
import cc.nimbus.ui.clickgui.component.SettingControls;
import cc.nimbus.ui.clickgui.component.UiContext;
import cc.nimbus.ui.clickgui.component.UiRect;
import cc.nimbus.ui.clickgui.theme.ClickGuiTheme;
import io.github.humbleui.types.Rect;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.PreeditEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class ClientSettingsTool implements ToolPage {
    private final SettingControls controls;
    private final List<TabHit> tabs = new ArrayList<>();
    private Tab selected = Tab.GENERAL;
    private UiRect contentBounds;
    private float scroll;
    private float targetScroll;
    private float maximumScroll;
    private float indicatorX = Float.NaN;
    private float indicatorWidth;
    private float tabTransition = 1f;

    public ClientSettingsTool(SettingControls controls) {
        this.controls = controls;
    }

    @Override
    public void render(UiContext context, UiRect bounds) {
        tabs.clear();
        context.medium(context.translate("nimbus.client_settings.title", "Client Settings"),
                bounds.x() + 22f, bounds.y() + 18f, 19f, ClickGuiTheme.TEXT);
        context.medium(context.translate("nimbus.client_settings.description",
                        "Global identity and rendering preferences"), bounds.x() + 22f, bounds.y() + 43f,
                10.5f, ClickGuiTheme.MUTED);
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
                indicatorWidth * .46f, 2.5f, 1.25f, context.theme().accent(0xB0));
        context.frame().draw().rect(bounds.x() + 22f, tabY + 34f, bounds.width() - 44f, 1f,
                0x12FFFFFF);

        contentBounds = new UiRect(bounds.x() + 22f, tabY + 43f, bounds.width() - 44f,
                bounds.height() - (tabY - bounds.y()) - 65f);
        List<Setting<?>> settings = settings(selected);
        float contentHeight = controls.height(settings, contentBounds.width());
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
                controls.render(context, settings, contentBounds.x() + (1f - tabTransition) * 10f,
                        contentBounds.y() + scroll, contentBounds.width(), contentBounds);
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
            case GENERAL -> List.of(value.clientName, value.language, value.style, value.accent, value.hudScale);
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

    private static float animate(float value, float target, float speed) {
        float result = value + (target - value) * speed;
        return Math.abs(result - target) < .002f ? target : result;
    }
}
