package cc.nimbus.ui.clickgui;

import cc.nimbus.features.module.api.ModuleCategory;

import java.util.EnumMap;

public final class ClickGuiSession {
    public enum Tool {
        NONE,
        CONFIG,
        MUSIC,
        CLIENT
    }

    public enum MusicRoute {
        RECOMMENDED,
        FEATURED,
        SEARCH,
        LIKED,
        CACHED,
        PLAYLIST,
        NOW_PLAYING,
        LOGIN
    }

    private final EnumMap<ModuleCategory, String> expanded = new EnumMap<>(ModuleCategory.class);
    private final EnumMap<ModuleCategory, Float> scroll = new EnumMap<>(ModuleCategory.class);
    private Tool tool = Tool.NONE;
    private MusicRoute musicRoute = MusicRoute.RECOMMENDED;

    public String expanded(ModuleCategory category) {
        return expanded.get(category);
    }

    public void expanded(ModuleCategory category, String moduleName) {
        if (moduleName == null) expanded.remove(category);
        else expanded.put(category, moduleName);
    }

    public float scroll(ModuleCategory category) {
        return scroll.getOrDefault(category, 0f);
    }

    public void scroll(ModuleCategory category, float value) {
        scroll.put(category, value);
    }

    public Tool tool() {
        return tool;
    }

    public void tool(Tool value) {
        tool = value == null ? Tool.NONE : value;
    }

    public MusicRoute musicRoute() {
        return musicRoute;
    }

    public void musicRoute(MusicRoute value) {
        musicRoute = value == null ? MusicRoute.RECOMMENDED : value;
    }
}
