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
import cc.nimbus.utils.client.input.Keyboard;
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
    private static final float PANEL_GAP = 10f;
    private static final float PANEL_HEADER = 36f;
    private static final float MODULE_HEIGHT = 31f;
    private static final float RAIL_SIZE = 38f;
    private static final ModuleCategory[] CATEGORIES = {
            ModuleCategory.Combat, ModuleCategory.Move, ModuleCategory.Player,
            ModuleCategory.Render, ModuleCategory.Exploit, ModuleCategory.HUD
    };
    private static final CategoryIcon COMBAT_ICON = new CategoryIcon(ClickGuiIcons.CATEGORY_COMBAT, 16f, 0f);
    private static final CategoryIcon MOVE_ICON = new CategoryIcon(ClickGuiIcons.CATEGORY_MOVE, 17f, 0f);
    private static final CategoryIcon PLAYER_ICON = new CategoryIcon(ClickGuiIcons.CATEGORY_PLAYER, 20f, 0f);
    private static final CategoryIcon RENDER_ICON = new CategoryIcon(ClickGuiIcons.CATEGORY_RENDER, 18f, 1.5f);
    private static final CategoryIcon EXPLOIT_ICON = new CategoryIcon(ClickGuiIcons.CATEGORY_EXPLOIT, 20f, 0f);
    private static final CategoryIcon HUD_ICON = new CategoryIcon(ClickGuiIcons.CATEGORY_HUD, 18f, 0f);

    private final ClickGuiSession session;
    private final FocusManager focus = new FocusManager();
    private final TextInput search = new TextInput(Minecraft.getInstance().font, 64);
    private final SettingControls controls = new SettingControls(focus);
    private final EnumMap<ClickGuiSession.Tool, ToolPage> tools = new EnumMap<>(ClickGuiSession.Tool.class);
    private final EnumMap<ModuleCategory, UiRect> columnBounds = new EnumMap<>(ModuleCategory.class);
    private final EnumMap<ModuleCategory, Float> animatedScroll = new EnumMap<>(ModuleCategory.class);
    private final List<ModuleHit> moduleHits = new ArrayList<>();
    private final List<RailHit> railHits = new ArrayList<>();
    private final Map<String, Float> expansion = new HashMap<>();
    private final Map<String, Float> activeAnimations = new HashMap<>();
    private final Map<String, Float> hoverAnimations = new HashMap<>();
    private final Map<String, Float> bindingAnimations = new HashMap<>();
    private final Map<String, Float> keyVisibilityAnimations = new HashMap<>();
    private final Map<String, Float> keyPulseAnimations = new HashMap<>();
    private final Map<String, Integer> observedKeys = new HashMap<>();
    private final Map<String, String> displayedKeys = new HashMap<>();
    private final EnumMap<RailAction, Float> railHoverAnimations = new EnumMap<>(RailAction.class);
    private final EnumMap<RailAction, Float> railActiveAnimations = new EnumMap<>(RailAction.class);
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
    private List<String> tooltipLines = List.of();
    private float tooltipWrapWidth = -1f;
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

    public void render(UiContext context, float width, float height, float closingProgress) {
        logicalWidth = width;
        logicalHeight = height;
        moduleHits.clear();
        railHits.clear();
        columnBounds.clear();
        tooltip = "";
        controls.beginFrame(context.deltaSeconds());
        float speed = 1f - (float) Math.exp(-context.deltaSeconds() * 12f);
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
        UiContext columnContext = session.tool() == ClickGuiSession.Tool.NONE
                ? context : context.withoutPointer();
        if (columnsProgress > .005f) renderColumns(columnContext, speed, closingProgress);
        renderTool(context);
        renderRail(context);
        renderTooltip(context);
    }

    private void renderColumns(UiContext context, float speed, float closingProgress) {
        float railSpace = 62f;
        float available = logicalWidth - railSpace - 48f;
        float panelWidth = Math.clamp((available - PANEL_GAP * 5f) / 6f, 150f, 210f);
        float totalWidth = panelWidth * 6f + PANEL_GAP * 5f;
        float x = Math.max(20f, (logicalWidth - railSpace - totalWidth) * .5f);
        String query = search.value().strip().toLowerCase(Locale.ROOT);
        for (int index = 0; index < CATEGORIES.length; index++) {
            renderColumn(context, CATEGORIES[index], index, x + index * (panelWidth + PANEL_GAP),
                    panelWidth, query, speed, closingProgress);
        }
    }

    private void renderColumn(UiContext context, ModuleCategory category, int index, float x, float width,
                              String query, float speed, float closingProgress) {
        List<BasicModule> modules = Core.INSTANCE.getModuleManager().getModulesByCategory(category).stream()
                .filter(module -> query.isEmpty()
                        || localizedModuleName(context, module).toLowerCase(Locale.ROOT).contains(query)
                        || localizedModuleDescription(context, module).toLowerCase(Locale.ROOT).contains(query))
                .toList();
        float contentHeight = 7f;
        for (BasicModule module : modules) {
            boolean expanded = module.getName().equals(session.expanded(category)) && !module.getSettings().isEmpty();
            float progress = expansion(module, expanded, speed);
            contentHeight += MODULE_HEIGHT + 2f;
            if (progress > .001f) {
                contentHeight += (controls.height(module.getSettings(), width - 23f) + 6f) * progress;
            }
        }
        float maximumHeight = logicalHeight - 82f;
        float panelHeight = Math.min(maximumHeight, PANEL_HEADER + Math.max(MODULE_HEIGHT + 6f, contentHeight));
        float opening = opening(index, context.nowMillis());
        float exit = columnExit(index, closingProgress);
        float y = 40f - (1f - opening) * 14f - (1f - columnsProgress) * 18f - exit * 12f;
        UiRect panel = new UiRect(x, y, width, panelHeight);
        columnBounds.put(category, panel);
        int layer = context.frame().canvas().saveLayerAlpha(
                Rect.makeXYWH(panel.x(), panel.y(), panel.width(), panel.height()),
                Math.round(255f * opening * columnsProgress * (1f - exit)));
        try {
            context.frame().draw().roundedRect(panel.x(), panel.y(), panel.width(), panel.height(), 7f,
                    ClickGuiTheme.PANEL);
            context.frame().draw().outline(panel.x(), panel.y(), panel.width(), panel.height(), 7f, 1f,
                    ClickGuiTheme.STROKE);
            String categoryLabel = context.translate(category.getTranslationKey(), category.getName());
            CategoryIcon categoryIcon = categoryIcon(category);
            float iconCenterX = panel.x() + 19f;
            float textTop = panel.y() + (PANEL_HEADER - context.height(UiContext.FONT, 11.5f,
                    categoryLabel)) * .5f;
            float iconCenterY = textTop + context.visualCenterY(UiContext.FONT, 11.5f, categoryLabel);
            icon(context, categoryIcon.svg(), iconCenterX - categoryIcon.size() * .5f,
                    iconCenterY - categoryIcon.size() * .5f + categoryIcon.offsetY(),
                    categoryIcon.size(), ClickGuiTheme.TEXT);
            context.textIn(categoryLabel,
                    new UiRect(panel.x() + 34f, panel.y(), panel.width() - 62f, PANEL_HEADER),
                    11.5f, ClickGuiTheme.TEXT);
            context.centered(Integer.toString(modules.size()),
                    new UiRect(panel.x() + panel.width() - 27f, panel.y(), 20f, PANEL_HEADER),
                    10f, ClickGuiTheme.MUTED);
            context.frame().draw().rect(panel.x(), panel.y() + PANEL_HEADER - 1f, panel.width(), 1f,
                    ClickGuiTheme.STROKE);
            UiRect viewport = new UiRect(panel.x() + 5f, panel.y() + PANEL_HEADER,
                    panel.width() - 10f, panel.height() - PANEL_HEADER - 5f);
            float minimum = Math.min(0f, viewport.height() - contentHeight);
            float targetScroll = Math.clamp(session.scroll(category), minimum, 0f);
            session.scroll(category, targetScroll);
            float scroll = animate(Math.clamp(animatedScroll.getOrDefault(category, targetScroll), minimum, 0f),
                    targetScroll, 1f - (float) Math.exp(-context.deltaSeconds() * 13f));
            animatedScroll.put(category, scroll);
            int clip = context.clip(viewport, 2f);
            try {
                float rowY = viewport.y() + 7f + scroll;
                for (BasicModule module : modules) {
                    boolean expanded = module.getName().equals(session.expanded(category))
                            && !module.getSettings().isEmpty();
                    float progress = expansion.getOrDefault(module.getName(), expanded ? 1f : 0f);
                    UiRect row = new UiRect(viewport.x(), rowY, viewport.width(), MODULE_HEIGHT);
                    if (row.intersects(viewport)) renderModule(context, row, module, category, expanded, speed);
                    rowY += MODULE_HEIGHT + 2f;
                    float settingsHeight = controls.height(module.getSettings(), viewport.width() - 13f) * progress;
                    if (settingsHeight > .5f) {
                        UiRect settings = new UiRect(viewport.x() + 9f, rowY + 1f,
                                viewport.width() - 13f, settingsHeight);
                        if (settings.intersects(viewport)) {
                            context.frame().draw().roundedRect(viewport.x() + 3f, settings.y(), 2f,
                                    settings.height(), 1f, context.theme().accent(0x46));
                            int settingsClip = context.clip(settings, 2f);
                            try {
                                controls.render(context, module.getSettings(), settings.x(), settings.y(),
                                        settings.width(), intersection(settings, viewport));
                            } finally {
                                context.restore(settingsClip);
                            }
                        }
                        rowY += settingsHeight + 6f * progress;
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
        float bindingProgress = animate(bindingAnimations, module.getName(), binding == module, speed);
        Integer previousKey = observedKeys.put(module.getName(), module.getKey());
        if (previousKey != null && previousKey != module.getKey()) keyPulseAnimations.put(module.getName(), 1f);
        float keyPulse = animate(keyPulseAnimations.getOrDefault(module.getName(), 0f), 0f, speed * .72f);
        keyPulseAnimations.put(module.getName(), keyPulse);
        if (module.getKey() != 0) displayedKeys.put(module.getName(), Keyboard.getKeyName(module.getKey()));
        float keyVisibility = animate(keyVisibilityAnimations.getOrDefault(module.getName(),
                        module.getKey() == 0 ? 0f : 1f), module.getKey() == 0 ? 0f : 1f, speed);
        keyVisibilityAnimations.put(module.getName(), keyVisibility);
        int background = mix(0x00000000, 0x18FFFFFF, active);
        background = mix(background, ClickGuiTheme.HOVER, hovered * (1f - active * .4f));
        background = mix(background, context.theme().accent(0x30), bindingProgress);
        if ((background >>> 24) != 0) {
            context.frame().draw().roundedRect(row.x(), row.y(), row.width(), row.height(), 5f, background);
        }
        boolean hasSettings = !module.getSettings().isEmpty();
        float right = row.x() + row.width() - (hasSettings ? 27f : 7f);
        String keyName = displayedKeys.getOrDefault(module.getName(), "");
        float keyWidth = Math.clamp(context.width(UiContext.FONT, 8.5f, keyName) + 10f, 27f, 49f);
        UiRect keyBounds = new UiRect(right - keyWidth, row.y() + 7f, keyWidth, 17f);
        float keyAlpha = keyVisibility * (1f - bindingProgress);
        if (keyAlpha > .001f) {
            int keyLayer = context.frame().canvas().saveLayerAlpha(
                    Rect.makeXYWH(keyBounds.x(), keyBounds.y(), keyBounds.width(), keyBounds.height()),
                    Math.round(255f * keyAlpha));
            try {
                context.frame().draw().roundedRect(keyBounds.x(), keyBounds.y(), keyBounds.width(),
                        keyBounds.height(), 4f, mix(0x16FFFFFF, context.theme().accent(0x44), keyPulse));
                context.visualCentered(ellipsize(context, keyName, keyBounds.width() - 6f, 8.5f),
                        keyBounds, 8.5f,
                        mix(ClickGuiTheme.MUTED, ClickGuiTheme.TEXT, keyPulse));
            } finally {
                context.frame().canvas().restoreToCount(keyLayer);
            }
        }
        float normalLabelRight = lerp(right, keyBounds.x() - 5f, keyVisibility);
        float normalLabelWidth = Math.max(20f, normalLabelRight - row.x() - 9f);
        String moduleLabel = ellipsize(context, localizedModuleName(context, module), normalLabelWidth, 11f);
        if (active > .01f) {
            float indicatorHeight = Math.min(17f, row.height() - 10f);
            context.frame().draw().roundedRect(row.x(), row.y() + (row.height() - indicatorHeight) * .5f,
                    2.5f * active, indicatorHeight, 1.25f, context.theme().accent(0x70));
        }
        if (bindingProgress < .999f) {
            int normalLayer = context.frame().canvas().saveLayerAlpha(
                    Rect.makeXYWH(row.x(), row.y(), row.width(), row.height()),
                    Math.round(255f * (1f - bindingProgress)));
            try {
                context.visualTextIn(moduleLabel,
                        new UiRect(row.x() + 9f, row.y(), normalLabelWidth, row.height()), 11f,
                        module.isEnabled() ? ClickGuiTheme.TEXT : ClickGuiTheme.MUTED);
            } finally {
                context.frame().canvas().restoreToCount(normalLayer);
            }
        }
        if (bindingProgress > .001f) {
            float bindingLabelWidth = Math.max(20f, right - row.x() - 9f);
            int bindingLayer = context.frame().canvas().saveLayerAlpha(
                    Rect.makeXYWH(row.x(), row.y(), row.width(), row.height()),
                    Math.round(255f * bindingProgress));
            try {
                context.visualTextIn(context.translate("nimbus.clickgui.press_key", "Press key"),
                        new UiRect(row.x() + 9f, row.y(), bindingLabelWidth, row.height()),
                        11f, ClickGuiTheme.TEXT);
            } finally {
                context.frame().canvas().restoreToCount(bindingLayer);
            }
        }
        if (hasSettings) {
            float disclosure = expansion.getOrDefault(module.getName(), expanded ? 1f : 0f);
            iconAlpha(context, ClickGuiIcons.EXPAND_MORE, row.x() + row.width() - 22f,
                    row.y() + 8.5f - disclosure, 14f, ClickGuiTheme.MUTED, 1f - disclosure);
            iconAlpha(context, ClickGuiIcons.EXPAND_LESS, row.x() + row.width() - 22f,
                    row.y() + 9.5f - disclosure, 14f, ClickGuiTheme.TEXT, disclosure);
        }
        if (columnsInteractive) moduleHits.add(new ModuleHit(row, module, category));
        if (hover) tooltip = localizedModuleDescription(context, module);
    }

    private void renderRail(UiContext context) {
        float x = logicalWidth - 51f;
        float y = (logicalHeight - (RAIL_SIZE * 4f + 8f * 3f)) * .5f;
        rail(context, new UiRect(x, y, RAIL_SIZE, RAIL_SIZE), RailAction.SEARCH,
                ClickGuiIcons.RAIL_SEARCH, context.translate("nimbus.clickgui.search", "Search"));
        rail(context, new UiRect(x, y + 46f, RAIL_SIZE, RAIL_SIZE), RailAction.MUSIC,
                ClickGuiIcons.RAIL_MUSIC, context.translate("nimbus.clickgui.music", "Music"));
        rail(context, new UiRect(x, y + 92f, RAIL_SIZE, RAIL_SIZE), RailAction.CONFIG,
                ClickGuiIcons.RAIL_CONFIG, context.translate("nimbus.clickgui.config", "Config"));
        rail(context, new UiRect(x, y + 138f, RAIL_SIZE, RAIL_SIZE), RailAction.CLIENT,
                ClickGuiIcons.RAIL_SETTINGS,
                context.translate("nimbus.clickgui.client_settings", "Client Settings"));
        if (searchProgress > .01f) {
            float width = 216f * searchProgress;
            searchBounds = new UiRect(x - 10f - width, y + 3f, width, 32f);
            if (width > 54f) {
                search.render(context, searchBounds,
                        context.translate("nimbus.clickgui.search_modules", "Search modules"),
                        10.5f, false, 0x780A0B0D);
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
        float speed = 1f - (float) Math.exp(-context.deltaSeconds() * 14f);
        float hoverProgress = animate(railHoverAnimations.getOrDefault(action, 0f), hover ? 1f : 0f, speed);
        float activeProgress = animate(railActiveAnimations.getOrDefault(action, selected ? 1f : 0f),
                selected ? 1f : 0f, speed);
        railHoverAnimations.put(action, hoverProgress);
        railActiveAnimations.put(action, activeProgress);
        int background = mix(ClickGuiTheme.RAIL, ClickGuiTheme.RAIL_HOVER, hoverProgress);
        background = mix(background, ClickGuiTheme.RAIL_ACTIVE, activeProgress);
        context.frame().draw().roundedRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 8f,
                background);
        icon(context, svg, bounds.x() + 9f, bounds.y() + 9f, 20f,
                mix(ClickGuiTheme.RAIL_ICON, ClickGuiTheme.RAIL_ICON_ACTIVE, activeProgress));
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
            ToolPage activePage = tools.get(activeTool);
            if (activePage != null && activePage.showHostCloseButton()) {
                closeBounds = new UiRect(x + width - 37f, y + 12f, 26f, 26f);
                if (closeBounds.contains(context.mouseX(), context.mouseY())) {
                    context.frame().draw().roundedRect(closeBounds.x(), closeBounds.y(), closeBounds.width(),
                            closeBounds.height(), 3f, ClickGuiTheme.HOVER);
                }
                icon(context, ClickGuiIcons.CLOSE, closeBounds.x() + 6f, closeBounds.y() + 6f, 14f,
                        ClickGuiTheme.MUTED);
            } else {
                closeBounds = null;
            }
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
            case MUSIC -> .82f;
            case CLIENT -> .56f;
            case CONFIG -> .46f;
            default -> 0f;
        };
        float heightRatio = switch (tool) {
            case MUSIC -> .84f;
            case CLIENT -> .66f;
            case CONFIG -> .56f;
            default -> 0f;
        };
        float width = Math.clamp(logicalWidth * widthRatio, tool == ClickGuiSession.Tool.MUSIC ? 820f : 440f,
                logicalWidth - 90f);
        float height = Math.clamp(logicalHeight * heightRatio, tool == ClickGuiSession.Tool.MUSIC ? 560f : 360f,
                logicalHeight - 70f);
        return new UiRect((logicalWidth - width) * .5f, (logicalHeight - height) * .5f, width, height);
    }

    private void renderTooltip(UiContext context) {
        if (!columnsInteractive) return;
        if (!tooltip.equals(previousTooltip)) {
            previousTooltip = tooltip;
            tooltipSince = context.nowMillis();
            tooltipLines = List.of();
            tooltipWrapWidth = -1f;
        }
        if (tooltip.isBlank() || context.nowMillis() - tooltipSince < 550L) return;

        float contentLimit = Math.max(80f, Math.min(326f, logicalWidth - 30f));
        if (tooltipLines.isEmpty() || tooltipWrapWidth != contentLimit) {
            tooltipLines = wrap(context, tooltip, contentLimit, 10f);
            tooltipWrapWidth = contentLimit;
        }
        double measuredWidth = tooltipLines.stream()
                .mapToDouble(line -> context.width(UiContext.FONT, 10f, line))
                .max()
                .orElse(0d);
        float lineHeight = context.height(UiContext.FONT, 10f) + 2f;
        float width = (float) measuredWidth + 14f;
        float height = tooltipLines.size() * lineHeight + 8f;
        float x = Math.clamp(context.mouseX() + 12f, 8f, Math.max(8f, logicalWidth - width - 8f));
        float below = context.mouseY() + 14f;
        float y = below + height <= logicalHeight - 8f
                ? below
                : context.mouseY() - height - 8f;
        y = Math.clamp(y, 8f, Math.max(8f, logicalHeight - height - 8f));
        UiRect bounds = new UiRect(x, y, width, height);
        context.frame().draw().roundedRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 3f,
                0xE80E1012);
        for (int index = 0; index < tooltipLines.size(); index++) {
            context.text(tooltipLines.get(index), bounds.x() + 7f, bounds.y() + 4f + index * lineHeight,
                    10f, ClickGuiTheme.TEXT);
        }
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
                boolean closing = hit.module().getName().equals(expanded);
                session.expanded(hit.category(), closing ? null : hit.module().getName());
                if (closing) controls.finishInput();
                else controls.clearTransient();
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
        float value = Math.clamp((now - openedAt - index * 28L) / 320f, 0f, 1f);
        return 1f - (float) Math.pow(1f - value, 3d);
    }

    private static float columnExit(int index, float closingProgress) {
        float start = (CATEGORIES.length - 1 - index) * .1f;
        float value = Math.clamp((closingProgress - start) / .5f, 0f, 1f);
        return value * value * (3f - 2f * value);
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

    private static UiRect intersection(UiRect first, UiRect second) {
        float left = Math.max(first.x(), second.x());
        float top = Math.max(first.y(), second.y());
        float right = Math.min(first.x() + first.width(), second.x() + second.width());
        float bottom = Math.min(first.y() + first.height(), second.y() + second.height());
        return new UiRect(left, top, Math.max(0f, right - left), Math.max(0f, bottom - top));
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

    private static void iconAlpha(UiContext context, String svg, float x, float y, float size,
                                  int color, float alpha) {
        float value = Math.clamp(alpha, 0f, 1f);
        if (value <= .001f) return;
        int layer = context.frame().canvas().saveLayerAlpha(
                Rect.makeXYWH(x, y, size, size), Math.round(255f * value));
        try {
            icon(context, svg, x, y, size, color);
        } finally {
            context.frame().canvas().restoreToCount(layer);
        }
    }

    private static CategoryIcon categoryIcon(ModuleCategory category) {
        return switch (category) {
            case Combat -> COMBAT_ICON;
            case Move -> MOVE_ICON;
            case Player -> PLAYER_ICON;
            case Render -> RENDER_ICON;
            case Exploit -> EXPLOIT_ICON;
            case HUD -> HUD_ICON;
        };
    }

    private record CategoryIcon(String svg, float size, float offsetY) {
    }

    private static String localizedModuleName(UiContext context, BasicModule module) {
        return context.translate(module.getDisplayNameTranslationKey(), module.getDisplayName());
    }

    private static String localizedModuleDescription(UiContext context, BasicModule module) {
        return context.translate(module.getDescriptionTranslationKey(), module.getDescription());
    }

    private static String ellipsize(UiContext context, String value, float width) {
        return ellipsize(context, value, width, 10f);
    }

    private static String ellipsize(UiContext context, String value, float width, float size) {
        if (value == null || context.width(UiContext.FONT, size, value) <= width) return value;
        String result = value;
        while (!result.isEmpty() && context.width(UiContext.FONT, size, result + "...") > width) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "...";
    }

    private static List<String> wrap(UiContext context, String value, float width, float size) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : value.split("\\R", -1)) {
            if (paragraph.isEmpty()) {
                lines.add("");
                continue;
            }
            int lineStart = 0;
            int cursor = 0;
            int lastWhitespace = -1;
            while (cursor < paragraph.length()) {
                int next = paragraph.offsetByCodePoints(cursor, 1);
                if (Character.isWhitespace(paragraph.codePointAt(cursor))) lastWhitespace = cursor;
                String candidate = paragraph.substring(lineStart, next).stripTrailing();
                if (context.width(UiContext.FONT, size, candidate) > width) {
                    int lineEnd = lastWhitespace >= lineStart ? lastWhitespace : cursor;
                    if (lineEnd == lineStart) lineEnd = next;
                    lines.add(paragraph.substring(lineStart, lineEnd).strip());
                    lineStart = lineEnd;
                    while (lineStart < paragraph.length()
                            && Character.isWhitespace(paragraph.codePointAt(lineStart))) {
                        lineStart = paragraph.offsetByCodePoints(lineStart, 1);
                    }
                    cursor = lineStart;
                    lastWhitespace = -1;
                    continue;
                }
                cursor = next;
            }
            if (lineStart < paragraph.length()) lines.add(paragraph.substring(lineStart).strip());
        }
        return lines.isEmpty() ? List.of("") : List.copyOf(lines);
    }

    private enum RailAction { SEARCH, MUSIC, CONFIG, CLIENT }
    private record RailHit(UiRect bounds, RailAction action) { }
    private record ModuleHit(UiRect bounds, BasicModule module, ModuleCategory category) { }
}
