package cc.nimbus.ui.clickgui.component;

import cc.nimbus.features.module.api.setting.Setting;
import cc.nimbus.features.module.api.setting.impl.BooleanSetting;
import cc.nimbus.features.module.api.setting.impl.ColorSetting;
import cc.nimbus.features.module.api.setting.impl.FoldSetting;
import cc.nimbus.features.module.api.setting.impl.ModeSetting;
import cc.nimbus.features.module.api.setting.impl.MultiSelectSetting;
import cc.nimbus.features.module.api.setting.impl.NumberSetting;
import cc.nimbus.features.module.api.setting.impl.TextSetting;
import cc.nimbus.ui.clickgui.ClickGuiIcons;
import cc.nimbus.ui.clickgui.input.FocusManager;
import cc.nimbus.ui.clickgui.input.MultilineTextInput;
import cc.nimbus.ui.clickgui.input.TextInput;
import cc.nimbus.ui.clickgui.theme.ClickGuiTheme;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.types.Point;
import io.github.humbleui.types.Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import cc.nimbus.ui.input.PreeditEvent;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class SettingControls {
    private static final float ROW_HEIGHT = 32f;
    private static final float NUMBER_HEIGHT = 42f;
    private static final float STACKED_TEXT_HEADER_HEIGHT = 26f;
    private static final float STACKED_TEXT_HEIGHT = 63f;
    private static final float STACKED_TEXT_THRESHOLD = 220f;
    private static final float MULTILINE_TEXT_HEIGHT = 132f;
    private static final float GAP = 2f;
    private static final float OPTION_HEIGHT = 26f;
    private static final float COLOR_AREA_HEIGHT = 124f;
    private static final float COLOR_PICKER_HEIGHT = 100f;

    private final FocusManager focus;
    private final TextInput editor;
    private final MultilineTextInput multilineEditor;
    private final List<Hit> hits = new ArrayList<>();
    private final Map<Setting<?>, Float> animations = new IdentityHashMap<>();
    private final Map<Setting<?>, Float> hoverAnimations = new IdentityHashMap<>();
    private final Map<Setting<?>, Float> focusAnimations = new IdentityHashMap<>();
    private final Map<Setting<?>, Float> valueHoverAnimations = new IdentityHashMap<>();
    private final Map<Setting<?>, Float> dragAnimations = new IdentityHashMap<>();
    private final Map<NumberSetting, Double> displayedNumbers = new IdentityHashMap<>();
    private final Map<Setting<?>, Float> disclosureAnimations = new IdentityHashMap<>();
    private final Map<Setting<?>, Float> visibilityAnimations = new IdentityHashMap<>();
    private final Map<Setting<?>, MarqueeState> marqueeStates = new IdentityHashMap<>();
    private final Map<OptionKey, Float> optionAnimations = new java.util.HashMap<>();
    private final Set<Setting<?>> disclosuresUpdated = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Setting<?>> visibilityUpdated = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    private Setting<?> editing;
    private Setting<?> openOptions;
    private NumberSetting draggingNumber;
    private ColorSetting draggingColor;
    private ColorSetting openColor;
    private HitKind colorDragKind;
    private UiRect hitClip;
    private UiContext lastContext;
    private float disclosureSpeed = 1f;
    private boolean hitsEnabled = true;

    public SettingControls(FocusManager focus) {
        this.focus = focus;
        editor = new TextInput(Minecraft.getInstance().font, 1024);
        multilineEditor = new MultilineTextInput(Minecraft.getInstance().font, 8192);
    }

    public void beginFrame(float deltaSeconds) {
        hits.clear();
        disclosuresUpdated.clear();
        visibilityUpdated.clear();
        disclosureSpeed = 1f - (float) Math.exp(-deltaSeconds * 14f);
    }

    public float height(List<Setting<?>> settings, float width) {
        float total = 0f;
        for (Setting<?> setting : settings) {
            float visibility = visibility(setting);
            if (visibility <= .001f) continue;
            total += (settingHeight(setting, width) + GAP) * visibility;
        }
        return Math.max(0f, total);
    }

    public float render(UiContext context, List<Setting<?>> settings, float x, float y, float width) {
        return render(context, settings, x, y, width, null);
    }

    public float render(UiContext context, List<Setting<?>> settings, float x, float y, float width,
                        UiRect clipBounds) {
        lastContext = context;
        UiRect previousClip = hitClip;
        hitClip = clipBounds;
        float currentY = y;
        try {
            for (Setting<?> setting : settings) {
                float visibility = visibility(setting);
                if (visibility <= .001f) continue;
                float fullHeight = settingHeight(setting, width);
                float visibleHeight = fullHeight * visibility;
                UiRect bounds = new UiRect(x, currentY, width, fullHeight);
                UiRect visibleBounds = new UiRect(x, currentY, width, visibleHeight);
                if (clipBounds == null || visibleBounds.intersects(clipBounds)) {
                    int clip = context.clip(visibleBounds, 0f);
                    int layer = context.frame().canvas().saveLayerAlpha(
                            Rect.makeXYWH(visibleBounds.x(), visibleBounds.y(), visibleBounds.width(),
                                    visibleBounds.height()), Math.round(255f * visibility));
                    boolean previousHitsEnabled = hitsEnabled;
                    hitsEnabled = !setting.isHidden() && visibility >= 1f;
                    try {
                        renderSetting(context, setting, bounds);
                    } finally {
                        hitsEnabled = previousHitsEnabled;
                        context.frame().canvas().restoreToCount(layer);
                        context.restore(clip);
                    }
                }
                currentY += (fullHeight + GAP) * visibility;
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
                if (setting instanceof TextSetting text && text.isMultiline() && lastContext != null) {
                    multilineEditor.placeCursor(lastContext, hit.bounds(), 10.5f, x, y);
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

    public void finishInput() {
        commitEditor();
        draggingNumber = null;
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
        UiRect row = new UiRect(bounds.x(), bounds.y(), bounds.width(), ROW_HEIGHT);
        boolean stackedText = setting instanceof TextSetting text && !text.isMultiline()
                && bounds.width() < STACKED_TEXT_THRESHOLD;
        float headerHeight = stackedText ? STACKED_TEXT_HEADER_HEIGHT
                : setting instanceof NumberSetting ? NUMBER_HEIGHT : ROW_HEIGHT;
        float speed = 1f - (float) Math.exp(-context.deltaSeconds() * 13f);
        float hover = animate(hoverAnimations.getOrDefault(setting, 0f),
                new UiRect(bounds.x(), bounds.y(), bounds.width(), Math.min(bounds.height(), headerHeight))
                        .contains(context.mouseX(), context.mouseY()) ? 1f : 0f, speed);
        hoverAnimations.put(setting, hover);
        if (hover > .01f) {
            context.frame().draw().roundedRect(bounds.x(), bounds.y() + 1f, bounds.width(),
                    Math.min(bounds.height(), headerHeight) - 2f,
                    5f, alpha(ClickGuiTheme.HOVER, hover));
        }
        context.frame().draw().rect(bounds.x() + 7f, bounds.y() + bounds.height() - 1f,
                bounds.width() - 14f, 1f, 0x0CFFFFFF);
        float labelHeight = stackedText ? STACKED_TEXT_HEADER_HEIGHT
                : setting instanceof NumberSetting ? 28f : ROW_HEIGHT;
        float labelWidth = labelWidth(setting, bounds.width(), stackedText);
        context.textIn(ellipsize(context, translatedName(context, setting), labelWidth),
                new UiRect(bounds.x() + 8f, bounds.y(), labelWidth, labelHeight),
                11f, ClickGuiTheme.TEXT);

        if (setting instanceof BooleanSetting value) renderBoolean(context, value, row);
        else if (setting instanceof FoldSetting value) renderFold(context, value, row);
        else if (setting instanceof NumberSetting value) renderNumber(context, value, bounds);
        else if (setting instanceof ModeSetting value) renderMode(context, value, bounds);
        else if (setting instanceof MultiSelectSetting value) renderMulti(context, value, bounds);
        else if (setting instanceof ColorSetting value) renderColor(context, value, bounds);
        else if (setting instanceof TextSetting value && value.isMultiline()) renderMultilineText(context, value, bounds);
        else if (setting instanceof TextSetting value) renderText(context, value, bounds, value.isSecure());
        else {
            context.textIn(context.translate("nimbus.ui.unsupported", "Unsupported: %s",
                            setting.getClass().getSimpleName()),
                    new UiRect(bounds.x() + 7f, bounds.y() + ROW_HEIGHT, bounds.width() - 14f, 22f),
                    10f, ClickGuiTheme.DANGER);
        }
    }

    private void renderBoolean(UiContext context, BooleanSetting setting, UiRect row) {
        UiRect toggle = new UiRect(row.x() + row.width() - 38f, row.y() + 7f, 30f, 18f);
        float speed = 1f - (float) Math.exp(-context.deltaSeconds() * 14f);
        float progress = animations.getOrDefault(setting, setting.getValue() ? 1f : 0f);
        progress += ((setting.getValue() ? 1f : 0f) - progress) * speed;
        animations.put(setting, progress);
        context.frame().draw().roundedRect(toggle.x(), toggle.y(), toggle.width(), toggle.height(), 7f,
                mix(0x30FFFFFF, context.accentIn(toggle, 0xA0), progress));
        float knobX = toggle.x() + 2f + 12f * progress;
        context.frame().draw().roundedRect(knobX, toggle.y() + 2f, 14f, 14f, 7f,
                mix(0xFFC9CBD0, 0xFFFFFFFF, progress));
        addHit(new Hit(setting, HitKind.BOOLEAN, row, null));
    }

    private void renderFold(UiContext context, FoldSetting setting, UiRect row) {
        float speed = 1f - (float) Math.exp(-context.deltaSeconds() * 14f);
        float progress = animate(animations.getOrDefault(setting, setting.getValue() ? 1f : 0f),
                setting.getValue() ? 1f : 0f, speed);
        animations.put(setting, progress);
        iconAlpha(context, ClickGuiIcons.EXPAND_MORE, row.x() + row.width() - 26f,
                row.y() + 7f - progress, 18f, ClickGuiTheme.MUTED, 1f - progress);
        iconAlpha(context, ClickGuiIcons.EXPAND_LESS, row.x() + row.width() - 26f,
                row.y() + 8f - progress, 18f, ClickGuiTheme.MUTED, progress);
        addHit(new Hit(setting, HitKind.FOLD, row, null));
    }

    private void renderNumber(UiContext context, NumberSetting setting, UiRect bounds) {
        UiRect valueBounds = new UiRect(bounds.x() + bounds.width() - 58f, bounds.y() + 3f, 50f, 22f);
        float speed = 1f - (float) Math.exp(-context.deltaSeconds() * 15f);
        double targetValue = setting.getValue().doubleValue();
        double displayedValue = displayedNumbers.getOrDefault(setting, targetValue);
        float valueSpeed = 1f - (float) Math.exp(-context.deltaSeconds()
                * (draggingNumber == setting ? 32f : 16f));
        displayedValue += (targetValue - displayedValue) * valueSpeed;
        double snapDistance = Math.max(1e-7, setting.getIncrement().doubleValue() * .01);
        if (Math.abs(targetValue - displayedValue) <= snapDistance) displayedValue = targetValue;
        displayedNumbers.put(setting, displayedValue);
        float focused = animate(focusAnimations.getOrDefault(setting, 0f),
                editing == setting ? 1f : 0f, speed);
        focusAnimations.put(setting, focused);
        float valueHover = animate(valueHoverAnimations.getOrDefault(setting, 0f),
                valueBounds.contains(context.mouseX(), context.mouseY()) ? 1f : 0f, speed);
        valueHoverAnimations.put(setting, valueHover);
        int valueBackground = mix(0x00000000, 0x14FFFFFF, valueHover);
        valueBackground = mix(valueBackground, context.accentIn(valueBounds, 0x32), focused);
        int valueOutline = alpha(context.accentIn(valueBounds, 0x64), focused);
        context.frame().draw().roundedRect(valueBounds.x(), valueBounds.y(), valueBounds.width(),
                valueBounds.height(), 4f, valueBackground);
        context.frame().draw().outline(valueBounds.x(), valueBounds.y(), valueBounds.width(),
                valueBounds.height(), 4f, 1f, valueOutline);
        if (focused < .999f) {
            int valueLayer = context.frame().canvas().saveLayerAlpha(
                    Rect.makeXYWH(valueBounds.x(), valueBounds.y(), valueBounds.width(), valueBounds.height()),
                    Math.round(255f * (1f - focused)));
            try {
                context.centered(format(displayedValue), valueBounds, 10.5f, ClickGuiTheme.MUTED);
            } finally {
                context.frame().canvas().restoreToCount(valueLayer);
            }
        }
        if (focused > .001f) {
            int editorLayer = context.frame().canvas().saveLayerAlpha(
                    Rect.makeXYWH(valueBounds.x(), valueBounds.y(), valueBounds.width(), valueBounds.height()),
                    Math.round(255f * focused));
            try {
                editor.render(context, valueBounds, "0", 10.5f, false, valueBackground, valueOutline);
            } finally {
                context.frame().canvas().restoreToCount(editorLayer);
            }
        }
        UiRect track = new UiRect(bounds.x() + 8f, bounds.y() + bounds.height() - 9f,
                bounds.width() - 16f, 4f);
        double min = setting.getMinimum().doubleValue();
        double max = setting.getMaximum().doubleValue();
        float progress = max <= min ? 0f : (float) ((displayedValue - min) / (max - min));
        float clamped = Math.clamp(progress, 0f, 1f);
        float dragging = animate(dragAnimations.getOrDefault(setting, draggingNumber == setting ? 1f : 0f),
                draggingNumber == setting ? 1f : 0f, speed);
        dragAnimations.put(setting, dragging);
        context.frame().draw().roundedRect(track.x(), track.y(), track.width(), track.height(), 2f,
                mix(0x28FFFFFF, 0x38FFFFFF, dragging));
        context.frame().draw().roundedRect(track.x(), track.y(), track.width() * clamped,
                track.height(), 2f, context.accentAt(track.x() + track.width() * clamped, 0xB8));
        try (Paint thumb = new Paint().setAntiAlias(true).setColor(0xFFF3F4F6)) {
            context.frame().canvas().drawCircle(track.x() + track.width() * clamped,
                    track.y() + track.height() * .5f, 4f + 1.25f * dragging, thumb);
        }
        addHit(new Hit(setting, HitKind.NUMBER_TRACK,
                new UiRect(track.x(), track.y() - 6f, track.width(), 16f), null));
        addHit(new Hit(setting, HitKind.NUMBER_VALUE, valueBounds, null));
    }

    private void renderMode(UiContext context, ModeSetting setting, UiRect bounds) {
        UiRect value = new UiRect(bounds.x() + bounds.width() * 0.44f, bounds.y(),
                bounds.width() * 0.56f - 29f, ROW_HEIGHT);
        scrollingValueText(context, setting,
                translatedOption(context, setting, setting.getValue()), value, 10.5f, ClickGuiTheme.MUTED);
        float disclosure = disclosureAnimations.getOrDefault(setting, openOptions == setting ? 1f : 0f);
        disclosureIcon(context, bounds, disclosure);
        addHit(new Hit(setting, HitKind.MODE, new UiRect(bounds.x(), bounds.y(), bounds.width(), ROW_HEIGHT), null));
        if (disclosure <= .001f) return;
        int clip = context.clip(bounds, 0f);
        int layer = context.frame().canvas().saveLayerAlpha(
                Rect.makeXYWH(bounds.x(), bounds.y() + ROW_HEIGHT, bounds.width(),
                        Math.max(1f, bounds.height() - ROW_HEIGHT)), Math.round(255f * disclosure));
        try {
            float y = bounds.y() + ROW_HEIGHT;
            for (String option : setting.getModes()) {
                UiRect optionBounds = new UiRect(bounds.x() + 5f, y, bounds.width() - 10f, OPTION_HEIGHT);
                boolean selected = setting.is(option);
                float selectedProgress = optionProgress(context, setting, option, selected);
                context.textIn(translatedOption(context, setting, option), new UiRect(optionBounds.x() + 9f, optionBounds.y(),
                        optionBounds.width() - 36f, optionBounds.height()), 10.5f,
                        mix(ClickGuiTheme.MUTED, ClickGuiTheme.TEXT, selectedProgress));
                iconAlpha(context, ClickGuiIcons.CHECK, optionBounds.x() + optionBounds.width() - 23f,
                        optionBounds.y() + 5f, 16f, context.accentIn(optionBounds, 0xC0), selectedProgress);
                if (openOptions == setting && disclosure >= .98f) {
                    addHit(new Hit(setting, HitKind.MODE_OPTION, optionBounds, option));
                }
                y += OPTION_HEIGHT;
            }
        } finally {
            context.frame().canvas().restoreToCount(layer);
            context.restore(clip);
        }
    }

    private void renderMulti(UiContext context, MultiSelectSetting setting, UiRect bounds) {
        UiRect value = new UiRect(bounds.x() + bounds.width() * 0.44f, bounds.y(),
                bounds.width() * 0.56f - 29f, ROW_HEIGHT);
        String summary = multiSummary(context, setting);
        scrollingValueText(context, setting, summary, value, 10.5f, ClickGuiTheme.MUTED);
        float disclosure = disclosureAnimations.getOrDefault(setting, openOptions == setting ? 1f : 0f);
        disclosureIcon(context, bounds, disclosure);
        addHit(new Hit(setting, HitKind.MULTI, new UiRect(bounds.x(), bounds.y(), bounds.width(), ROW_HEIGHT), null));
        if (disclosure <= .001f) return;
        int clip = context.clip(bounds, 0f);
        int layer = context.frame().canvas().saveLayerAlpha(
                Rect.makeXYWH(bounds.x(), bounds.y() + ROW_HEIGHT, bounds.width(),
                        Math.max(1f, bounds.height() - ROW_HEIGHT)), Math.round(255f * disclosure));
        try {
            float y = bounds.y() + ROW_HEIGHT;
            for (String option : setting.getModes()) {
                boolean selected = setting.isSelected(option);
                UiRect optionBounds = new UiRect(bounds.x() + 5f, y, bounds.width() - 10f, OPTION_HEIGHT);
                UiRect check = new UiRect(optionBounds.x() + 7f, optionBounds.y() + 6f, 14f, 14f);
                float selectedProgress = optionProgress(context, setting, option, selected);
                context.frame().draw().roundedRect(check.x(), check.y(), check.width(), check.height(), 3f,
                        mix(0x20FFFFFF, context.accentIn(check, 0x78), selectedProgress));
                iconAlpha(context, ClickGuiIcons.CHECK, check.x() + 1f, check.y() + 1f, 12f,
                        ClickGuiTheme.TEXT, selectedProgress);
                context.textIn(translatedOption(context, setting, option), new UiRect(optionBounds.x() + 28f, optionBounds.y(),
                        optionBounds.width() - 35f, optionBounds.height()), 10.5f,
                        mix(ClickGuiTheme.MUTED, ClickGuiTheme.TEXT, selectedProgress));
                if (openOptions == setting && disclosure >= .98f) {
                    addHit(new Hit(setting, HitKind.MULTI_OPTION, optionBounds, option));
                }
                y += OPTION_HEIGHT;
            }
        } finally {
            context.frame().canvas().restoreToCount(layer);
            context.restore(clip);
        }
    }

    private void renderColor(UiContext context, ColorSetting setting, UiRect bounds) {
        UiRect swatch = new UiRect(bounds.x() + bounds.width() - 29f, bounds.y() + 7f, 21f, 16f);
        context.frame().draw().roundedRect(swatch.x(), swatch.y(), swatch.width(), swatch.height(), 3f,
                setting.isRainbow() ? setting.getRainbow(3200, 0) : setting.getValue());
        context.frame().draw().outline(swatch.x(), swatch.y(), swatch.width(), swatch.height(), 3f, 1f,
                ClickGuiTheme.STROKE);
        addHit(new Hit(setting, HitKind.COLOR, new UiRect(bounds.x(), bounds.y(), bounds.width(), ROW_HEIGHT), null));
        float disclosure = disclosureAnimations.getOrDefault(setting, setting.isExpanded() ? 1f : 0f);
        if (disclosure <= .001f) return;
        int clip = context.clip(bounds, 0f);
        int layer = context.frame().canvas().saveLayerAlpha(
                Rect.makeXYWH(bounds.x(), bounds.y() + ROW_HEIGHT, bounds.width(),
                        Math.max(1f, bounds.height() - ROW_HEIGHT)), Math.round(255f * disclosure));
        try {
            float pickerY = bounds.y() + ROW_HEIGHT + 2f;
            UiRect saturationBrightness = new UiRect(bounds.x() + 7f, pickerY, bounds.width() - 14f, 60f);
            drawSaturationBrightness(context, saturationBrightness, setting.getHue());
            UiRect hue = new UiRect(saturationBrightness.x(), pickerY + 66f, saturationBrightness.width(), 8f);
            drawHue(context, hue);
            UiRect alpha = new UiRect(saturationBrightness.x(), pickerY + 80f,
                    saturationBrightness.width(), 8f);
            drawAlpha(context, alpha, setting);
            if (setting.isExpanded() && disclosure >= .98f) {
                addHit(new Hit(setting, HitKind.COLOR_SB, saturationBrightness, null));
                addHit(new Hit(setting, HitKind.COLOR_HUE, hue, null));
                addHit(new Hit(setting, HitKind.COLOR_ALPHA, alpha, null));
            }
            if (setting.isRainbowSupported()) {
                UiRect rainbow = new UiRect(saturationBrightness.x(), pickerY + 94f,
                        saturationBrightness.width(), 20f);
                context.textIn(context.translate("nimbus.ui.rainbow", "Rainbow"), new UiRect(
                        rainbow.x() + 2f, rainbow.y(), rainbow.width() - 38f, rainbow.height()),
                        10f, setting.isRainbow() ? ClickGuiTheme.TEXT : ClickGuiTheme.MUTED);
                float rainbowProgress = animations.getOrDefault(setting, setting.isRainbow() ? 1f : 0f);
                rainbowProgress = animate(rainbowProgress, setting.isRainbow() ? 1f : 0f,
                        1f - (float) Math.exp(-context.deltaSeconds() * 14f));
                animations.put(setting, rainbowProgress);
                UiRect toggle = new UiRect(rainbow.x() + rainbow.width() - 30f,
                        rainbow.y() + 3f, 28f, 16f);
                context.frame().draw().roundedRect(toggle.x(), toggle.y(), toggle.width(), toggle.height(), 8f,
                        mix(0x30FFFFFF, context.accentIn(toggle, 0x72), rainbowProgress));
                context.frame().draw().roundedRect(toggle.x() + 2f + 12f * rainbowProgress,
                        toggle.y() + 2f, 12f, 12f, 6f,
                        mix(0xFFC9CBD0, 0xFFFFFFFF, rainbowProgress));
                if (setting.isExpanded() && disclosure >= .98f) {
                    addHit(new Hit(setting, HitKind.COLOR_RAINBOW, rainbow, null));
                }
            }
        } finally {
            context.frame().canvas().restoreToCount(layer);
            context.restore(clip);
        }
    }

    private void renderText(UiContext context, Setting<String> setting, UiRect bounds, boolean secure) {
        boolean stacked = bounds.width() < STACKED_TEXT_THRESHOLD;
        UiRect input = stacked
                ? new UiRect(bounds.x() + 8f, bounds.y() + 31f, bounds.width() - 16f, 25f)
                : new UiRect(bounds.x() + bounds.width() * .46f, bounds.y() + 4f,
                bounds.width() * .54f - 8f, 24f);
        float speed = 1f - (float) Math.exp(-context.deltaSeconds() * 15f);
        float focused = animate(focusAnimations.getOrDefault(setting, editing == setting ? 1f : 0f),
                editing == setting ? 1f : 0f, speed);
        focusAnimations.put(setting, focused);
        int inputBackground = mix(ClickGuiTheme.CONTROL, context.accentIn(input, 0x38), focused);
        int inputOutline = mix(ClickGuiTheme.STROKE, context.accentIn(input, 0x64), focused);
        if (editing == setting) {
            String placeholder = setting instanceof TextSetting text && text.getPlaceholder() != null
                    ? context.translate(setting.getTranslationKey("placeholder"), text.getPlaceholder())
                    : context.translate("nimbus.ui.value", "Value");
            editor.render(context, input, placeholder, 10.5f, secure, inputBackground, inputOutline);
        } else {
            context.frame().draw().roundedRect(input.x(), input.y(), input.width(), input.height(), 4f,
                    inputBackground);
            context.frame().draw().outline(input.x(), input.y(), input.width(), input.height(), 4f, 1f,
                    inputOutline);
            String value = setting.getValue();
            if (secure && value != null) value = "*".repeat(value.length());
            context.centered(ellipsize(context, value == null || value.isEmpty()
                            ? context.translate("nimbus.ui.empty", "Empty") : value,
                    input.width() - 8f), input, 10f, ClickGuiTheme.MUTED);
        }
        addHit(new Hit(setting, HitKind.TEXT, input, null));
    }

    private void renderMultilineText(UiContext context, TextSetting setting, UiRect bounds) {
        UiRect input = new UiRect(bounds.x() + 7f, bounds.y() + ROW_HEIGHT + 5f,
                bounds.width() - 14f, bounds.height() - ROW_HEIGHT - 12f);
        if (editing == setting) {
            multilineEditor.render(context, input,
                    setting.getPlaceholder() == null
                            ? context.translate("nimbus.ui.value", "Value")
                            : context.translate(setting.getTranslationKey("placeholder"), setting.getPlaceholder()),
                    10.5f, setting.isSecure());
        } else {
            context.frame().draw().roundedRect(input.x(), input.y(), input.width(), input.height(), 4f,
                    ClickGuiTheme.CONTROL);
            context.frame().draw().outline(input.x(), input.y(), input.width(), input.height(), 4f, 1f,
                    ClickGuiTheme.STROKE);
            String value = setting.getValue();
            String display = value == null || value.isEmpty()
                    ? context.translate("nimbus.ui.empty", "Empty") : value;
            MultilineTextInput.renderPreview(context, input, display, 10.5f,
                    setting.isSecure(), ClickGuiTheme.MUTED);
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
        if (setting instanceof ModeSetting mode) {
            float progress = disclosure(setting, openOptions == setting);
            return ROW_HEIGHT + (mode.getModes().length * OPTION_HEIGHT + 2f) * progress;
        }
        if (setting instanceof MultiSelectSetting multi) {
            float progress = disclosure(setting, openOptions == setting);
            return ROW_HEIGHT + (multi.getModes().length * OPTION_HEIGHT + 2f) * progress;
        }
        if (setting instanceof ColorSetting color) {
            float pickerHeight = color.isRainbowSupported() ? COLOR_AREA_HEIGHT : COLOR_PICKER_HEIGHT;
            return ROW_HEIGHT + pickerHeight * disclosure(setting, color.isExpanded());
        }
        if (setting instanceof NumberSetting) return NUMBER_HEIGHT;
        if (setting instanceof TextSetting text && text.isMultiline()) return MULTILINE_TEXT_HEIGHT;
        if (setting instanceof TextSetting) return width < STACKED_TEXT_THRESHOLD ? STACKED_TEXT_HEIGHT : ROW_HEIGHT;
        if (!(setting instanceof BooleanSetting || setting instanceof FoldSetting || setting instanceof NumberSetting
                || setting instanceof ModeSetting || setting instanceof MultiSelectSetting
                || setting instanceof ColorSetting || setting instanceof TextSetting)) {
            return 54f;
        }
        return ROW_HEIGHT;
    }

    private float visibility(Setting<?> setting) {
        boolean target = !setting.isHidden();
        float value = visibilityAnimations.getOrDefault(setting, target ? 1f : 0f);
        if (visibilityUpdated.add(setting)) {
            value = animate(value, target ? 1f : 0f, disclosureSpeed);
            visibilityAnimations.put(setting, value);
        }
        return value;
    }

    private float disclosure(Setting<?> setting, boolean target) {
        float value = disclosureAnimations.getOrDefault(setting, target ? 1f : 0f);
        if (disclosuresUpdated.add(setting)) {
            value = animate(value, target ? 1f : 0f, disclosureSpeed);
            disclosureAnimations.put(setting, value);
        }
        return value;
    }

    private Hit findHit(float x, float y) {
        for (int index = hits.size() - 1; index >= 0; index--) {
            Hit hit = hits.get(index);
            if (hit.bounds().contains(x, y) && (hit.clip() == null || hit.clip().contains(x, y))) return hit;
        }
        return null;
    }

    private void addHit(Hit hit) {
        if (hitsEnabled && (hitClip == null || hit.bounds().intersects(hitClip))) {
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

    private static void rightText(UiContext context, String value, UiRect bounds, float size, int color) {
        float width = context.width(UiContext.FONT, size, value);
        float y = bounds.y() + (bounds.height() - context.height(UiContext.FONT, size, value)) * .5f;
        context.text(value, bounds.x() + Math.max(0f, bounds.width() - width), y, size, color);
    }

    private void scrollingValueText(UiContext context, Setting<?> setting, String value,
                                    UiRect bounds, float size, int color) {
        String text = value == null ? "" : value;
        float textWidth = context.width(UiContext.FONT, size, text);
        if (textWidth <= bounds.width()) {
            marqueeStates.remove(setting);
            rightText(context, text, bounds, size, color);
            return;
        }

        long now = context.nowMillis();
        MarqueeState state = marqueeStates.get(setting);
        if (state == null || !state.value().equals(text)) {
            state = new MarqueeState(text, now);
            marqueeStates.put(setting, state);
        }

        float overflow = textWidth - bounds.width();
        long hold = 900L;
        long travel = Math.max(1000L, Math.round(overflow / 28f * 1000f));
        long cycle = hold * 2L + travel * 2L;
        long elapsed = Math.floorMod(now - state.startedAt(), cycle);
        float offset;
        if (elapsed < hold) {
            offset = 0f;
        } else if (elapsed < hold + travel) {
            offset = overflow * smoothStep((elapsed - hold) / (float) travel);
        } else if (elapsed < hold * 2L + travel) {
            offset = overflow;
        } else {
            offset = overflow * (1f - smoothStep((elapsed - hold * 2L - travel) / (float) travel));
        }

        float y = bounds.y() + (bounds.height() - context.height(UiContext.FONT, size, text)) * .5f;
        int clip = context.clip(bounds, 0f);
        try {
            context.text(text, bounds.x() - offset, y, size, color);
        } finally {
            context.restore(clip);
        }
    }

    private static float smoothStep(float value) {
        float bounded = Math.clamp(value, 0f, 1f);
        return bounded * bounded * (3f - 2f * bounded);
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

    private static void disclosureIcon(UiContext context, UiRect bounds, float progress) {
        float iconSize = 18f;
        float iconX = bounds.x() + bounds.width() - 26f;
        float iconY = bounds.y() + (ROW_HEIGHT - iconSize) * .5f;
        iconAlpha(context, ClickGuiIcons.SETTING_EXPAND_MORE, iconX, iconY, iconSize,
                ClickGuiTheme.MUTED, 1f - progress);
        iconAlpha(context, ClickGuiIcons.SETTING_EXPAND_LESS, iconX, iconY, iconSize,
                ClickGuiTheme.MUTED, progress);
    }

    private float optionProgress(UiContext context, Setting<?> setting, String option, boolean target) {
        OptionKey key = new OptionKey(setting, option);
        float speed = 1f - (float) Math.exp(-context.deltaSeconds() * 16f);
        float progress = animate(optionAnimations.getOrDefault(key, target ? 1f : 0f),
                target ? 1f : 0f, speed);
        optionAnimations.put(key, progress);
        return progress;
    }

    private static float labelWidth(Setting<?> setting, float width, boolean stackedText) {
        if (stackedText) return width - 16f;
        float available = switch (setting) {
            case NumberSetting ignored -> width - 70f;
            case BooleanSetting ignored -> width - 50f;
            case FoldSetting ignored -> width - 38f;
            case ModeSetting ignored -> width * .44f - 12f;
            case MultiSelectSetting ignored -> width * .44f - 12f;
            case ColorSetting ignored -> width - 41f;
            case TextSetting ignored -> width * .46f - 12f;
            default -> width - 16f;
        };
        return Math.max(24f, available);
    }

    private static String multiSummary(UiContext context, MultiSelectSetting setting) {
        int selected = setting.getValue().size();
        if (selected == 0) return context.translate("nimbus.ui.none", "None");
        if (selected == 1) return translatedOption(context, setting, setting.getValue().iterator().next());
        return context.translate("nimbus.ui.selected_count", "%s selected", selected);
    }

    private static String translatedName(UiContext context, Setting<?> setting) {
        return context.translate(setting.getTranslationKey(), setting.getName());
    }

    private static String translatedOption(UiContext context, Setting<?> setting, String option) {
        return context.translate(setting.getOptionTranslationKey(option), option);
    }

    private static float animate(float value, float target, float speed) {
        float result = value + (target - value) * speed;
        return Math.abs(result - target) < .002f ? target : result;
    }

    private static int alpha(int color, float progress) {
        int alpha = Math.round((color >>> 24) * Math.clamp(progress, 0f, 1f));
        return color & 0x00FFFFFF | alpha << 24;
    }

    private static int mix(int from, int to, float progress) {
        float value = Math.clamp(progress, 0f, 1f);
        int a = Math.round((from >>> 24) + ((to >>> 24) - (from >>> 24)) * value);
        int r = Math.round((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * value);
        int g = Math.round((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * value);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * value);
        return a << 24 | r << 16 | g << 8 | b;
    }

    private static String format(Number value) {
        if (value == null) return "0";
        double number = value.doubleValue();
        if (Math.abs(number - Math.rint(number)) < 1e-8) return Long.toString(Math.round(number));
        return String.format(Locale.ROOT, "%.2f", number).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static String ellipsize(UiContext context, String value, float width) {
        if (value == null) return "";
        if (context.width(UiContext.FONT, 10.5f, value) <= width) return value;
        String result = value;
        while (!result.isEmpty()
                && context.width(UiContext.FONT, 10.5f, result + "...") > width) {
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

    private record MarqueeState(String value, long startedAt) {
    }

    private record OptionKey(Setting<?> setting, String option) { }
}
