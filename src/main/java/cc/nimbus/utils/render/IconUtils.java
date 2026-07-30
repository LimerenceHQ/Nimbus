package cc.nimbus.utils.render;

import io.github.humbleui.skija.*;
import io.github.humbleui.skija.svg.SVGDOM;
import io.github.humbleui.skija.svg.SVGSVG;
import io.github.humbleui.types.Rect;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;

import java.util.HashMap;
import java.util.Map;

public class IconUtils {
    private static final Map<String, SVGDOM> SVG_CACHE = new HashMap<>();

    public static final String SEARCH_ICON = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"28\" height=\"28\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" class=\"lucide lucide-search\" aria-hidden=\"true\"><path d=\"m21 21-4.34-4.34\"></path><circle cx=\"11\" cy=\"11\" r=\"8\"></circle></svg>";
    public static final String CATEGORY_COMBAT_ICON = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><circle cx=\"12\" cy=\"12\" r=\"10\"></circle><line x1=\"22\" y1=\"12\" x2=\"18\" y2=\"12\"></line><line x1=\"6\" y1=\"12\" x2=\"2\" y2=\"12\"></line><line x1=\"12\" y1=\"6\" x2=\"12\" y2=\"2\"></line><line x1=\"12\" y1=\"22\" x2=\"12\" y2=\"18\"></line></svg>";
    public static final String CATEGORY_MOVEMENT_ICON = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><polygon points=\"13 2 3 14 12 14 11 22 21 10 12 10 13 2\"></polygon></svg>";
    public static final String CATEGORY_PLAYER_ICON = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><path d=\"M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2\"></path><circle cx=\"12\" cy=\"7\" r=\"4\"></circle></svg>";
    public static final String CATEGORY_RENDER_ICON = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><path d=\"M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z\"></path><circle cx=\"12\" cy=\"12\" r=\"3\"></circle></svg>";
    public static final String CATEGORY_EXPLOIT_ICON = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><path d=\"M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z\"></path><line x1=\"12\" y1=\"9\" x2=\"12\" y2=\"13\"></line><line x1=\"12\" y1=\"17\" x2=\"12.01\" y2=\"17\"></line></svg>";
    public static final String CATEGORY_GHOST_ICON = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><path d=\"M9 10h.01\"></path><path d=\"M15 10h.01\"></path><path d=\"M12 2a8 8 0 0 0-8 8v12l3-3 2.5 2.5L12 19l2.5 2.5L17 19l3 3V10a8 8 0 0 0-8-8z\"></path></svg>";
    public static final String CATEGORY_HUD_ICON = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><rect width=\"7\" height=\"9\" x=\"3\" y=\"3\" rx=\"1\"></rect><rect width=\"7\" height=\"5\" x=\"14\" y=\"3\" rx=\"1\"></rect><rect width=\"7\" height=\"9\" x=\"14\" y=\"12\" rx=\"1\"></rect><rect width=\"7\" height=\"5\" x=\"3\" y=\"16\" rx=\"1\"></rect></svg>";
    public static final String SPEED_ICON = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"28\" height=\"28\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" class=\"lucide lucide-zap\" aria-hidden=\"true\"><path d=\"M4 14a1 1 0 0 1-.78-1.63l9.9-10.2a.5.5 0 0 1 .86.46l-1.92 6.02A1 1 0 0 0 13 10h7a1 1 0 0 1 .78 1.63l-9.9 10.2a.5.5 0 0 1-.86-.46l1.92-6.02A1 1 0 0 0 11 14z\"></path></svg>";
    public static final String SLOWNESS_ICON = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"28\" height=\"28\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" class=\"lucide lucide-snail\" aria-hidden=\"true\"><path d=\"M2 13a6 6 0 1 0 12 0 4 4 0 1 0-8 0 2 2 0 0 0 4 0\"></path><circle cx=\"10\" cy=\"13\" r=\"8\"></circle><path d=\"M2 21h12c4.4 0 8-3.6 8-8V7a2 2 0 1 0-4 0v6\"></path><path d=\"M18 3 19.1 5.2\"></path><path d=\"M22 3 20.9 5.2\"></path></svg>";
    public static final String HASTE_ICON = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"28\" height=\"28\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" class=\"lucide lucide-pickaxe\" aria-hidden=\"true\"><path d=\"M14.531 12.469 6.619 20.38a1 1 0 1 1-3-3l7.912-7.912\"></path><path d=\"M15.686 4.314A12.5 12.5 0 0 0 5.461 2.958 1 1 0 0 0 5.58 4.71a22 22 0 0 1 6.318 3.393\"></path><path d=\"M17.7 3.7a1 1 0 0 0-1.4 0l-4.6 4.6a1 1 0 0 0 0 1.4l2.6 2.6a1 1 0 0 0 1.4 0l4.6-4.6a1 1 0 0 0 0-1.4z\"></path><path d=\"M19.686 8.314a12.501 12.501 0 0 1 1.356 10.225 1 1 0 0 1-1.751-.119 22 22 0 0 0-3.393-6.319\"></path></svg>";
    public static final String SHIELD_ICON = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"28\" height=\"28\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" class=\"lucide lucide-shield\" aria-hidden=\"true\"><path d=\"M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z\"></path></svg>";
    public static final String SWORDS_ICON = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"28\" height=\"28\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" class=\"lucide lucide-swords\" aria-hidden=\"true\"><polyline points=\"14.5 17.5 3 6 3 3 6 3 17.5 14.5\"></polyline><line x1=\"13\" x2=\"19\" y1=\"19\" y2=\"13\"></line><line x1=\"16\" x2=\"20\" y1=\"16\" y2=\"20\"></line><line x1=\"19\" x2=\"21\" y1=\"21\" y2=\"19\"></line><polyline points=\"14.5 6.5 18 3 21 3 21 6 17.5 9.5\"></polyline><line x1=\"5\" x2=\"9\" y1=\"14\" y2=\"18\"></line><line x1=\"7\" x2=\"4\" y1=\"17\" y2=\"20\"></line><line x1=\"3\" x2=\"5\" y1=\"19\" y2=\"21\"></line></svg>";
    public static final String STRENGTH_ICON = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"28\" height=\"28\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" class=\"lucide lucide-dumbbell\" aria-hidden=\"true\"><path d=\"M17.596 12.768a2 2 0 1 0 2.829-2.829l-1.768-1.767a2 2 0 0 0 2.828-2.829l-2.828-2.828a2 2 0 0 0-2.829 2.828l-1.767-1.768a2 2 0 1 0-2.829 2.829z\"></path><path d=\"m2.5 21.5 1.4-1.4\"></path><path d=\"m20.1 3.9 1.4-1.4\"></path><path d=\"M5.343 21.485a2 2 0 1 0 2.829-2.828l1.767 1.768a2 2 0 1 0 2.829-2.829l-6.364-6.364a2 2 0 1 0-2.829 2.829l1.768 1.767a2 2 0 0 0-2.828 2.829z\"></path><path d=\"m9.6 14.4 4.8-4.8\"></path></svg>";
    public static final String HEALTH_ICON = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"28\" height=\"28\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" class=\"lucide lucide-heart\" aria-hidden=\"true\"><path d=\"M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z\"></path></svg>";
    public static final String NAUSEA_ICON = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"28\" height=\"28\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" class=\"lucide lucide-tornado\" aria-hidden=\"true\"><path d=\"M21 4H3\"></path><path d=\"M18 8H6\"></path><path d=\"M19 12H9\"></path><path d=\"M16 16h-6\"></path><path d=\"M11 20H9\"></path></svg>";
    public static final String REGENERATION_ICON = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"28\" height=\"28\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" class=\"lucide lucide-heart-pulse\" aria-hidden=\"true\"><path d=\"M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z\"></path><path d=\"M3.22 12H9.5l.5-1 2 4.5 2-7 1.5 3.5h5.27\"></path></svg>";
    public static final String RESISTANCE_ICON = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"28\" height=\"28\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" class=\"lucide lucide-shield\" aria-hidden=\"true\"><path d=\"M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z\"></path></svg>";
    public static final String FIRE_RESISTANCE_ICON = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"28\" height=\"28\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" class=\"lucide lucide-flame\" aria-hidden=\"true\"><path d=\"M8.5 14.5A2.5 2.5 0 0 0 11 12c0-1.38-.5-2-1-3-1.072-2.143-.224-4.054 2-6 .5 2.5 2 4.9 4 6.5 2 1.6 3 3.5 3 5.5a7 7 0 1 1-14 0c0-1.153.433-2.294 1-3a2.5 2.5 0 0 0 2.5 2.5z\"></path></svg>";
    public static final String INVISIBILITY_ICON = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"28\" height=\"28\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" class=\"lucide lucide-ghost\" aria-hidden=\"true\"><path d=\"M9 10h.01\"></path><path d=\"M15 10h.01\"></path><path d=\"M12 2a8 8 0 0 0-8 8v12l3-3 2.5 2.5L12 19l2.5 2.5L17 19l3 3V10a8 8 0 0 0-8-8z\"></path></svg>";
    public static final String BLINDNESS_ICON = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"28\" height=\"28\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" class=\"lucide lucide-eye-off\" aria-hidden=\"true\"><path d=\"M10.733 5.076a10.744 10.744 0 0 1 11.205 6.575 1 1 0 0 1 0 .696 10.747 10.747 0 0 1-1.444 2.49\"></path><path d=\"M14.084 14.158a3 3 0 0 1-4.242-4.242\"></path><path d=\"M17.479 17.499a10.75 10.75 0 0 1-15.417-5.151 1 1 0 0 1 0-.696 10.75 10.75 0 0 1 4.446-5.143\"></path><path d=\"m2 2 20 20\"></path></svg>";
    public static final String NIGHT_VISION_ICON = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"28\" height=\"28\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" class=\"lucide lucide-eye\" aria-hidden=\"true\"><path d=\"M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0\"></path><circle cx=\"12\" cy=\"12\" r=\"3\"></circle></svg>";
    public static final String HUNGER_ICON = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"28\" height=\"28\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" class=\"lucide lucide-drumstick\" aria-hidden=\"true\"><path d=\"M15.4 15.63a7.875 6 135 1 1 6.23-6.23 4.5 3.43 135 0 0-6.23 6.23\"></path><path d=\"m8.29 12.71-2.6 2.6a2.5 2.5 0 1 0-1.65 4.65A2.5 2.5 0 1 0 8.7 18.3l2.59-2.59\"></path></svg>";
    public static final String WEAKNESS_ICON = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"28\" height=\"28\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" class=\"lucide lucide-trending-down\" aria-hidden=\"true\"><path d=\"M16 17h6v-6\"></path><path d=\"m22 17-8.5-8.5-5 5L2 7\"></path></svg>";
    public static final String POISON_ICON = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"28\" height=\"28\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" class=\"lucide lucide-biohazard\" aria-hidden=\"true\"><circle cx=\"12\" cy=\"11.9\" r=\"2\"></circle><path d=\"M6.7 3.4c-.9 2.5 0 5.2 2.2 6.7C6.5 9 3.7 9.6 2 11.6\"></path><path d=\"m8.9 10.1 1.4.8\"></path><path d=\"M17.3 3.4c.9 2.5 0 5.2-2.2 6.7 2.4-1.2 5.2-.6 6.9 1.5\"></path><path d=\"m15.1 10.1-1.4.8\"></path><path d=\"M16.7 20.8c-2.6-.4-4.6-2.6-4.7-5.3-.2 2.6-2.1 4.8-4.7 5.2\"></path><path d=\"M12 13.9v1.6\"></path><path d=\"M13.5 5.4c-1-.2-2-.2-3 0\"></path><path d=\"M17 16.4c.7-.7 1.2-1.6 1.5-2.5\"></path><path d=\"M5.5 13.9c.3.9.8 1.8 1.5 2.5\"></path></svg>";
    public static final String WITHER_ICON = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"28\" height=\"28\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" class=\"lucide lucide-skull\" aria-hidden=\"true\"><path d=\"m12.5 17-.5-1-.5 1h1z\"></path><path d=\"M15 22a1 1 0 0 0 1-1v-1a2 2 0 0 0 1.56-3.25 8 8 0 1 0-11.12 0A2 2 0 0 0 8 20v1a1 1 0 0 0 1 1z\"></path><circle cx=\"15\" cy=\"12\" r=\"1\"></circle><circle cx=\"9\" cy=\"12\" r=\"1\"></circle></svg>";
    public static final String ABSORPTION_ICON = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"28\" height=\"28\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" class=\"lucide lucide-shield\" aria-hidden=\"true\"><path d=\"M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z\"></path></svg>";

