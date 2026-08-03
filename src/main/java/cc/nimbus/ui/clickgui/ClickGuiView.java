package cc.nimbus.ui.clickgui;

import cc.nimbus.core.Core;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.ui.clickgui.component.SettingControls;
import cc.nimbus.ui.clickgui.component.UiContext;
import cc.nimbus.ui.clickgui.component.UiRect;
import cc.nimbus.ui.clickgui.input.FocusManager;
import cc.nimbus.ui.clickgui.input.TextInput;
import cc.nimbus.ui.clickgui.theme.ClickGuiTheme;
import cc.nimbus.ui.clickgui.tool.ClientSettingsTool;
import cc.nimbus.ui.clickgui.tool.ConfigTool;
import cc.nimbus.ui.clickgui.tool.MusicTool;
import cc.nimbus.ui.clickgui.tool.ToolPage;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.types.Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.PreeditEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ClickGuiView {
    public static final float MIN_WIDTH = 1000f;
    public static final float MIN_HEIGHT = 620f;
    private static final float PANEL_GAP = 8f;
    private static final float PANEL_HEADER = 29f;
    private static final float MODULE_HEIGHT = 27f;
    private static final float RAIL_SIZE = 34f;
    private static final ModuleCategory[] CATEGORIES = {
            ModuleCategory.Combat, ModuleCategory.Move, ModuleCategory.Player,
            ModuleCategory.Render, ModuleCategory.Exploit, ModuleCategory.HUD
    };

    private final ClickGuiSession session;
    private final FocusManager focus = new FocusManager();
    private final TextInput search = new TextInput(Minecraft.getInstance().font, 64);
    private final SettingControls controls = new SettingControls(focus);
    private final EnumMap<ClickGuiSession.Tool, ToolPage> tools = new EnumMap<>(ClickGuiSession.Tool.class);
    private final EnumMap<ModuleCategory, UiRect> columnBounds = new EnumMap<>(ModuleCategory.class);
    private final List<ModuleHit> moduleHits = new ArrayList<>();
    private final List<RailHit> railHits = new ArrayList<>();
    private final Map<String, Float> expansion = new HashMap<>();
    private final Map<String, Float> activeAnimations = new HashMap<>();
    private final Map<String, Float> hoverAnimations = new HashMap<>();
    private final long openedAt = System.currentTimeMillis();
    private BasicModule binding;
    private ClickGuiSession.Tool activeTool = ClickGuiSession.Tool.NONE;
    private ClickGuiSession.Tool outgoingTool = ClickGuiSession.Tool.NONE;
    private UiRect toolBounds;
    private UiRect toolFrameBounds;
    private UiRect closeBounds;
    private UiRect searchBounds;
    private String tooltip = "";
    private String previousTooltip = "";
    private long tooltipSince;
    private boolean searchOpen;
    private boolean leftDown;
    private float searchProgress;
    private float toolProgress;
    private float columnsProgress = 1f;
    private float switchProgress = 1f;
    private boolean columnsInteractive = true;
    private float logicalWidth;
    private float logicalHeight;

    public ClickGuiView(ClickGuiSession session) {
        this.session = session;
        tools.put(ClickGuiSession.Tool.CONFIG, new ConfigTool(focus));
        tools.put(ClickGuiSession.Tool.CLIENT, new ClientSettingsTool(controls));
        tools.put(ClickGuiSession.Tool.MUSIC, new MusicTool(session, focus));
        if (session.tool() != ClickGuiSession.Tool.NONE) {
            activeTool = session.tool();
            ToolPage page = tools.get(activeTool);
            if (page != null) page.opened();
        }
    }

    public void render(UiContext context, float width, float height) {
        logicalWidth = width;
        logicalHeight = height;
        moduleHits.clear();
        railHits.clear();
        columnBounds.clear();
        tooltip = "";
        controls.beginFrame();
        float speed = 1f - (float) Math.exp(-context.deltaSeconds() * 15f);
        searchProgress = animate(searchProgress, searchOpen ? 1f : 0f, speed);
        toolProgress = animate(toolProgress, session.tool() == ClickGuiSession.Tool.NONE ? 0f : 1f, speed);
        columnsProgress = animate(columnsProgress, session.tool() == ClickGuiSession.Tool.NONE ? 1f : 0f, speed);
        columnsInteractive = session.tool() == ClickGuiSession.Tool.NONE && columnsProgress > .98f;
        switchProgress = animate(switchProgress, 1f, speed);
        if (outgoingTool != ClickGuiSession.Tool.NONE && switchProgress >= 1f) {
            ToolPage outgoing = tools.get(outgoingTool);
            if (outgoing != null) outgoing.closed();
            outgoingTool = ClickGuiSession.Tool.NONE;
        }
        if (columnsProgress > .005f) renderColumns(context, speed);
        renderTool(context);
        renderRail(context);
        renderTooltip(context);
    }

    private void renderColumns(UiContext context, float speed) {
        float railSpace = 62f;
        float available = logicalWidth - railSpace - 48f;
        float panelWidth = Math.clamp((available - PANEL_GAP * 5f) / 6f, 150f, 210f);
        float totalWidth = panelWidth * 6f + PANEL_GAP * 5f;
        float x = Math.max(20f, (logicalWidth - railSpace - totalWidth) * .5f);
        String query = search.value().strip().toLowerCase(Locale.ROOT);
        for (int index = 0; index < CATEGORIES.length; index++) {
            renderColumn(context, CATEGORIES[index], index, x + index * (panelWidth + PANEL_GAP),
                    panelWidth, query, speed);
        }
    }

    private void renderColumn(UiContext context, ModuleCategory category, int index, float x, float width,
                              String query, float speed) {
        List<BasicModule> modules = Core.INSTANCE.getModuleManager().getModulesByCategory(category).stream()
                .filter(module -> query.isEmpty()
                        || module.getDisplayName().toLowerCase(Locale.ROOT).contains(query)
                        || module.getDescription().toLowerCase(Locale.ROOT).contains(query))
                .toList();
        float contentHeight = 4f;
        for (BasicModule module : modules) {
            boolean expanded = module.getName().equals(session.expanded(category)) && !module.getSettings().isEmpty();
            float progress = expansion(module, expanded, speed);
            contentHeight += MODULE_HEIGHT + 1f;
            if (progress > .001f) contentHeight += controls.height(module.getSettings(), width - 10f) * progress + 4f;
        }
        float maximumHeight = logicalHeight - 92f;
        float panelHeight = Math.min(maximumHeight, PANEL_HEADER + Math.max(MODULE_HEIGHT + 6f, contentHeight));
        float opening = opening(index, context.nowMillis());
        float y = 44f - (1f - opening) * 12f - (1f - columnsProgress) * 18f;
        UiRect panel = new UiRect(x, y, width, panelHeight);
        columnBounds.put(category, panel);
        int layer = context.frame().canvas().saveLayerAlpha(
                Rect.makeXYWH(panel.x(), panel.y(), panel.width(), panel.height()),
                Math.round(255f * opening * columnsProgress));
        try {
            context.frame().draw().roundedRect(panel.x(), panel.y(), panel.width(), panel.height(), 4f,
                    ClickGuiTheme.PANEL);
            context.frame().draw().outline(panel.x(), panel.y(), panel.width(), panel.height(), 4f, 1f,
                    ClickGuiTheme.STROKE);
            context.medium(category.getName(), panel.x() + 10f, panel.y() + 7f, 11f, ClickGuiTheme.TEXT);
            context.frame().draw().rect(panel.x(), panel.y() + PANEL_HEADER - 1f, panel.width(), 1f,
                    ClickGuiTheme.STROKE);
            UiRect viewport = new UiRect(panel.x() + 4f, panel.y() + PANEL_HEADER,
                    panel.width() - 8f, panel.height() - PANEL_HEADER - 4f);
            float minimum = Math.min(0f, viewport.height() - contentHeight);
            float scroll = Math.clamp(session.scroll(category), minimum, 0f);
            session.scroll(category, scroll);
            int clip = context.clip(viewport, 2f);
            try {
                float rowY = viewport.y() + 3f + scroll;
                for (BasicModule module : modules) {
                    boolean expanded = module.getName().equals(session.expanded(category))
                            && !module.getSettings().isEmpty();
                    float progress = expansion.getOrDefault(module.getName(), expanded ? 1f : 0f);
                    UiRect row = new UiRect(viewport.x(), rowY, viewport.width(), MODULE_HEIGHT);
                    if (row.intersects(viewport)) renderModule(context, row, module, category, expanded, speed);
                    rowY += MODULE_HEIGHT + 1f;
                    float settingsHeight = controls.height(module.getSettings(), viewport.width() - 6f) * progress;
                    if (settingsHeight > .5f) {
                        UiRect settings = new UiRect(viewport.x() + 3f, rowY + 2f,
                                viewport.width() - 6f, settingsHeight);
                        if (settings.intersects(viewport)) {
                            int settingsClip = context.clip(settings, 2f);
                            try {
                                controls.render(context, module.getSettings(), settings.x(), settings.y(),
                                        settings.width(), viewport);
                            } finally {
                                context.restore(settingsClip);
                            }
                        }
                        rowY += settingsHeight + 4f;
                    }
                }
                if (modules.isEmpty()) context.centered("No modules", viewport, 10f, ClickGuiTheme.MUTED);
            } finally {
                context.restore(clip);
            }
        } finally {
            context.frame().canvas().restoreToCount(layer);
        }
    }

    private void renderModule(UiContext context, UiRect row, BasicModule module, ModuleCategory category,
                              boolean expanded, float speed) {
        boolean hover = columnsInteractive && row.contains(context.mouseX(), context.mouseY());
        float active = animate(activeAnimations, module.getName(), module.isEnabled(), speed);
        float hovered = animate(hoverAnimations, module.getName(), hover, speed);
        int background = mix(0x00000000, 0x20FFFFFF, active);
        background = mix(background, ClickGuiTheme.HOVER, hovered * (1f - active * .4f));
        if ((background >>> 24) != 0) {
            context.frame().draw().roundedRect(row.x(), row.y(), row.width(), row.height(), 2f, background);
        }
        String label = binding == module ? "Listening..." : module.getDisplayName();
        context.textIn(label, new UiRect(row.x() + 7f, row.y(), row.width() - 27f, row.height()),
                10.5f, module.isEnabled() ? ClickGuiTheme.TEXT : ClickGuiTheme.MUTED);
        if (!module.getSettings().isEmpty()) {
            context.centered(expanded ? "-" : "+",
                    new UiRect(row.x() + row.width() - 22f, row.y(), 18f, row.height()),
                    12f, expanded ? ClickGuiTheme.TEXT : ClickGuiTheme.MUTED);
        }
        if (columnsInteractive) moduleHits.add(new ModuleHit(row, module, category));
        if (hover) tooltip = module.getDescription();
    }

    private void renderRail(UiContext context) {
        float x = logicalWidth - 47f;
        float y = (logicalHeight - (RAIL_SIZE * 4f + 8f * 3f)) * .5f;
        rail(context, new UiRect(x, y, RAIL_SIZE, RAIL_SIZE), RailAction.SEARCH,
                ClickGuiIcons.SEARCH, "Search");
        rail(context, new UiRect(x, y + 42f, RAIL_SIZE, RAIL_SIZE), RailAction.MUSIC,
                ClickGuiIcons.MUSIC, "Music");
        rail(context, new UiRect(x, y + 84f, RAIL_SIZE, RAIL_SIZE), RailAction.CONFIG,
                ClickGuiIcons.CONFIG, "Config");
        rail(context, new UiRect(x, y + 126f, RAIL_SIZE, RAIL_SIZE), RailAction.CLIENT,
                ClickGuiIcons.SETTINGS, "Client Settings");
        if (searchProgress > .01f) {
            float width = 216f * searchProgress;
            searchBounds = new UiRect(x - 10f - width, y + 2f, width, 30f);
            if (width > 54f) {
                search.render(context, searchBounds, "Search modules", 10.5f, false, 0x780A0B0D);
            }
        } else searchBounds = null;
    }

    private void rail(UiContext context, UiRect bounds, RailAction action, String svg, String title) {
        boolean selected = switch (action) {
            case SEARCH -> searchOpen;
            case MUSIC -> session.tool() == ClickGuiSession.Tool.MUSIC;
            case CONFIG -> session.tool() == ClickGuiSession.Tool.CONFIG;
            case CLIENT -> session.tool() == ClickGuiSession.Tool.CLIENT;
        };
        boolean hover = bounds.contains(context.mouseX(), context.mouseY());
        int background = selected ? ClickGuiTheme.RAIL_ACTIVE
                : hover ? ClickGuiTheme.RAIL_HOVER : ClickGuiTheme.RAIL;
        context.frame().draw().roundedRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 4f,
                background);
        icon(context, svg, bounds.x() + 8f, bounds.y() + 8f, 18f,
                selected ? ClickGuiTheme.RAIL_ICON_ACTIVE : ClickGuiTheme.RAIL_ICON);
        railHits.add(new RailHit(bounds, action));
        if (hover) tooltip = title;
    }

    private void renderTool(UiContext context) {
        if (activeTool == ClickGuiSession.Tool.NONE || toolProgress < .005f) {
            toolBounds = null;
            toolFrameBounds = null;
            if (activeTool != ClickGuiSession.Tool.NONE && session.tool() == ClickGuiSession.Tool.NONE) {
                ToolPage page = tools.get(activeTool);
                if (page != null) page.closed();
                activeTool = ClickGuiSession.Tool.NONE;
            }
            return;
        }
        UiRect target = toolRect(activeTool);
        UiRect source = outgoingTool == ClickGuiSession.Tool.NONE ? target : toolRect(outgoingTool);
        float width = lerp(source.width(), target.width(), switchProgress);
        float height = lerp(source.height(), target.height(), switchProgress);
        float x = (logicalWidth - width) * .5f + (1f - toolProgress) * 72f;
        float y = (logicalHeight - height) * .5f;
        toolFrameBounds = new UiRect(x, y, width, height);
        int layer = context.frame().canvas().saveLayerAlpha(
                Rect.makeXYWH(x, y, width, height), Math.round(toolProgress * 255f));
        try {
            context.frame().draw().roundedRect(x, y, width, height, 6f, ClickGuiTheme.PANEL_STRONG);
            context.frame().draw().outline(x, y, width, height, 6f, 1f, ClickGuiTheme.STROKE);
            int clip = context.clip(toolFrameBounds, 6f);
            try {
                if (outgoingTool != ClickGuiSession.Tool.NONE && switchProgress < 1f) {
                    UiRect outgoingBounds = new UiRect(x - switchProgress * 48f, y, width, height);
                    renderToolPage(context, outgoingTool, outgoingBounds, 1f - switchProgress);
                }
                toolBounds = new UiRect(x + (1f - switchProgress) * 56f, y, width, height);
                renderToolPage(context, activeTool, toolBounds, switchProgress);
            } finally {
                context.restore(clip);
            }
            closeBounds = new UiRect(x + width - 37f, y + 12f, 26f, 26f);
            if (closeBounds.contains(context.mouseX(), context.mouseY())) {
                context.frame().draw().roundedRect(closeBounds.x(), closeBounds.y(), closeBounds.width(),
                        closeBounds.height(), 3f, ClickGuiTheme.HOVER);
            }
            icon(context, ClickGuiIcons.CLOSE, closeBounds.x() + 6f, closeBounds.y() + 6f, 14f,
                    ClickGuiTheme.MUTED);
        } finally {
            context.frame().canvas().restoreToCount(layer);
        }
    }

    private void renderToolPage(UiContext context, ClickGuiSession.Tool tool, UiRect bounds, float alpha) {
        ToolPage page = tools.get(tool);
        if (page == null || alpha <= .005f) return;
        int layer = context.frame().canvas().saveLayerAlpha(
                Rect.makeXYWH(bounds.x(), bounds.y(), bounds.width(), bounds.height()),
                Math.round(255f * Math.clamp(alpha, 0f, 1f)));
        try {
            page.render(context, bounds);
        } finally {
            context.frame().canvas().restoreToCount(layer);
        }
    }

    private UiRect toolRect(ClickGuiSession.Tool tool) {
        float widthRatio = switch (tool) {
            case MUSIC -> .72f;
            case CLIENT -> .56f;
            case CONFIG -> .46f;
            default -> 0f;
        };
        float heightRatio = switch (tool) {
            case MUSIC -> .76f;
            case CLIENT -> .66f;
            case CONFIG -> .56f;
            default -> 0f;
        };
        float width = Math.clamp(logicalWidth * widthRatio, tool == ClickGuiSession.Tool.MUSIC ? 720f : 440f,
                logicalWidth - 90f);
        float height = Math.clamp(logicalHeight * heightRatio, tool == ClickGuiSession.Tool.MUSIC ? 500f : 360f,
                logicalHeight - 70f);
        return new UiRect((logicalWidth - width) * .5f, (logicalHeight - height) * .5f, width, height);
    }

    private void renderTooltip(UiContext context) {
        if (!columnsInteractive) return;
        if (!tooltip.equals(previousTooltip)) {
            previousTooltip = tooltip;
            tooltipSince = context.nowMillis();
        }
        if (tooltip.isBlank() || context.nowMillis() - tooltipSince < 550L) return;
        float width = Math.min(270f, context.width(UiContext.FONT, 10f, tooltip) + 16f);
        UiRect bounds = new UiRect(Math.min(context.mouseX() + 12f, logicalWidth - width - 8f),
                Math.min(context.mouseY() + 14f, logicalHeight - 31f), width, 25f);
        context.frame().draw().roundedRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 3f,
                0xE80E1012);
        context.textIn(ellipsize(context, tooltip, width - 14f),
                new UiRect(bounds.x() + 7f, bounds.y(), width - 14f, bounds.height()), 10f, ClickGuiTheme.TEXT);
    }

    public boolean mouseClicked(float x, float y, int button, UiContext context) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) leftDown = true;
        for (RailHit hit : railHits) {
            if (!hit.bounds().contains(x, y) || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) continue;
            activate(hit.action());
            return true;
        }
        if (searchBounds != null && searchBounds.contains(x, y) && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            focus.focus(search);
            search.placeCursor(context, searchBounds, 10.5f, x);
            return true;
        }
        if (toolFrameBounds != null && toolProgress > .1f) {
            if (closeBounds != null && closeBounds.contains(x, y) && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                setTool(ClickGuiSession.Tool.NONE);
                return true;
            }
            if (session.tool() == ClickGuiSession.Tool.NONE) return true;
            if (toolFrameBounds.contains(x, y)) {
                ToolPage page = tools.get(activeTool);
                return page == null || page.mouseClicked(x, y, button);
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) setTool(ClickGuiSession.Tool.NONE);
            return true;
        }
        for (ModuleHit hit : moduleHits) {
            if (!hit.bounds().contains(x, y)) continue;
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) hit.module().toggle();
            else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && !hit.module().getSettings().isEmpty()) {
                String expanded = session.expanded(hit.category());
                session.expanded(hit.category(), hit.module().getName().equals(expanded) ? null : hit.module().getName());
                controls.clearTransient();
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) binding = hit.module();
            else return false;
            focus.clear();
            return true;
        }
        focus.clear();
        return controls.mouseClicked(x, y, button);
    }

    public void mouseMoved(float x, float y) {
        if (session.tool() == ClickGuiSession.Tool.NONE) controls.mouseMoved(x, y, leftDown);
        ToolPage page = tools.get(activeTool);
        if (session.tool() != ClickGuiSession.Tool.NONE && page != null) page.mouseMoved(x, y, leftDown);
    }

    public void mouseReleased() {
        leftDown = false;
        controls.mouseReleased();
        ToolPage page = tools.get(activeTool);
        if (page != null) page.mouseReleased();
    }

    public boolean mouseScrolled(float x, float y, double delta) {
        if (toolFrameBounds != null && toolFrameBounds.contains(x, y)) {
            ToolPage page = tools.get(activeTool);
            return page == null || page.mouseScrolled(x, y, delta);
        }
        if (session.tool() != ClickGuiSession.Tool.NONE) return true;
        for (var entry : columnBounds.entrySet()) {
            if (!entry.getValue().contains(x, y)) continue;
            session.scroll(entry.getKey(), session.scroll(entry.getKey()) + (float) delta * 24f);
            return true;
        }
        return false;
    }

    public boolean keyPressed(KeyEvent event) {
        if (binding != null) {
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) binding = null;
            else {
                binding.setKey(event.key() == GLFW.GLFW_KEY_BACKSPACE || event.key() == GLFW.GLFW_KEY_DELETE
                        ? 0 : event.key());
                binding = null;
            }
            return true;
        }
        ToolPage page = tools.get(activeTool);
        if (session.tool() != ClickGuiSession.Tool.NONE && page != null && page.keyPressed(event)) return true;
        if (controls.keyPressed(event)) return true;
        return searchOpen && search.keyPressed(event);
    }

    public boolean charTyped(CharacterEvent event) {
        ToolPage page = tools.get(activeTool);
        if (session.tool() != ClickGuiSession.Tool.NONE && page != null && page.charTyped(event)) return true;
        if (controls.charTyped(event)) return true;
        return searchOpen && search.charTyped(event);
    }

    public boolean preeditUpdated(PreeditEvent event) {
        ToolPage page = tools.get(activeTool);
        if (session.tool() != ClickGuiSession.Tool.NONE && page != null && page.preeditUpdated(event)) return true;
        if (controls.preeditUpdated(event)) return true;
        return searchOpen && search.preeditUpdated(event);
    }

    public boolean cancelTransient() {
        ToolPage page = tools.get(activeTool);
        if (session.tool() != ClickGuiSession.Tool.NONE && page != null && page.cancelTransient()) return true;
        if (binding != null) {
            binding = null;
            return true;
        }
        if (controls.cancelTransient()) return true;
        if (focus.hasFocus()) {
            focus.clear();
            return true;
        }
        if (searchOpen) {
            searchOpen = false;
            search.value("");
            return true;
        }
        if (session.tool() != ClickGuiSession.Tool.NONE) {
            setTool(ClickGuiSession.Tool.NONE);
            return true;
        }
        return false;
    }

    public void close() {
        search.value("");
        searchOpen = false;
        binding = null;
        controls.clearTransient();
        focus.clear();
        tools.values().forEach(ToolPage::closed);
        activeTool = ClickGuiSession.Tool.NONE;
        outgoingTool = ClickGuiSession.Tool.NONE;
    }

    private void activate(RailAction action) {
        switch (action) {
            case SEARCH -> {
                searchOpen = !searchOpen;
                if (searchOpen) focus.focus(search);
                else {
                    search.value("");
                    focus.clear();
                }
            }
            case MUSIC -> toggleTool(ClickGuiSession.Tool.MUSIC);
            case CONFIG -> toggleTool(ClickGuiSession.Tool.CONFIG);
            case CLIENT -> toggleTool(ClickGuiSession.Tool.CLIENT);
        }
    }

    private void toggleTool(ClickGuiSession.Tool tool) {
        setTool(session.tool() == tool ? ClickGuiSession.Tool.NONE : tool);
    }

    private void setTool(ClickGuiSession.Tool tool) {
        if (tool == session.tool()) return;
        if (tool != ClickGuiSession.Tool.NONE && tool == activeTool
                && session.tool() == ClickGuiSession.Tool.NONE) {
            session.tool(tool);
            return;
        }
        ToolPage active = tools.get(activeTool);
        if (active != null) active.cancelTransient();
        controls.clearTransient();
        focus.clear();
        if (outgoingTool != ClickGuiSession.Tool.NONE) {
            ToolPage previousOutgoing = tools.get(outgoingTool);
            if (previousOutgoing != null) previousOutgoing.closed();
            outgoingTool = ClickGuiSession.Tool.NONE;
        }
        session.tool(tool);
        if (tool == ClickGuiSession.Tool.NONE) return;
        for (ModuleCategory category : CATEGORIES) session.expanded(category, null);
        if (activeTool != ClickGuiSession.Tool.NONE) {
            outgoingTool = activeTool;
            switchProgress = 0f;
        } else {
            switchProgress = 1f;
            toolProgress = 0f;
        }
        activeTool = tool;
        ToolPage page = tools.get(tool);
        if (page != null) page.opened();
    }

    private float expansion(BasicModule module, boolean target, float speed) {
        float value = animate(expansion, module.getName(), target, speed);
        expansion.put(module.getName(), value);
        return value;
    }

    private float opening(int index, long now) {
        float value = Math.clamp((now - openedAt - index * 24L) / 220f, 0f, 1f);
        return 1f - (float) Math.pow(1f - value, 3d);
    }

    private static float animate(Map<String, Float> values, String key, boolean target, float speed) {
        float value = values.getOrDefault(key, target ? 1f : 0f);
        value = animate(value, target ? 1f : 0f, speed);
        values.put(key, value);
        return value;
    }

    private static float animate(float value, float target, float speed) {
        float result = value + (target - value) * speed;
        return Math.abs(result - target) < .002f ? target : result;
    }

    private static float lerp(float from, float to, float progress) {
        return from + (to - from) * Math.clamp(progress, 0f, 1f);
    }

    private static int mix(int from, int to, float progress) {
        float value = Math.clamp(progress, 0f, 1f);
        int a = Math.round((from >>> 24) + ((to >>> 24) - (from >>> 24)) * value);
        int r = Math.round((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * value);
        int g = Math.round((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * value);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * value);
        return a << 24 | r << 16 | g << 8 | b;
    }

    private static void icon(UiContext context, String svg, float x, float y, float size, int color) {
        try (Paint paint = new Paint().setAntiAlias(true).setColor(color)) {
            context.frame().draw().svg(svg, x, y, size, paint);
        }
    }

    private static String ellipsize(UiContext context, String value, float width) {
        if (value == null || context.width(UiContext.FONT, 10f, value) <= width) return value;
        String result = value;
        while (!result.isEmpty() && context.width(UiContext.FONT, 10f, result + "...") > width) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "...";
    }

    private enum RailAction { SEARCH, MUSIC, CONFIG, CLIENT }
    private record RailHit(UiRect bounds, RailAction action) { }
    private record ModuleHit(UiRect bounds, BasicModule module, ModuleCategory category) { }
}
