package cc.nimbus.ui.clickgui.tool;

import cc.nimbus.core.Core;
import cc.nimbus.features.client.ClientSettings;
import cc.nimbus.features.module.api.setting.Setting;
import cc.nimbus.ui.clickgui.component.SettingControls;
import cc.nimbus.ui.clickgui.component.UiContext;
import cc.nimbus.ui.clickgui.component.UiRect;
import cc.nimbus.ui.clickgui.theme.ClickGuiTheme;
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
    private float maximumScroll;

    public ClientSettingsTool(SettingControls controls) {
        this.controls = controls;
    }

    @Override
    public void render(UiContext context, UiRect bounds) {
        tabs.clear();
        context.medium("Client Settings", bounds.x() + 22f, bounds.y() + 18f, 19f, ClickGuiTheme.TEXT);
        context.text("Global identity and rendering preferences", bounds.x() + 22f, bounds.y() + 43f,
                10.5f, ClickGuiTheme.MUTED);
        float tabY = bounds.y() + 67f;
        float tabWidth = (bounds.width() - 44f - 18f) / 4f;
        for (int index = 0; index < Tab.values().length; index++) {
            Tab tab = Tab.values()[index];
            UiRect tabBounds = new UiRect(bounds.x() + 22f + index * (tabWidth + 6f), tabY, tabWidth, 28f);
            if (tab == selected || tabBounds.contains(context.mouseX(), context.mouseY())) {
                context.frame().draw().roundedRect(tabBounds.x(), tabBounds.y(), tabBounds.width(), tabBounds.height(),
                        3f, tab == selected ? context.theme().accent(0x24) : ClickGuiTheme.HOVER);
            }
            context.centered(tab.label, tabBounds, 10.5f,
                    tab == selected ? ClickGuiTheme.TEXT : ClickGuiTheme.MUTED);
            tabs.add(new TabHit(tabBounds, tab));
        }

        contentBounds = new UiRect(bounds.x() + 22f, tabY + 42f, bounds.width() - 44f,
                bounds.height() - (tabY - bounds.y()) - 64f);
        List<Setting<?>> settings = settings(selected);
        float contentHeight = controls.height(settings, contentBounds.width());
        maximumScroll = Math.max(0f, contentHeight - contentBounds.height());
        scroll = Math.clamp(scroll, -maximumScroll, 0f);
        int save = context.clip(contentBounds, 3f);
        try {
            controls.render(context, settings, contentBounds.x(), contentBounds.y() + scroll,
                    contentBounds.width(), contentBounds);
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
        scroll = Math.clamp(scroll + (float) delta * 24f, -maximumScroll, 0f);
        return true;
    }

    @Override public void closed() { controls.clearTransient(); }

    private static List<Setting<?>> settings(Tab tab) {
        ClientSettings value = Core.INSTANCE.getConfigManager().clientSettings();
        return switch (tab) {
            case GENERAL -> List.of(value.clientName, value.style, value.accent);
            case SURFACE -> List.of(value.postProcessing, value.splitRegions, value.texture, value.textureOpacity);
            case BLUR -> List.of(value.blurMode, value.blurRadius, value.blurPasses, value.downSample,
                    value.upSample, value.blurRefresh, value.blurRefreshLimit);
            case SHADOW -> List.of(value.shadow, value.shadowRadius, value.shadowOpacity,
                    value.shadowRefresh, value.shadowRefreshLimit);
        };
    }

    private enum Tab {
        GENERAL("General"), SURFACE("Surface"), BLUR("Blur"), SHADOW("Shadow");
        private final String label;
        Tab(String label) { this.label = label; }
    }
    private record TabHit(UiRect bounds, Tab tab) { }
}