    public static final String CONFIG_FOLDER_ICON = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" class=\"lucide lucide-folder\" aria-hidden=\"true\"><path d=\"M4 20h16a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.93a2 2 0 0 1-1.66-.9l-.82-1.2A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13c0 1.1.9 2 2 2Z\"/></svg>";
    public static final String CONFIG_DOWNLOAD_ICON = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" class=\"lucide lucide-download\" aria-hidden=\"true\"><path d=\"M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4\"/><polyline points=\"7 10 12 15 17 10\"/><line x1=\"12\" y1=\"15\" x2=\"12\" y2=\"3\"/></svg>";
    public static final String CONFIG_UPLOAD_ICON = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" class=\"lucide lucide-upload\" aria-hidden=\"true\"><path d=\"M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4\"/><polyline points=\"17 8 12 3 7 8\"/><line x1=\"12\" y1=\"3\" x2=\"12\" y2=\"15\"/></svg>";
    public static final String CONFIG_SAVE_ICON = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" class=\"lucide lucide-save\" aria-hidden=\"true\"><path d=\"M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z\"/><polyline points=\"17 21 17 13 7 13 7 21\"/><polyline points=\"7 3 7 8 15 8\"/></svg>";
    public static final String CONFIG_TRASH_ICON = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" class=\"lucide lucide-trash-2\" aria-hidden=\"true\"><polyline points=\"3 6 5 6 21 6\"/><path d=\"M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2\"/><line x1=\"10\" y1=\"11\" x2=\"10\" y2=\"17\"/><line x1=\"14\" y1=\"11\" x2=\"14\" y2=\"17\"/></svg>";
    public static final String REFRESH_ICON = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" class=\"lucide lucide-refresh-cw\" aria-hidden=\"true\"><path d=\"M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8\"/><path d=\"M21 3v5h-5\"/><path d=\"M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16\"/><path d=\"M8 16H3v5\"/></svg>";
    public static final String KEYBOARD_ICON = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><rect width=\"20\" height=\"16\" x=\"2\" y=\"4\" rx=\"2\" ry=\"2\"/><path d=\"M6 8h.001\"/><path d=\"M10 8h.001\"/><path d=\"M14 8h.001\"/><path d=\"M18 8h.001\"/><path d=\"M8 12h.001\"/><path d=\"M12 12h.001\"/><path d=\"M16 12h.001\"/><path d=\"M7 16h10\"/></svg>";


