package cc.nimbus.ui.clickgui.component;

import cc.nimbus.features.module.api.setting.Setting;
import cc.nimbus.features.module.api.setting.impl.BooleanSetting;
import cc.nimbus.features.module.api.setting.impl.ColorSetting;
import cc.nimbus.features.module.api.setting.impl.FoldSetting;
import cc.nimbus.features.module.api.setting.impl.ModeSetting;
import cc.nimbus.features.module.api.setting.impl.MultiSelectSetting;
import cc.nimbus.features.module.api.setting.impl.NumberSetting;
import cc.nimbus.features.module.api.setting.impl.StringSetting;
import cc.nimbus.features.module.api.setting.impl.TextSetting;
import cc.nimbus.ui.clickgui.input.FocusManager;
import cc.nimbus.ui.clickgui.input.MultilineTextInput;
import cc.nimbus.ui.clickgui.input.TextInput;
import cc.nimbus.ui.clickgui.theme.ClickGuiTheme;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.types.Point;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.PreeditEvent;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SettingControls {
    private static final float ROW_HEIGHT = 30f;
    private static final float GAP = 4f;
    private static final float OPTION_HEIGHT = 23f;
    private static final float COLOR_AREA_HEIGHT = 124f;

    private final FocusManager focus;
    private final TextInput editor;
    private final MultilineTextInput multilineEditor;
    private final List<Hit> hits = new ArrayList<>();
    private final Map<Setting<?>, Float> animations = new IdentityHashMap<>();
    private Setting<?> editing;
    private Setting<?> openOptions;
    private NumberSetting draggingNumber;
    private ColorSetting draggingColor;
    private ColorSetting openColor;
    private HitKind colorDragKind;
    private UiRect hitClip;

    public SettingControls(FocusManager focus) {
        this.focus = focus;
        editor = new TextInput(Minecraft.getInstance().font, 1024);
        multilineEditor = new MultilineTextInput(Minecraft.getInstance().font, 8192);
    }

    public void beginFrame() {
        hits.clear();
    }

    public float height(List<Setting<?>> settings, float width) {
        float total = 0f;
        for (Setting<?> setting : settings) {
            if (setting.isHidden()) continue;
            total += settingHeight(setting, width) + GAP;
        }
        return Math.max(0f, total - GAP);
    }

    public float render(UiContext context, List<Setting<?>> settings, float x, float y, float width) {
        return render(context, settings, x, y, width, null);
    }

    public float render(UiContext context, List<Setting<?>> settings, float x, float y, float width,
                        UiRect clipBounds) {
        UiRect previousClip = hitClip;
        hitClip = clipBounds;
        float currentY = y;
        try {
            for (Setting<?> setting : settings) {
                if (setting.isHidden()) continue;
                float height = settingHeight(setting, width);
                UiRect bounds = new UiRect(x, currentY, width, height);
                if (clipBounds == null || bounds.intersects(clipBounds)) renderSetting(context, setting, bounds);
                currentY += height + GAP;
            }
        } finally {
            hitClip = previousClip;
        }
        return currentY - y;
    }

    public boolean mouseClicked(float x, float y, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        Hit hit = findHit(x, y);
        if (hit == null) {
            commitEditor();
            return false;
        }
        Setting<?> setting = hit.setting();
        switch (hit.kind()) {
            case BOOLEAN -> ((BooleanSetting) setting).setValue(!((BooleanSetting) setting).getValue());
            case FOLD -> ((FoldSetting) setting).setValue(!((FoldSetting) setting).getValue());
            case NUMBER_TRACK -> {
                draggingNumber = (NumberSetting) setting;
                updateNumber(draggingNumber, x, hit.bounds());
            }
            case NUMBER_VALUE -> beginEditing(setting);
            case MODE -> openOptions = openOptions == setting ? null : setting;
            case MODE_OPTION -> {
                ((ModeSetting) setting).setValue(hit.option());
                openOptions = null;
            }
            case MULTI -> openOptions = openOptions == setting ? null : setting;
            case MULTI_OPTION -> ((MultiSelectSetting) setting).toggle(hit.option());
            case COLOR -> {
                ColorSetting color = (ColorSetting) setting;
                if (openColor != null && openColor != color) openColor.setExpanded(false);
                color.setExpanded(!color.isExpanded());
                openColor = color.isExpanded() ? color : null;
                if (color.isExpanded()) openOptions = null;
            }
            case COLOR_SB, COLOR_HUE, COLOR_ALPHA -> {
                draggingColor = (ColorSetting) setting;
                colorDragKind = hit.kind();
                updateColor(draggingColor, colorDragKind, x, y, hit.bounds());
            }
            case COLOR_RAINBOW -> ((ColorSetting) setting).setRainbow(!((ColorSetting) setting).isRainbow());
            case TEXT -> {
                beginEditing(setting);
                if (setting instanceof TextSetting text && text.isMultiline()) {
                    multilineEditor.placeCursor(hit.bounds(), x, y);
                }
            }
        }
        return true;
    }

    public void mouseMoved(float x, float y, boolean leftDown) {
        if (!leftDown) {
            draggingNumber = null;
            draggingColor = null;
            colorDragKind = null;
            return;
        }
        if (draggingNumber != null) {
            Hit hit = hits.stream()
                    .filter(value -> value.setting() == draggingNumber && value.kind() == HitKind.NUMBER_TRACK)
                    .findFirst().orElse(null);
            if (hit != null) updateNumber(draggingNumber, x, hit.bounds());
        }
        if (draggingColor != null && colorDragKind != null) {
            Hit hit = hits.stream()
                    .filter(value -> value.setting() == draggingColor && value.kind() == colorDragKind)
                    .findFirst().orElse(null);
            if (hit != null) updateColor(draggingColor, colorDragKind, x, y, hit.bounds());
        }
    }

    public void mouseReleased() {
        draggingNumber = null;
        draggingColor = null;
        colorDragKind = null;
    }

    public boolean keyPressed(KeyEvent event) {
        if (editing == null) return false;
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            cancelEditor();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            commitEditor();
            return true;
        }
        return editing instanceof TextSetting text && text.isMultiline()
                ? multilineEditor.keyPressed(event) : editor.keyPressed(event);
    }

    public boolean charTyped(CharacterEvent event) {
        if (editing instanceof TextSetting text && text.isMultiline()) return multilineEditor.charTyped(event);
        return editing != null && editor.charTyped(event);
    }

    public boolean preeditUpdated(PreeditEvent event) {
        if (editing instanceof TextSetting text && text.isMultiline()) return multilineEditor.preeditUpdated(event);
        return editing != null && editor.preeditUpdated(event);
    }

    public boolean editing() {
        return editing != null;
    }

    public void cancelEditor() {
        editing = null;
        multilineEditor.focused(false);
        focus.clear();
    }

    public void commitEditor() {
        Setting<?> target = editing;
        if (target == null) return;
        String value = target instanceof TextSetting text && text.isMultiline()
                ? multilineEditor.value() : editor.value();
        if (target instanceof NumberSetting number) {
            try {
                number.setValue(Double.parseDouble(value.trim()));
            } catch (NumberFormatException ignored) {
            }
        } else if (target instanceof TextSetting text) {
            text.setValue(value);
        } else if (target instanceof StringSetting string) {
            string.setValue(value);
        }
        editing = null;
        multilineEditor.focused(false);
        focus.clear();
    }

    public void clearTransient() {
        cancelEditor();
        openOptions = null;
        draggingNumber = null;
        if (openColor != null) openColor.setExpanded(false);
        openColor = null;
        draggingColor = null;
        colorDragKind = null;
    }

    public boolean cancelTransient() {
        boolean active = editing != null || openOptions != null || openColor != null
                || draggingNumber != null || draggingColor != null;
        if (active) clearTransient();
        return active;
    }

    private void renderSetting(UiContext context, Setting<?> setting, UiRect bounds) {
        context.frame().draw().roundedRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 3f,
                ClickGuiTheme.CONTROL);
        context.frame().draw().outline(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 3f, 1f,
                ClickGuiTheme.STROKE);
        UiRect row = new UiRect(bounds.x(), bounds.y(), bounds.width(), ROW_HEIGHT);
        context.textIn(ellipsize(context, setting.getName(), bounds.width() * 0.55f),
                new UiRect(bounds.x() + 7f, bounds.y(), bounds.width() * 0.55f, ROW_HEIGHT),
                11f, ClickGuiTheme.TEXT);

        if (setting instanceof BooleanSetting value) renderBoolean(context, value, row);
        else if (setting instanceof FoldSetting value) renderFold(context, value, row);
        else if (setting instanceof NumberSetting value) renderNumber(context, value, row);
        else if (setting instanceof ModeSetting value) renderMode(context, value, bounds);
        else if (setting instanceof MultiSelectSetting value) renderMulti(context, value, bounds);
        else if (setting instanceof ColorSetting value) renderColor(context, value, bounds);
        else if (setting instanceof TextSetting value && value.isMultiline()) renderMultilineText(context, value, bounds);
        else if (setting instanceof TextSetting value) renderText(context, value, row, value.isSecure());
        else if (setting instanceof StringSetting value) renderText(context, value, row, false);
        else {
            context.textIn("Unsupported: " + setting.getClass().getSimpleName(),
                    new UiRect(bounds.x() + 7f, bounds.y() + ROW_HEIGHT, bounds.width() - 14f, 22f),
                    10f, ClickGuiTheme.DANGER);
        }
    }

    private void renderBoolean(UiContext context, BooleanSetting setting, UiRect row) {
        UiRect toggle = new UiRect(row.x() + row.width() - 32f, row.y() + 8f, 24f, 14f);
        float speed = 1f - (float) Math.exp(-context.deltaSeconds() * 14f);
        float progress = animations.getOrDefault(setting, setting.getValue() ? 1f : 0f);
        progress += ((setting.getValue() ? 1f : 0f) - progress) * speed;
        animations.put(setting, progress);
        context.frame().draw().roundedRect(toggle.x(), toggle.y(), toggle.width(), toggle.height(), 7f,
                progress > .01f ? context.theme().accent(Math.round(0x20 + 0x35 * progress)) : 0x35000000);
        float knobX = toggle.x() + 2f + 10f * progress;
        context.frame().draw().roundedRect(knobX, toggle.y() + 2f, 10f, 10f, 5f, 0xDFFFFFFF);
        addHit(new Hit(setting, HitKind.BOOLEAN, row, null));
    }

    private void renderFold(UiContext context, FoldSetting setting, UiRect row) {
        String value = setting.getValue() ? "-" : "+";
        context.centered(value, new UiRect(row.x() + row.width() - 28f, row.y(), 22f, row.height()),
                14f, ClickGuiTheme.TEXT);
        addHit(new Hit(setting, HitKind.FOLD, row, null));
    }

    private void renderNumber(UiContext context, NumberSetting setting, UiRect row) {
        UiRect valueBounds = new UiRect(row.x() + row.width() - 58f, row.y() + 4f, 50f, 21f);
        if (editing == setting) {
            editor.render(context, valueBounds, "0", 10.5f, false);
        } else {
            context.centered(format(setting.getValue()), valueBounds, 10.5f, ClickGuiTheme.MUTED);
        }
        UiRect track = new UiRect(row.x() + 7f, row.y() + row.height() - 5f, row.width() - 14f, 2f);
        double min = setting.getMinimum().doubleValue();
        double max = setting.getMaximum().doubleValue();
        float progress = max <= min ? 0f : (float) ((setting.getValue().doubleValue() - min) / (max - min));
        context.frame().draw().rect(track.x(), track.y(), track.width(), track.height(), 0x28FFFFFF);
        context.frame().draw().rect(track.x(), track.y(), track.width() * Math.clamp(progress, 0f, 1f),
                track.height(), context.theme().accent(0x55));
        addHit(new Hit(setting, HitKind.NUMBER_TRACK,
                new UiRect(track.x(), row.y() + 16f, track.width(), 14f), null));
        addHit(new Hit(setting, HitKind.NUMBER_VALUE, valueBounds, null));
    }

    private void renderMode(UiContext context, ModeSetting setting, UiRect bounds) {
        UiRect value = new UiRect(bounds.x() + bounds.width() * 0.56f, bounds.y() + 5f,
                bounds.width() * 0.44f - 7f, 20f);
        context.centered(ellipsize(context, setting.getValue(), value.width() - 8f), value,
                10f, ClickGuiTheme.MUTED);
        addHit(new Hit(setting, HitKind.MODE, new UiRect(bounds.x(), bounds.y(), bounds.width(), ROW_HEIGHT), null));
        if (openOptions != setting) return;
        float y = bounds.y() + ROW_HEIGHT;
        for (String option : setting.getModes()) {
            UiRect optionBounds = new UiRect(bounds.x() + 5f, y, bounds.width() - 10f, OPTION_HEIGHT);
            if (setting.is(option)) {
                context.frame().draw().roundedRect(optionBounds.x(), optionBounds.y(), optionBounds.width(),
                        optionBounds.height(), 2f, context.theme().accent(0x32));
            }
            context.textIn(option, new UiRect(optionBounds.x() + 5f, optionBounds.y(), optionBounds.width() - 10f,
                    optionBounds.height()), 10.5f, setting.is(option) ? ClickGuiTheme.TEXT : ClickGuiTheme.MUTED);
            addHit(new Hit(setting, HitKind.MODE_OPTION, optionBounds, option));
            y += OPTION_HEIGHT;
        }
    }

    private void renderMulti(UiContext context, MultiSelectSetting setting, UiRect bounds) {
        String summary = setting.getValue().isEmpty() ? "None" : String.join(", ", setting.getValue());
        UiRect value = new UiRect(bounds.x() + bounds.width() * 0.56f, bounds.y() + 5f,
                bounds.width() * 0.44f - 7f, 20f);
        context.centered(ellipsize(context, summary, value.width() - 8f), value, 10f, ClickGuiTheme.MUTED);
        addHit(new Hit(setting, HitKind.MULTI, new UiRect(bounds.x(), bounds.y(), bounds.width(), ROW_HEIGHT), null));
        if (openOptions != setting) return;
        float y = bounds.y() + ROW_HEIGHT;
        for (String option : setting.getModes()) {
            boolean selected = setting.isSelected(option);
            UiRect optionBounds = new UiRect(bounds.x() + 5f, y, bounds.width() - 10f, OPTION_HEIGHT);
            if (selected) {
                context.frame().draw().roundedRect(optionBounds.x(), optionBounds.y(), optionBounds.width(),
                        optionBounds.height(), 2f, context.theme().accent(0x32));
            }
            context.textIn(option, new UiRect(optionBounds.x() + 5f, optionBounds.y(), optionBounds.width() - 10f,
                    optionBounds.height()), 10.5f, selected ? ClickGuiTheme.TEXT : ClickGuiTheme.MUTED);
            addHit(new Hit(setting, HitKind.MULTI_OPTION, optionBounds, option));
            y += OPTION_HEIGHT;
        }
    }

    private void renderColor(UiContext context, ColorSetting setting, UiRect bounds) {
        UiRect swatch = new UiRect(bounds.x() + bounds.width() - 29f, bounds.y() + 7f, 21f, 16f);
        context.frame().draw().roundedRect(swatch.x(), swatch.y(), swatch.width(), swatch.height(), 3f,
                setting.isRainbow() ? setting.getRainbow(3200, 0) : setting.getValue());
        context.frame().draw().outline(swatch.x(), swatch.y(), swatch.width(), swatch.height(), 3f, 1f,
                ClickGuiTheme.STROKE);
        addHit(new Hit(setting, HitKind.COLOR, new UiRect(bounds.x(), bounds.y(), bounds.width(), ROW_HEIGHT), null));
        if (!setting.isExpanded()) return;

        float pickerY = bounds.y() + ROW_HEIGHT + 2f;
        UiRect saturationBrightness = new UiRect(bounds.x() + 7f, pickerY, bounds.width() - 14f, 60f);
        drawSaturationBrightness(context, saturationBrightness, setting.getHue());
        UiRect hue = new UiRect(saturationBrightness.x(), pickerY + 66f, saturationBrightness.width(), 8f);
        drawHue(context, hue);
        UiRect alpha = new UiRect(saturationBrightness.x(), pickerY + 80f, saturationBrightness.width(), 8f);
        drawAlpha(context, alpha, setting);
        addHit(new Hit(setting, HitKind.COLOR_SB, saturationBrightness, null));
        addHit(new Hit(setting, HitKind.COLOR_HUE, hue, null));
        addHit(new Hit(setting, HitKind.COLOR_ALPHA, alpha, null));
        UiRect rainbow = new UiRect(saturationBrightness.x(), pickerY + 94f, saturationBrightness.width(), 20f);
        if (setting.isRainbow()) {
            context.frame().draw().roundedRect(rainbow.x(), rainbow.y(), rainbow.width(), rainbow.height(), 2f,
                    context.theme().accent(0x32));
        }
        context.textIn("Rainbow", new UiRect(rainbow.x() + 5f, rainbow.y(), rainbow.width() - 10f,
                rainbow.height()), 10f, setting.isRainbow() ? ClickGuiTheme.TEXT : ClickGuiTheme.MUTED);
        addHit(new Hit(setting, HitKind.COLOR_RAINBOW, rainbow, null));
    }

    private void renderText(UiContext context, Setting<String> setting, UiRect row, boolean secure) {
        UiRect input = new UiRect(row.x() + row.width() * 0.47f, row.y() + 4f,
                row.width() * 0.53f - 7f, 22f);
        if (editing == setting) {
            String placeholder = setting instanceof TextSetting text && text.getPlaceholder() != null
                    ? text.getPlaceholder() : "Value";
            editor.render(context, input, placeholder, 10.5f, secure);
        } else {
            String value = setting.getValue();
            if (secure && value != null) value = "*".repeat(value.length());
            context.centered(ellipsize(context, value == null || value.isEmpty() ? "Empty" : value,
                    input.width() - 8f), input, 10f, ClickGuiTheme.MUTED);
        }
        addHit(new Hit(setting, HitKind.TEXT, input, null));
    }

    private void renderMultilineText(UiContext context, TextSetting setting, UiRect bounds) {
        UiRect input = new UiRect(bounds.x() + 7f, bounds.y() + ROW_HEIGHT,
                bounds.width() - 14f, bounds.height() - ROW_HEIGHT - 7f);
        if (editing == setting) {
            multilineEditor.render(context, input,
                    setting.getPlaceholder() == null ? "Value" : setting.getPlaceholder(), 10.5f, setting.isSecure());
        } else {
            String value = setting.getValue();
            if (setting.isSecure() && value != null) value = "*".repeat(value.length());
            context.text(value == null || value.isEmpty() ? "Empty" : value.replace('\n', ' '),
                    input.x() + 7f, input.y() + 7f, 10.5f, ClickGuiTheme.MUTED);
        }
        addHit(new Hit(setting, HitKind.TEXT, input, null));
    }

    private void beginEditing(Setting<?> setting) {
        commitEditor();
        editing = setting;
        if (setting instanceof TextSetting text && text.isMultiline()) {
            multilineEditor.maximumLength(text.getSize() > 0 ? text.getSize() : 8192);
            multilineEditor.value(String.valueOf(setting.getValue() == null ? "" : setting.getValue()));
            multilineEditor.focused(true);
            focus.clear();
        } else {
            editor.maximumLength(setting instanceof TextSetting text && text.getSize() > 0
                    ? text.getSize() : setting instanceof NumberSetting ? 64 : 1024);
            if (setting instanceof NumberSetting number) editor.value(format(number.getValue()));
            else editor.value(String.valueOf(setting.getValue() == null ? "" : setting.getValue()));
            focus.focus(editor);
        }
    }

    private float settingHeight(Setting<?> setting, float width) {
        if (setting instanceof ModeSetting mode && openOptions == setting) {
            return ROW_HEIGHT + mode.getModes().length * OPTION_HEIGHT + 2f;
        }
        if (setting instanceof MultiSelectSetting multi && openOptions == setting) {
            return ROW_HEIGHT + multi.getModes().length * OPTION_HEIGHT + 2f;
        }
        if (setting instanceof ColorSetting color && color.isExpanded()) {
            return ROW_HEIGHT + COLOR_AREA_HEIGHT;
        }
        if (setting instanceof TextSetting text && text.isMultiline()) return 96f;
        if (!(setting instanceof BooleanSetting || setting instanceof FoldSetting || setting instanceof NumberSetting
                || setting instanceof ModeSetting || setting instanceof MultiSelectSetting
                || setting instanceof ColorSetting || setting instanceof StringSetting || setting instanceof TextSetting)) {
            return 54f;
        }
        return ROW_HEIGHT;
    }

    private Hit findHit(float x, float y) {
        for (int index = hits.size() - 1; index >= 0; index--) {
            Hit hit = hits.get(index);
            if (hit.bounds().contains(x, y) && (hit.clip() == null || hit.clip().contains(x, y))) return hit;
        }
        return null;
    }

    private void addHit(Hit hit) {
        if (hitClip == null || hit.bounds().intersects(hitClip)) {
            hits.add(new Hit(hit.setting(), hit.kind(), hit.bounds(), hit.option(), hitClip));
        }
    }

    private static void updateNumber(NumberSetting setting, float mouseX, UiRect track) {
        double progress = Math.clamp((mouseX - track.x()) / Math.max(1f, track.width()), 0f, 1f);
        double min = setting.getMinimum().doubleValue();
        double max = setting.getMaximum().doubleValue();
        setting.setValue(min + (max - min) * progress);
    }

    private static void updateColor(ColorSetting setting, HitKind kind, float mouseX, float mouseY, UiRect bounds) {
        float x = Math.clamp((mouseX - bounds.x()) / Math.max(1f, bounds.width()), 0f, 1f);
        float y = Math.clamp((mouseY - bounds.y()) / Math.max(1f, bounds.height()), 0f, 1f);
        switch (kind) {
            case COLOR_SB -> setting.setHSB(setting.getHue(), x, 1f - y);
            case COLOR_HUE -> setting.setHue(x);
            case COLOR_ALPHA -> setting.setAlphaValue(Math.round(x * 255f));
            default -> {
            }
        }
    }

    private static void drawSaturationBrightness(UiContext context, UiRect bounds, float hue) {
        int hueColor = Color.HSBtoRGB(hue, 1f, 1f);
        context.frame().draw().roundedRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 2f, hueColor);
        try (Paint horizontal = new Paint().setAntiAlias(true);
             Paint vertical = new Paint().setAntiAlias(true)) {
            horizontal.setShader(context.frame().effects().linearGradient(
                    new Point(bounds.x(), bounds.y()), new Point(bounds.x() + bounds.width(), bounds.y()),
                    new int[]{0xFFFFFFFF, 0x00FFFFFF}));
            vertical.setShader(context.frame().effects().linearGradient(
                    new Point(bounds.x(), bounds.y()), new Point(bounds.x(), bounds.y() + bounds.height()),
                    new int[]{0x00000000, 0xFF000000}));
            context.frame().draw().roundedRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 2f, horizontal);
            context.frame().draw().roundedRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 2f, vertical);
        }
    }

    private static void drawHue(UiContext context, UiRect bounds) {
        try (Paint paint = new Paint().setAntiAlias(true)) {
            paint.setShader(context.frame().effects().linearGradient(
                    new Point(bounds.x(), bounds.y()), new Point(bounds.x() + bounds.width(), bounds.y()),
                    new int[]{0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF,
                            0xFF0000FF, 0xFFFF00FF, 0xFFFF0000}));
            context.frame().draw().roundedRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 2f, paint);
        }
    }

    private static void drawAlpha(UiContext context, UiRect bounds, ColorSetting setting) {
        int opaque = 0xFF000000 | setting.getValue() & 0x00FFFFFF;
        try (Paint paint = new Paint().setAntiAlias(true)) {
            paint.setShader(context.frame().effects().linearGradient(
                    new Point(bounds.x(), bounds.y()), new Point(bounds.x() + bounds.width(), bounds.y()),
                    new int[]{opaque & 0x00FFFFFF, opaque}));
            context.frame().draw().roundedRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 2f, paint);
        }
    }

    private static String format(Number value) {
        if (value == null) return "0";
        double number = value.doubleValue();
        if (Math.abs(number - Math.rint(number)) < 1e-8) return Long.toString(Math.round(number));
        return String.format(Locale.ROOT, "%.2f", number).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static String ellipsize(UiContext context, String value, float width) {
        if (value == null) return "";
        if (context.frame().text().width(UiContext.FONT, 10.5f, value) <= width) return value;
        String result = value;
        while (!result.isEmpty()
                && context.frame().text().width(UiContext.FONT, 10.5f, result + "...") > width) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "...";
    }

    private enum HitKind {
        BOOLEAN,
        FOLD,
        NUMBER_TRACK,
        NUMBER_VALUE,
        MODE,
        MODE_OPTION,
        MULTI,
        MULTI_OPTION,
        COLOR,
        COLOR_SB,
        COLOR_HUE,
        COLOR_ALPHA,
        COLOR_RAINBOW,
        TEXT
    }

    private record Hit(Setting<?> setting, HitKind kind, UiRect bounds, String option, UiRect clip) {
        private Hit(Setting<?> setting, HitKind kind, UiRect bounds, String option) {
            this(setting, kind, bounds, option, null);
        }
    }
}