    public static void drawSvg(Canvas canvas, String svgContent, float x, float y, float size, Paint paint) {
        SVGDOM dom = SVG_CACHE.computeIfAbsent(svgContent, k -> new SVGDOM(Data.makeFromBytes(k.getBytes())));

        SVGSVG root = dom.getRoot();
        if (root == null) return;

        // Default to 24x24 if viewBox is missing
        float width = 24;
        float height = 24;

        Rect viewBox = root.getViewBox();
        if (viewBox != null) {
            width = viewBox.getWidth();
            height = viewBox.getHeight();
        }

        canvas.save();
        canvas.translate(x, y);

        float scale = size / Math.max(width, height);
        canvas.scale(scale, scale);
        canvas.translate(-width / 2f, -height / 2f); // Center

        if (paint != null) {
            try (Paint tintPaint = new Paint()) {
                tintPaint.setColorFilter(ColorFilter.makeBlend(paint.getColor(), BlendMode.SRC_IN));
                tintPaint.setAlpha(paint.getAlpha());
                // Expand bounds to account for stroke width
                canvas.saveLayer(Rect.makeXYWH(-4, -4, width + 8, height + 8), tintPaint);
                dom.render(canvas);
                canvas.restore();
            }
        } else {
            dom.render(canvas);
        }

        canvas.restore();
    }

    public static void drawSvgTopLeft(Canvas canvas, String svgContent, float left, float top, float size, Paint paint) {
        SVGDOM dom = SVG_CACHE.computeIfAbsent(svgContent, k -> new SVGDOM(Data.makeFromBytes(k.getBytes())));
        SVGSVG root = dom.getRoot();
        if (root == null) return;

        float width = 24;
        float height = 24;
        Rect viewBox = root.getViewBox();
        if (viewBox != null) {
            width = viewBox.getWidth();
            height = viewBox.getHeight();
        }

        canvas.save();
        canvas.translate(left, top);

        float scale = size / Math.max(width, height);
        canvas.scale(scale, scale);

        if (paint != null) {
            try (Paint tintPaint = new Paint()) {
                tintPaint.setColorFilter(ColorFilter.makeBlend(paint.getColor(), BlendMode.SRC_IN));
                tintPaint.setAlpha(paint.getAlpha());
                // Expand bounds to account for stroke width
                canvas.saveLayer(Rect.makeXYWH(-4, -4, width + 8, height + 8), tintPaint);
                dom.render(canvas);
                canvas.restore();
            }
        } else {
            dom.render(canvas);
        }

        canvas.restore();
    }

    public static void drawSnowflake(Canvas canvas, float x, float y, float radius, Paint paint) {
        canvas.save();
        canvas.translate(x, y);
        for (int i = 0; i < 6; i++) {
            canvas.drawLine(0, 0, 0, -radius, paint);
            float branchDist = radius * 0.6f;
            float branchSize = radius * 0.35f;
            canvas.drawLine(0, -branchDist, -branchSize, -branchDist - branchSize * 0.5f, paint);
            canvas.drawLine(0, -branchDist, branchSize, -branchDist - branchSize * 0.5f, paint);
            canvas.rotate(60);
        }
        canvas.restore();
    }

    public static void drawSnail(Canvas canvas, float x, float y, float radius, Paint paint) {
        drawSvg(canvas, SLOWNESS_ICON, x, y, radius * 2, paint);
    }

    public static void drawEffect(Canvas canvas, MobEffect effect, float x, float y, float size, Paint paint) {
        String svg = null;
        if (effect == MobEffects.SPEED.value()) svg = SPEED_ICON;
        else if (effect == MobEffects.SLOWNESS.value()) svg = SLOWNESS_ICON;
        else if (effect == MobEffects.HASTE.value()) svg = HASTE_ICON;
        else if (effect == MobEffects.STRENGTH.value()) svg = STRENGTH_ICON;
        else if (effect == MobEffects.INSTANT_HEALTH.value()) svg = HEALTH_ICON;
        else if (effect == MobEffects.JUMP_BOOST.value()) svg = HEALTH_ICON;
        else if (effect == MobEffects.NAUSEA.value()) svg = NAUSEA_ICON;
        else if (effect == MobEffects.REGENERATION.value()) svg = REGENERATION_ICON;
        else if (effect == MobEffects.RESISTANCE.value()) svg = RESISTANCE_ICON;
        else if (effect == MobEffects.FIRE_RESISTANCE.value()) svg = FIRE_RESISTANCE_ICON;
        else if (effect == MobEffects.INVISIBILITY.value()) svg = INVISIBILITY_ICON;
        else if (effect == MobEffects.BLINDNESS.value()) svg = BLINDNESS_ICON;
        else if (effect == MobEffects.NIGHT_VISION.value()) svg = NIGHT_VISION_ICON;
        else if (effect == MobEffects.HUNGER.value()) svg = HUNGER_ICON;
        else if (effect == MobEffects.WEAKNESS.value()) svg = WEAKNESS_ICON;
        else if (effect == MobEffects.POISON.value()) svg = POISON_ICON;
        else if (effect == MobEffects.WITHER.value()) svg = WITHER_ICON;
        else if (effect == MobEffects.HEALTH_BOOST.value()) svg = HEALTH_ICON;
        else if (effect == MobEffects.SATURATION.value()) svg = HUNGER_ICON;
        else if (effect == MobEffects.ABSORPTION.value()) svg = ABSORPTION_ICON;

        if (svg != null) {
            drawSvg(canvas, svg, x, y, size, paint);
        }
    }
}

