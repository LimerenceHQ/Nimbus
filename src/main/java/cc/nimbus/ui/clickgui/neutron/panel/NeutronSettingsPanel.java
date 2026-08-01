package cc.nimbus.ui.clickgui.neutron.panel;

import cc.nimbus.core.Core;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.setting.Setting;
import cc.nimbus.features.module.api.setting.impl.*;
import cc.nimbus.ui.clickgui.neutron.widget.*;
import cc.nimbus.ui.clickgui.neutron.NeutronActions;
import cc.nimbus.ui.clickgui.neutron.NeutronState;
import cc.nimbus.utils.render.skija.api.SkijaFrame;
import cc.nimbus.utils.client.input.Keyboard;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.types.Point;
import io.github.humbleui.types.Rect;

import java.awt.*;
import java.util.*;
import java.util.List;

public class NeutronSettingsPanel extends NeutronScrollAreaPanel {
    private static final String FONT_TITLE = "SarasaUiSC-SemiBold";
    private static final String FONT_TEXT = "SarasaUiSC-Regular";
    private static final int CARD_BG = 0x22000000;
    private static final int CARD_STROKE = 0x22FFFFFF;
    private static final int OUTLINE = 0x33FFFFFF;
    private static final float COLOR_ROW_H = 36f;
    private static final float COLOR_PICKER_PAD = 8f;
    private static final float COLOR_SB_SIZE = 80f;
    private static final float COLOR_SLIDER_H = 12f;
    private static final float COLOR_SLIDER_GAP = 10f;

    private final NeutronState state;
    private final NeutronActions actions;
    private NumberSetting draggingNumber;
    private final Map<NumberSetting, Rect> sliderRects = new HashMap<>();
    private final Map<NumberSetting, Rect> valueRects = new HashMap<>();
    private final Map<Setting<String>, Rect> textRects = new HashMap<>();
    private final List<ChipHit> chipHits = new ArrayList<>();
    private final List<ModeOptionHit> modeOptionHits = new ArrayList<>();
    private final Map<ModeSetting, Rect> modeTriggerRects = new HashMap<>();
    private final List<ModuleCard> lastCards = new ArrayList<>();
    private final IdentityHashMap<Setting<?>, Float> visibilityAnim = new IdentityHashMap<>();
    private final IdentityHashMap<Setting<?>, Integer> visibilityFrame = new IdentityHashMap<>();
    private final IdentityHashMap<Setting<?>, Boolean> visibilityState = new IdentityHashMap<>();
    private final IdentityHashMap<Object, Float> expandAnim = new IdentityHashMap<>();
    private final IdentityHashMap<Object, Integer> expandFrame = new IdentityHashMap<>();
    private final IdentityHashMap<Object, Boolean> expandState = new IdentityHashMap<>();
    private final IdentityHashMap<Object, Float> toggleAnim = new IdentityHashMap<>();
    private final IdentityHashMap<Object, Integer> toggleFrame = new IdentityHashMap<>();
    private final IdentityHashMap<Object, Boolean> toggleState = new IdentityHashMap<>();
    private final IdentityHashMap<BasicModule, Float> cardHAnim = new IdentityHashMap<>();
    private final IdentityHashMap<BasicModule, Integer> cardHFrame = new IdentityHashMap<>();
    private final IdentityHashMap<ModeSetting, Float> modeAnim = new IdentityHashMap<>();
    private final IdentityHashMap<ModeSetting, Integer> modeAnimFrame = new IdentityHashMap<>();
    private ModeSetting openedMode;
    private ModeDropdown pendingDropdown;
    private Rect lastModeDropdownRect;
    private ColorSetting openedColor;
    private int frameId;
    private long lastFrameNanos;
    private float deltaSec;
    private SkijaFrame currentFrame;
    private ColorSetting draggingColor;
    private ColorDragTarget draggingColorTarget;
    private final IdentityHashMap<BasicModule, Float> cardXAnim = new IdentityHashMap<>();
    private final IdentityHashMap<BasicModule, Integer> cardXFrame = new IdentityHashMap<>();
    private final IdentityHashMap<BasicModule, Float> cardYAnim = new IdentityHashMap<>();
    private final IdentityHashMap<BasicModule, Integer> cardYFrame = new IdentityHashMap<>();

    public NeutronSettingsPanel(NeutronState state, NeutronActions actions) {
        this.state = state;
        this.actions = actions;
        this.radius = 12f;
        this.scrollStep = 36f;
    }

    @Override
    protected void renderContent(SkijaFrame frame, float mx, float my, boolean leftDown, boolean rightDown, boolean leftPressed) {
        currentFrame = frame;
        Canvas canvas = frame.canvas();
        sliderRects.clear();
        valueRects.clear();
        textRects.clear();
        chipHits.clear();
        modeOptionHits.clear();
        modeTriggerRects.clear();
        pendingDropdown = null;
        beginFrame();
        boolean blockClick = leftPressed && isModeDropdownClick(mx, my);

        float gap = 18f;
        float sidePad = 16f;
        float topPad = 8f;
        float bottomPad = 12f;
        float availableW = Math.max(0f, width - sidePad * 2f);
        boolean singleColumn = availableW < 640f;
        float colGap = singleColumn ? 0f : gap;
        float colW = singleColumn ? Math.min(560f, availableW) : (availableW - colGap) / 2f;
        float totalW = singleColumn ? colW : (colW * 2f + colGap);
        float startX = x + (width - totalW) / 2f;
        float col1X = startX;
        float col2X = startX + colW + colGap;
        float baseY = y + topPad;

        String query = state.searchText.toString().trim().toLowerCase(Locale.ROOT);
        List<BasicModule> modules = Core.INSTANCE.getModuleManager().getModulesByCategory(state.selectedCategory);

        List<BasicModule> filtered = new ArrayList<>();
        for (BasicModule m : modules) {
            if (query.isEmpty() || matchesQuery(m, query)) {
                filtered.add(m);
            }
        }

        if (filtered.isEmpty()) {
            lastCards.clear();
            UiPaints.TEXT.setColor(UiColors.TEXT_SECONDARY);
            UiText.drawXY(currentFrame, FONT_TEXT, 13f, "暂无模块", x, y, width, height, UiPaints.TEXT);
            contentHeight = topPad + bottomPad;
            return;
        }

        float contentHeight1 = 0f;
        float contentHeight2 = 0f;
        List<ModuleCard> cards = new ArrayList<>();
        for (BasicModule module : filtered) {
            float cardH = measureModuleCardHeight(module, colW);
            boolean toLeft = singleColumn || contentHeight1 <= contentHeight2;
            float cx = toLeft ? col1X : col2X;
            float cy = baseY + (toLeft ? contentHeight1 : contentHeight2);
            float animX = tickCardPos(cardXAnim, cardXFrame, module, cx, 12f);
            float animY = tickCardPos(cardYAnim, cardYFrame, module, cy, 12f);
            float animH = tickCardAnim(cardHAnim, cardHFrame, module, cardH, 8f);
            cards.add(new ModuleCard(module, animX, animY, colW, animH));
            if (toLeft) contentHeight1 += animH + gap;
            else contentHeight2 += animH + gap;
        }

        contentHeight = Math.max(contentHeight1, contentHeight2) + topPad + bottomPad;
        lastCards.clear();
        lastCards.addAll(cards);

        for (ModuleCard card : cards) {
            renderModuleCard(canvas, card, mx, my, leftDown, leftPressed && !blockClick);
        }
        if (pendingDropdown != null) {
            renderModeDropdown(canvas, pendingDropdown, mx, my);
            lastModeDropdownRect = pendingDropdown.rect();
        } else {
            lastModeDropdownRect = null;
        }

        if (!leftDown) {
            draggingNumber = null;
            draggingColor = null;
            draggingColorTarget = null;
        }

        if (openedMode == null) {
            processChipHits(leftPressed, mx, my);
        }
        if (leftPressed && state.numberFocused && state.editingNumber != null) {
            Rect r = valueRects.get(state.editingNumber);
            if (r == null || !r.contains(mx, my)) {
                actions.commitNumber();
            }
        }
        if (leftPressed && state.textFocused && state.editingText != null) {
            Rect r = textRects.get(state.editingText);
            if (r == null || !r.contains(mx, my)) {
                actions.commitText();
            }
        }
        processModeHits(leftPressed, mx, my);
    }

    public boolean middleClick(float mx, float my) {
        if (!contains(mx, my) || lastCards.isEmpty()) return false;
        float maxScroll = Math.max(0f, contentHeight - height);
        float renderScrollY = Math.max(0f, Math.min(maxScroll, (float) Math.round(scrollY)));
        float localY = my + renderScrollY;
        ModuleCard hovered = findHoveredCard(mx, localY);
        if (hovered == null) return false;
        for (BasicModule m : Core.INSTANCE.getModuleManager().getModules()) {
            m.changeKey = false;
        }
        if (state.bindingModule == hovered.module) {
            state.bindingModule = null;
            actions.showToast("已取消绑定：" + hovered.module.getDisplayName());
            return true;
        }
        state.bindingModule = hovered.module;
        hovered.module.changeKey = true;
        actions.showToast("按下按键完成绑定：" + hovered.module.getDisplayName());
        return true;
    }

    private ModuleCard findHoveredCard(float mx, float my) {
        for (ModuleCard card : lastCards) {
            if (mx >= card.x && mx <= card.x + card.w && my >= card.y && my <= card.y + card.h) {
                return card;
            }
        }
        return null;
    }

    private void renderModuleCard(Canvas canvas, ModuleCard card, float mx, float my, boolean leftDown, boolean leftPressed) {
        float x = card.x;
        float y = card.y;
        float w = card.w;
        float h = card.h;
        float headerH = 36f;
        float innerPad = 12f;

        UiDraw.fillRRect(currentFrame, x, y, w, h, 14f, CARD_BG);
        UiDraw.strokeRRect(currentFrame, x, y, w, h, 14f, 1f, CARD_STROKE);

        boolean hoverHeader = mx >= x && mx <= x + w && my >= y && my <= y + headerH;
        float keyH = 18f;
        float keyTextSize = 11.5f;
        String keyText = card.module.getKey() == 0 ? "未绑定" : Keyboard.getKeyName(card.module.getKey());
        if (keyText == null || keyText.isEmpty()) keyText = "未绑定";
        float keyTextW = currentFrame.text().width(FONT_TEXT, keyTextSize, keyText);
        float keyW = Math.max(52f, keyTextW + 14f);

        float toggleW = 36f;
        float toggleH = 18f;
        float tx = x + w - toggleW - innerPad;
        float ty = y + (headerH - toggleH) / 2f;
        UiPaints.TEXT.setColor(UiColors.TEXT_PRIMARY);
        float maxTitleW = Math.max(40f, w - innerPad * 2f - toggleW - 8f - keyW - 8f);
        String title = ellipsize(card.module.getDisplayName(), maxTitleW);
        UiText.drawLeftY(currentFrame, FONT_TITLE, 13.5f, title, x + innerPad, y, headerH, UiPaints.TEXT);
        float keyX = tx - keyW - 8f;
        float keyY = ty;
        boolean hoverKey = mx >= keyX && mx <= keyX + keyW && my >= keyY && my <= keyY + keyH;

        int keyBg = state.bindingModule == card.module
                ? UiTheme.withAlpha(UiTheme.accent(), 0x55)
                : (hoverKey ? UiColors.HOVER : 0x14000000);
        UiDraw.fillRRect(currentFrame, keyX, keyY, keyW, keyH, 7f, keyBg);
        UiDraw.strokeRRect(currentFrame, keyX, keyY, keyW, keyH, 7f, 1f, OUTLINE);
        UiPaints.TEXT.setColor(UiColors.TEXT_PRIMARY);
        UiText.drawXY(currentFrame, FONT_TEXT, keyTextSize, keyText, keyX, keyY, keyW, keyH, UiPaints.TEXT);

        if (hoverHeader && leftPressed && !hoverKey) {
            card.module.toggle();
        }
        if (hoverKey && leftPressed) {
            for (BasicModule m : Core.INSTANCE.getModuleManager().getModules()) {
                m.changeKey = false;
            }
            if (state.bindingModule == card.module) {
                state.bindingModule = null;
                actions.showToast("已取消绑定：" + card.module.getDisplayName());
            } else {
                state.bindingModule = card.module;
                card.module.changeKey = true;
                actions.showToast("按下按键完成绑定：" + card.module.getDisplayName());
            }
        }
        float toggleT = tickToggle(card.module, card.module.isEnabled());
        int toggleBg = lerpColor(0x22000000, UiTheme.withAlpha(UiTheme.accent(), 0xAA), toggleT);
        UiDraw.fillRRect(currentFrame, tx, ty, toggleW, toggleH, toggleH / 2f, toggleBg);
        float knob = toggleH - 4f;
        float kx = tx + 2f + (toggleW - knob - 4f) * toggleT;
        UiDraw.fillRRect(currentFrame, kx, ty + 2f, knob, knob, knob / 2f, 0xFFFFFFFF);

        canvas.save();
        UiDraw.clipRRect(currentFrame, x, y, w, h, 14f);
        float cy = y + headerH + 6f;
        boolean hasSetting = false;
        for (Setting<?> setting : card.module.getSettings()) {
            float visibleT = tickVisibility(setting, setting.isHidden());
            if (visibleT <= 0.001f) continue;
            hasSetting = true;
            boolean allowInteraction = (openedMode == null || openedMode == setting) && visibleT > 0.98f;
            float rowW = w - innerPad * 2f;
            float rowH = getSettingRowHeight(setting, rowW);
            float drawH = rowH * visibleT;
            float clipY = cy + rowH - drawH;
            canvas.save();
            UiDraw.clipRRect(currentFrame, x + innerPad, clipY, rowW, drawH, 10f);
            renderSettingRow(canvas, setting, x + innerPad, cy, rowW, rowH, mx, my, leftDown, leftPressed, allowInteraction);
            canvas.restore();
            cy += drawH + 10f * visibleT;
        }
        canvas.restore();
        if (!hasSetting) {
            UiPaints.TEXT.setColor(UiColors.TEXT_SECONDARY);
            UiText.drawXY(currentFrame, FONT_TEXT, 12.5f, "暂无可调参数", x, y + headerH, w, h - headerH, UiPaints.TEXT);
        }
    }

    private void renderSettingRow(Canvas canvas, Setting<?> setting, float x, float y, float w, float rowH,
                                  float mx, float my, boolean leftDown, boolean leftPressed, boolean allowInteraction) {
        UiDraw.fillRRect(currentFrame, x, y, w, rowH, 10f, 0x14000000);
        UiDraw.strokeRRect(currentFrame, x, y, w, rowH, 10f, 1f, OUTLINE);

        if (setting instanceof NumberSetting s) {
            UiPaints.TEXT.setColor(UiColors.TEXT_PRIMARY);
            UiText.drawLeftY(currentFrame, FONT_TITLE, 13.5f, setting.getName(), x + 12f, y + 6f, 18f, UiPaints.TEXT);
            drawNumber(canvas, s, x, y, w, rowH, mx, my, leftDown && allowInteraction, leftPressed && allowInteraction);
        } else if (setting instanceof MultiSelectSetting s) {
            UiPaints.TEXT.setColor(UiColors.TEXT_PRIMARY);
            UiText.drawLeftY(currentFrame, FONT_TITLE, 13.5f, setting.getName(), x + 12f, y + 2f, 18f, UiPaints.TEXT);
            drawMulti(canvas, s, x, y + 26f, w, allowInteraction);
        } else {
            UiPaints.TEXT.setColor(UiColors.TEXT_PRIMARY);
            UiText.drawLeftY(currentFrame, FONT_TITLE, 13.5f, setting.getName(), x + 12f, y, rowH, UiPaints.TEXT);

            float contentY = y + (rowH - 20f) * 0.5f;
            if (setting instanceof BooleanSetting s) {
                drawBoolean(canvas, s, x, y, w, rowH, mx, my, leftPressed && allowInteraction);
            } else if (setting instanceof FoldSetting s) {
                drawFold(canvas, s, x, y, w, rowH, mx, my, leftPressed && allowInteraction);
            } else if (setting instanceof ModeSetting s) {
                drawMode(canvas, s, x, y, w, rowH, mx, my, leftPressed && allowInteraction, allowInteraction);
            } else if (setting instanceof ColorSetting s) {
                drawColor(canvas, s, x, y, w, rowH, mx, my, leftDown, leftPressed && allowInteraction, allowInteraction);
            } else if (setting instanceof StringSetting s) {
                drawTextValue(canvas, s, x, y, w, rowH, mx, my, leftPressed && allowInteraction, allowInteraction);
            } else if (setting instanceof TextSetting s) {
                drawTextValue(canvas, s, x, y, w, rowH, mx, my, leftPressed && allowInteraction, allowInteraction);
            }
        }
    }

    private void drawBoolean(Canvas canvas, BooleanSetting s, float x, float y, float w, float rowH, float mx, float my, boolean leftPressed) {
        float toggleW = 32f;
        float toggleH = 16f;
        float tx = x + w - toggleW - 12f;
        float ty = y + (rowH - toggleH) / 2f;
        boolean hover = mx >= tx && mx <= tx + toggleW && my >= ty && my <= ty + toggleH;
        float toggleT = tickToggle(s, s.getValue());
        int bg = lerpColor(0x22000000, UiTheme.withAlpha(UiTheme.accent(), 0xAA), toggleT);
        UiDraw.fillRRect(currentFrame, tx, ty, toggleW, toggleH, toggleH / 2f, bg);
        float knob = toggleH - 4f;
        float kx = tx + 2f + (toggleW - knob - 4f) * toggleT;
        UiDraw.fillRRect(currentFrame, kx, ty + 2f, knob, knob, knob / 2f, 0xFFFFFFFF);
        if (hover && leftPressed) {
            s.setValue(!s.getValue());
        }
    }

    private void drawFold(Canvas canvas, FoldSetting s, float x, float y, float w, float rowH, float mx, float my, boolean leftPressed) {
        float toggleW = 32f;
        float toggleH = 16f;
        float tx = x + w - toggleW - 12f;
        float ty = y + (rowH - toggleH) / 2f;
        boolean hover = mx >= tx && mx <= tx + toggleW && my >= ty && my <= ty + toggleH;
        float toggleT = tickToggle(s, s.getValue());
        int bg = lerpColor(0x22000000, UiTheme.withAlpha(UiTheme.accent(), 0xAA), toggleT);
        UiDraw.fillRRect(currentFrame, tx, ty, toggleW, toggleH, toggleH / 2f, bg);
        float knob = toggleH - 4f;
        float kx = tx + 2f + (toggleW - knob - 4f) * toggleT;
        UiDraw.fillRRect(currentFrame, kx, ty + 2f, knob, knob, knob / 2f, 0xFFFFFFFF);
        if (hover && leftPressed) {
            s.setValue(!s.getValue());
        }
    }

    private void drawNumber(Canvas canvas, NumberSetting s, float x, float y, float w, float rowH,
                            float mx, float my, boolean leftDown, boolean leftPressed) {
        float trackH = 7f;
        float trackY = y + 34f;
        float trackX = x + 12f;
        float trackW = w - 24f;

        Rect rect = Rect.makeXYWH(trackX, trackY - 6f, trackW, trackH + 12f);
        sliderRects.put(s, rect);

        float v = clamp01((s.getValue().doubleValue() - s.getMinimum().doubleValue()) /
                Math.max(1e-6, s.getMaximum().doubleValue() - s.getMinimum().doubleValue()));
        UiDraw.fillRRect(currentFrame, trackX, trackY, trackW, trackH, trackH / 2f, 0x22000000);
        UiDraw.fillRRect(currentFrame, trackX, trackY, trackW * v, trackH, trackH / 2f, UiTheme.withAlpha(UiTheme.accent(), 0xCC));

        String valueText = state.numberFocused && state.editingNumber == s ? state.numberInput.toString() : formatNumberValue(s.getValue());
        float tw = currentFrame.text().width(FONT_TITLE, 12.5f, valueText);
        float valueW = Math.max(48f, tw + 12f);
        float valueH = 20f;
        float valueX = x + w - valueW - 12f;
        float valueY = y + 6f;
        Rect valueRect = Rect.makeXYWH(valueX, valueY, valueW, valueH + 4f);
        valueRects.put(s, valueRect);
        UiDraw.fillRRect(currentFrame, valueX, valueY, valueW, valueH + 4f, 6f, 0x14000000);
        UiDraw.strokeRRect(currentFrame, valueX, valueY, valueW, valueH + 4f, 6f, 1f, OUTLINE);
        UiPaints.TEXT.setColor(UiColors.TEXT_PRIMARY);
        UiText.drawXY(currentFrame, FONT_TITLE, 12.5f, valueText, valueX, valueY, valueW, valueH + 4f, UiPaints.TEXT);
        if (state.numberFocused && state.editingNumber == s) {
            actions.updateNumberCaret();
            UiDraw.strokeRRect(currentFrame, valueX, valueY, valueW, valueH + 4f, 6f, 1f, OUTLINE);
            if (state.numberCaretVisible) {
                float cx = valueX + (valueW - tw) * 0.5f + tw + 1f;
                float cy = valueY + 3f;
                UiDraw.fillRect(currentFrame, cx, cy, 1f, valueH - 2f, 0xCCFFFFFF);
            }
        }

        if (leftDown && draggingNumber == s) {
            updateNumberSettingByMouse(s, mx, rect);
        } else if (leftPressed && rect.contains(mx, my)) {
            draggingNumber = s;
            updateNumberSettingByMouse(s, mx, rect);
        } else if (leftPressed && valueRect.contains(mx, my)) {
            actions.startNumberInput(s);
        }
    }

    private void drawMode(Canvas canvas, ModeSetting s, float x, float y, float w, float rowH, float mx, float my, boolean leftPressed, boolean allowInteraction) {
        String value = s.getValue();
        float maxModeW = 0f;
        for (String mode : s.getModes()) {
            maxModeW = Math.max(maxModeW, currentFrame.text().width(FONT_TEXT, 12f, mode));
        }
        float pillW = Math.max(52f, Math.max(maxModeW, currentFrame.text().width(FONT_TEXT, 12f, value)) + 20f);
        float pillH = 22f;
        float px = x + w - pillW - 12f;
        float py = y + (rowH - pillH) * 0.5f;
        boolean hover = mx >= px && mx <= px + pillW && my >= py && my <= py + pillH;
        UiDraw.fillRRect(currentFrame, px, py, pillW, pillH, 10f, hover ? UiColors.HOVER : 0x14000000);
        UiDraw.strokeRRect(currentFrame, px, py, pillW, pillH, 10f, 1f, OUTLINE);
        UiPaints.TEXT.setColor(UiColors.TEXT_PRIMARY);
        UiText.drawXY(currentFrame, FONT_TEXT, 12f, value, px, py, pillW, pillH, UiPaints.TEXT);
        modeTriggerRects.put(s, Rect.makeXYWH(px, py, pillW, pillH));
        if (allowInteraction && hover && leftPressed) {
            if (openedMode == s) {
                openedMode = null;
            } else {
                openedMode = s;
            }
        }
        float modeT = getModeAnimT(s);
        if (openedMode == s || modeT > 0.001f) {
            float listW = Math.max(pillW, Math.max(120f, maxModeW + 20f));
            float listX = px + pillW - listW;
            float itemH = 22f;
            float listY = py + pillH + 6f;
            pendingDropdown = new ModeDropdown(s, listX, listY, listW, itemH);
        }
    }

    private void drawColor(Canvas canvas, ColorSetting s, float x, float y, float w, float rowH,
                           float mx, float my, boolean leftDown, boolean leftPressed, boolean allowInteraction) {
        float headerH = COLOR_ROW_H;
        int currentColor = s.isRainbow() ? s.getRainbow(2000, 0) : s.getValue();
        float toggleW = 32f;
        float toggleH = 18f;
        float toggleX = x + w - toggleW - 12f;
        float toggleY = y + (headerH - toggleH) * 0.5f;
        float previewW = 22f;
        float previewH = 14f;
        float previewX = toggleX - previewW - 8f;
        float previewY = y + (headerH - previewH) * 0.5f;

        drawCheckerboard(canvas, previewX, previewY, previewW, previewH, 4f);
        UiDraw.fillRRect(currentFrame, previewX, previewY, previewW, previewH, 4f, currentColor);
        UiDraw.strokeRRect(currentFrame, previewX, previewY, previewW, previewH, 4f, 1f, OUTLINE);

        int[] hueColors = new int[7];
        for (int i = 0; i < 7; i++) {
            hueColors[i] = Color.HSBtoRGB(i / 6f, 1f, 1f);
        }
        try (Paint rainbowPaint = new Paint()) {
            rainbowPaint.setAntiAlias(true);
            rainbowPaint.setShader(currentFrame.effects().linearGradient(
                    new Point(toggleX, toggleY),
                    new Point(toggleX + toggleW, toggleY),
                    hueColors
            ));
            canvas.save();
            UiDraw.clipRRect(currentFrame, toggleX, toggleY, toggleW, toggleH, 7f);
            canvas.drawRect(Rect.makeXYWH(toggleX, toggleY, toggleW, toggleH), rainbowPaint);
            canvas.restore();
        }
        UiDraw.strokeRRect(currentFrame, toggleX, toggleY, toggleW, toggleH, 7f, 1f, OUTLINE);
        if (!s.isRainbow()) {
            UiDraw.fillRRect(currentFrame, toggleX, toggleY, toggleW, toggleH, 7f, 0x66000000);
        }
        UiPaints.TEXT.setColor(s.isRainbow() ? UiColors.TEXT_PRIMARY : UiColors.TEXT_SECONDARY);
        UiText.drawXY(currentFrame, FONT_TEXT, 12f, "R", toggleX, toggleY, toggleW, toggleH, UiPaints.TEXT);

        boolean toggleHover = mx >= toggleX && mx <= toggleX + toggleW && my >= toggleY && my <= toggleY + toggleH;
        boolean headerHover = mx >= x && mx <= x + w && my >= y && my <= y + headerH;

        if (allowInteraction && leftPressed && toggleHover) {
            s.setRainbow(!s.isRainbow());
        } else if (allowInteraction && leftPressed && headerHover) {
            openedMode = null;
            if (openedColor != null && openedColor != s) {
                openedColor.setExpanded(false);
            }
            if (openedColor == s && s.isExpanded()) {
                s.setExpanded(false);
                openedColor = null;
            } else {
                s.setExpanded(true);
                openedColor = s;
            }
        }

        float t = getColorAnimT(s);
        if (t <= 0f) {
            return;
        }

        float sbX = x + 12f;
        float sbY = y + headerH + COLOR_PICKER_PAD;
        float sbSize = COLOR_SB_SIZE;
        float sliderX = sbX + sbSize + COLOR_SLIDER_GAP;
        float sliderW = Math.max(40f, x + w - 12f - sliderX);
        float hueY = sbY;
        float alphaY = sbY + COLOR_SLIDER_H + 10f;

        float expandH = (COLOR_PICKER_PAD * 2f + COLOR_SB_SIZE) * t;
        canvas.save();
        UiDraw.clipRRect(currentFrame, x, y, w, headerH + expandH, 10f);

        drawSBPicker(canvas, sbX, sbY, sbSize, sbSize, s.getHue());
        float indicatorX = sbX + s.getSaturation() * sbSize;
        float indicatorY = sbY + (1f - s.getBrightness()) * sbSize;
        UiDraw.strokeRRect(currentFrame, indicatorX - 4f, indicatorY - 4f, 8f, 8f, 4f, 1.5f, 0xFFFFFFFF);
        UiDraw.strokeRRect(currentFrame, indicatorX - 3f, indicatorY - 3f, 6f, 6f, 3f, 1f, 0xFF000000);

        drawHueSlider(canvas, sliderX, hueY, sliderW, COLOR_SLIDER_H);
        float hueIndicatorX = sliderX + s.getHue() * sliderW;
        UiDraw.fillRRect(currentFrame, hueIndicatorX - 3f, hueY - 2f, 6f, COLOR_SLIDER_H + 4f, 3f, 0xFFFFFFFF);
        UiDraw.fillRRect(currentFrame, hueIndicatorX - 2f, hueY - 1f, 4f, COLOR_SLIDER_H + 2f, 2f,
                Color.HSBtoRGB(s.getHue(), 1f, 1f) | 0xFF000000);

        drawAlphaSlider(canvas, sliderX, alphaY, sliderW, COLOR_SLIDER_H, s);
        float alphaIndicatorX = sliderX + (s.getAlphaValue() / 255f) * sliderW;
        UiDraw.fillRRect(currentFrame, alphaIndicatorX - 3f, alphaY - 2f, 6f, COLOR_SLIDER_H + 4f, 3f, 0xFFFFFFFF);

        Rect sbRect = Rect.makeXYWH(sbX, sbY, sbSize, sbSize);
        Rect hueRect = Rect.makeXYWH(sliderX, hueY, sliderW, COLOR_SLIDER_H);
        Rect alphaRect = Rect.makeXYWH(sliderX, alphaY, sliderW, COLOR_SLIDER_H);

        if (allowInteraction) {
            if (leftDown && draggingColor == s && draggingColorTarget != null) {
                updateColorByDrag(s, draggingColorTarget, mx, my, sbRect, hueRect, alphaRect);
            } else if (leftPressed) {
                if (sbRect.contains(mx, my)) {
                    draggingColor = s;
                    draggingColorTarget = ColorDragTarget.SB;
                    updateColorByDrag(s, draggingColorTarget, mx, my, sbRect, hueRect, alphaRect);
                } else if (hueRect.contains(mx, my)) {
                    draggingColor = s;
                    draggingColorTarget = ColorDragTarget.HUE;
                    updateColorByDrag(s, draggingColorTarget, mx, my, sbRect, hueRect, alphaRect);
                } else if (alphaRect.contains(mx, my)) {
                    draggingColor = s;
                    draggingColorTarget = ColorDragTarget.ALPHA;
                    updateColorByDrag(s, draggingColorTarget, mx, my, sbRect, hueRect, alphaRect);
                }
            }
        }
        canvas.restore();
    }

    private void drawMulti(Canvas canvas, MultiSelectSetting s, float x, float y, float w, boolean allowInteraction) {
        float cx = x + 12f;
        float cy = y;
        float chipH = 19f;
        float gap = 6f;
        float rowGap = 6f;
        float maxX = x + w - 12f;
        for (String mode : s.getModes()) {
            float tw = currentFrame.text().width(FONT_TEXT, 11.5f, mode);
            float chipW = tw + 14f;
            if (cx + chipW > maxX) {
                cx = x + 12f;
                cy += chipH + rowGap;
            }
            boolean selected = s.isSelected(mode);
            UiDraw.fillRRect(currentFrame, cx, cy, chipW, chipH, 9f, selected ? UiTheme.withAlpha(UiTheme.accent(), 0x88) : 0x14000000);
            UiDraw.strokeRRect(currentFrame, cx, cy, chipW, chipH, 9f, 1f, UiColors.STROKE);
            UiPaints.TEXT.setColor(selected ? UiColors.TEXT_PRIMARY : UiColors.TEXT_SECONDARY);
            UiText.drawXY(currentFrame, FONT_TEXT, 11.5f, mode, cx, cy, chipW, chipH, UiPaints.TEXT);
            if (allowInteraction) {
                chipHits.add(new ChipHit(cx, cy, chipW, chipH, s, mode));
            }
            cx += chipW + gap;
        }
    }

    private void drawTextValue(Canvas canvas, Setting<String> setting, float x, float y, float w, float rowH,
                               float mx, float my, boolean leftPressed, boolean allowInteraction) {
        boolean focused = state.textFocused && state.editingText == setting;
        String raw = focused ? state.textInput.toString() : setting.getValue();
        String placeholder = setting instanceof TextSetting s ? s.getPlaceholder() : null;
        boolean usePlaceholder = !focused && (raw == null || raw.isEmpty()) && placeholder != null && !placeholder.isEmpty();
        String text = usePlaceholder ? placeholder : (raw == null || raw.isEmpty() ? "未设置" : raw);
        if (!usePlaceholder && setting instanceof TextSetting s && s.isSecure() && raw != null && !raw.isEmpty()) {
            text = maskText(text);
        }
        float maxW = Math.max(120f, w * 0.45f);
        String draw = ellipsize(text, maxW);
        float tw = currentFrame.text().width(FONT_TEXT, 12.5f, draw);
        float valueW = Math.max(72f, Math.min(maxW, tw + 12f));
        float valueH = 20f;
        float valueX = x + w - valueW - 12f;
        float valueY = y + (rowH - valueH) * 0.5f;
        Rect valueRect = Rect.makeXYWH(valueX, valueY, valueW, valueH);
        textRects.put(setting, valueRect);

        UiPaints.TEXT.setColor(usePlaceholder || (!focused && (raw == null || raw.isEmpty()))
                ? UiColors.TEXT_SECONDARY
                : UiColors.TEXT_PRIMARY);
        UiDraw.fillRRect(currentFrame, valueX, valueY, valueW, valueH, 8f, 0x14000000);
        UiDraw.strokeRRect(currentFrame, valueX, valueY, valueW, valueH, 8f, 1f, OUTLINE);
        UiText.drawXY(currentFrame, FONT_TEXT, 12.5f, draw, valueX, valueY, valueW, valueH, UiPaints.TEXT);

        if (focused) {
            actions.updateTextCaret();
            if (state.textCaretVisible) {
                float cx = valueX + (valueW - tw) * 0.5f + tw + 1f;
                float cy = valueY + 3f;
                UiDraw.fillRect(currentFrame, cx, cy, 1f, valueH - 4f, 0xCCFFFFFF);
            }
        }

        if (allowInteraction && leftPressed && valueRect.contains(mx, my)) {
            openedMode = null;
            openedColor = null;
            actions.startTextInput(setting);
        }
    }

    private void drawSBPicker(Canvas canvas, float x, float y, float w, float h, float hue) {
        int baseColor = Color.HSBtoRGB(hue, 1f, 1f);
        canvas.save();
        UiDraw.clipRRect(currentFrame, x, y, w, h, 6f);
        try (Paint paint = new Paint()) {
            paint.setAntiAlias(true);
            paint.setShader(currentFrame.effects().linearGradient(
                    new Point(x, y),
                    new Point(x + w, y),
                    new int[]{0xFFFFFFFF, baseColor}
            ));
            canvas.drawRect(Rect.makeXYWH(x, y, w, h), paint);
            paint.setShader(currentFrame.effects().linearGradient(
                    new Point(x, y),
                    new Point(x, y + h),
                    new int[]{0x00000000, 0xFF000000}
            ));
            canvas.drawRect(Rect.makeXYWH(x, y, w, h), paint);
        }
        canvas.restore();
        UiDraw.strokeRRect(currentFrame, x, y, w, h, 6f, 1f, OUTLINE);
    }

    private void drawHueSlider(Canvas canvas, float x, float y, float w, float h) {
        int[] colors = new int[7];
        for (int i = 0; i < 7; i++) {
            colors[i] = Color.HSBtoRGB(i / 6f, 1f, 1f);
        }
        canvas.save();
        UiDraw.clipRRect(currentFrame, x, y, w, h, 6f);
        try (Paint paint = new Paint()) {
            paint.setAntiAlias(true);
            paint.setShader(currentFrame.effects().linearGradient(
                    new Point(x, y),
                    new Point(x + w, y),
                    colors
            ));
            canvas.drawRect(Rect.makeXYWH(x, y, w, h), paint);
        }
        canvas.restore();
        UiDraw.strokeRRect(currentFrame, x, y, w, h, 6f, 1f, OUTLINE);
    }

    private void drawAlphaSlider(Canvas canvas, float x, float y, float w, float h, ColorSetting s) {
        drawCheckerboard(canvas, x, y, w, h, 6f);
        int baseColor = Color.HSBtoRGB(s.getHue(), s.getSaturation(), s.getBrightness());
        int transparent = baseColor & 0x00FFFFFF;
        int opaque = baseColor | 0xFF000000;
        canvas.save();
        UiDraw.clipRRect(currentFrame, x, y, w, h, 6f);
        try (Paint paint = new Paint()) {
            paint.setAntiAlias(true);
            paint.setShader(currentFrame.effects().linearGradient(
                    new Point(x, y),
                    new Point(x + w, y),
                    new int[]{transparent, opaque}
            ));
            canvas.drawRect(Rect.makeXYWH(x, y, w, h), paint);
        }
        canvas.restore();
        UiDraw.strokeRRect(currentFrame, x, y, w, h, 6f, 1f, OUTLINE);
    }

    private void updateColorByDrag(ColorSetting setting, ColorDragTarget target, float mx, float my,
                                   Rect sbRect, Rect hueRect, Rect alphaRect) {
        setting.setRainbow(false);
        switch (target) {
            case SB -> {
                float s = clamp01((mx - sbRect.getLeft()) / sbRect.getWidth());
                float b = clamp01(1f - (my - sbRect.getTop()) / sbRect.getHeight());
                setting.setHSB(setting.getHue(), s, b);
            }
            case HUE -> {
                float h = clamp01((mx - hueRect.getLeft()) / hueRect.getWidth());
                setting.setHue(h);
            }
            case ALPHA -> {
                float a = clamp01((mx - alphaRect.getLeft()) / alphaRect.getWidth());
                setting.setAlphaValue(Math.round(a * 255f));
            }
        }
    }

    private void processChipHits(boolean leftPressed, float mx, float my) {
        if (!leftPressed) return;
        for (ChipHit hit : chipHits) {
            if (hit.contains(mx, my)) {
                hit.setting.toggle(hit.mode);
                return;
            }
        }
    }

    private void processModeHits(boolean leftPressed, float mx, float my) {
        if (!leftPressed) return;
        for (ModeOptionHit hit : modeOptionHits) {
            if (hit.contains(mx, my)) {
                hit.setting.setValue(hit.mode);
                openedMode = null;
                return;
            }
        }
        if (openedMode != null) {
            Rect trigger = modeTriggerRects.get(openedMode);
            Rect dropdown = pendingDropdown == null ? null : pendingDropdown.rect();
            if ((trigger == null || !trigger.contains(mx, my)) && (dropdown == null || !dropdown.contains(mx, my))) {
                openedMode = null;
            }
        }
    }

    private void renderModeDropdown(Canvas canvas, ModeDropdown dropdown, float mx, float my) {
        ModeSetting setting = dropdown.setting;
        String value = setting.getValue();
        String[] modes = setting.getModes();
        Rect dropdownRect = dropdown.rect();
        float listH = dropdownRect.getHeight();
        float t = getModeAnimT(setting);
        float animH = Math.max(2f, listH * t);
        int bgAlpha = Math.round(0x1A * t);
        int strokeAlpha = Math.round(0x33 * t);
        int bg = UiTheme.withAlpha(0xFF000000, bgAlpha);
        int stroke = UiTheme.withAlpha(0xFFFFFFFF, strokeAlpha);
        UiDraw.fillRRect(currentFrame, dropdown.x, dropdown.y, dropdown.w, animH, 10f, bg);
        UiDraw.strokeRRect(currentFrame, dropdown.x, dropdown.y, dropdown.w, animH, 10f, 1f, stroke);
        canvas.save();
        UiDraw.clipRRect(currentFrame, dropdown.x, dropdown.y, dropdown.w, animH, 10f);
        float iy = dropdown.y + 4f;
        float visibleBottom = dropdown.y + animH - 4f;
        for (String mode : modes) {
            boolean itemHover = mx >= dropdown.x && mx <= dropdown.x + dropdown.w && my >= iy && my <= iy + dropdown.itemH;
            if (itemHover) {
                int hoverAlpha = Math.round(((UiColors.HOVER >>> 24) & 0xFF) * t);
                UiDraw.fillRRect(currentFrame, dropdown.x + 4f, iy, dropdown.w - 8f, dropdown.itemH, 8f,
                        UiTheme.withAlpha(UiColors.HOVER, hoverAlpha));
            }
            int textAlpha = Math.round(255f * t);
            UiPaints.TEXT.setColor(mode.equals(value)
                    ? UiTheme.withAlpha(UiColors.TEXT_PRIMARY, textAlpha)
                    : UiTheme.withAlpha(UiColors.TEXT_SECONDARY, textAlpha));
            UiText.drawLeftY(currentFrame, FONT_TEXT, 12f, mode, dropdown.x + 10f, iy, dropdown.itemH, UiPaints.TEXT);
            if (iy + dropdown.itemH <= visibleBottom) {
                modeOptionHits.add(new ModeOptionHit(dropdown.x, iy, dropdown.w, dropdown.itemH, setting, mode));
            }
            iy += dropdown.itemH;
        }
        canvas.restore();
    }


    private float measureModuleCardHeight(BasicModule module, float w) {
        float h = 32f + 6f;
        for (Setting<?> setting : module.getSettings()) {
            float visibleT = tickVisibility(setting, setting.isHidden());
            if (visibleT <= 0.001f) continue;
            h += getSettingRowHeight(setting, w - 24f) * visibleT + 10f * visibleT;
        }
        return Math.max(60f, h + 6f);
    }

    private float getSettingRowHeight(Setting<?> setting, float w) {
        if (setting instanceof NumberSetting) return 52f;
        if (setting instanceof ModeSetting) return 36f;
        if (setting instanceof BooleanSetting) return 34f;
        if (setting instanceof FoldSetting) return 34f;
        if (setting instanceof ColorSetting s) {
            float expand = COLOR_PICKER_PAD * 2f + COLOR_SB_SIZE;
            return COLOR_ROW_H + expand * getColorAnimT(s);
        }
        if (setting instanceof StringSetting) return 36f;
        if (setting instanceof TextSetting) return 36f;
        if (setting instanceof MultiSelectSetting multi) {
            float chipH = 19f;
            float rowGap = 6f;
            float gap = 6f;
            float maxX = w - 24f;
            float rowX = 0f;
            int rows = 1;
            for (String mode : multi.getModes()) {
                float tw = currentFrame.text().width(FONT_TEXT, 11.5f, mode);
                float chipW = tw + 14f;
                if (rowX == 0f) {
                    rowX = chipW + gap;
                } else if (rowX + chipW > maxX) {
                    rows++;
                    rowX = chipW + gap;
                } else {
                    rowX += chipW + gap;
                }
            }
            return 30f + rows * chipH + (rows - 1) * rowGap + 6f;
        }
        return 28f;
    }

    private void beginFrame() {
        long now = System.nanoTime();
        if (lastFrameNanos == 0L) {
            lastFrameNanos = now;
            deltaSec = 1f / 60f;
            frameId++;
            return;
        }
        deltaSec = (now - lastFrameNanos) / 1_000_000_000f;
        if (deltaSec < 0f) deltaSec = 0f;
        if (deltaSec > 0.05f) deltaSec = 0.05f;
        lastFrameNanos = now;
        frameId++;
    }

    private float smoothTo(float current, float target, float speed) {
        if (deltaSec <= 0f) return current;
        float t = Math.min(1f, speed * deltaSec);
        return current + (target - current) * t;
    }

    private float tickVisibility(Setting<?> setting, boolean hidden) {
        Integer last = visibilityFrame.get(setting);
        boolean visible = !hidden;
        Boolean lastVisible = visibilityState.get(setting);
        visibilityState.put(setting, visible);
        if (last != null && last == frameId) {
            return visibilityAnim.getOrDefault(setting, visible ? 1f : 0f);
        }
        visibilityFrame.put(setting, frameId);
        float current;
        boolean hasAnim = visibilityAnim.containsKey(setting);
        if ((lastVisible == null && visible) || (lastVisible != null && !lastVisible && visible) || (visible && !hasAnim)) {
            current = 0f;
        } else {
            current = visibilityAnim.getOrDefault(setting, visible ? 1f : 0f);
        }
        float target = visible ? 1f : 0f;
        float speed = visible ? 3.5f : 10f;
        float next = smoothTo(current, target, speed);
        if (!visible && next <= 0.001f) {
            visibilityAnim.remove(setting);
            return 0f;
        }
        visibilityAnim.put(setting, next);
        return next;
    }

    private float tickExpand(Object key, boolean expanded) {
        Integer last = expandFrame.get(key);
        Boolean lastState = expandState.get(key);
        expandState.put(key, expanded);
        if (last != null && last == frameId) {
            return expandAnim.getOrDefault(key, expanded ? 1f : 0f);
        }
        expandFrame.put(key, frameId);
        float current;
        if (lastState != null && lastState != expanded) {
            current = lastState ? 1f : 0f;
        } else if (expanded && !expandAnim.containsKey(key)) {
            current = 0f;
        } else {
            current = expandAnim.getOrDefault(key, expanded ? 1f : 0f);
        }
        float target = expanded ? 1f : 0f;
        float next = smoothTo(current, target, 10f);
        if (!expanded && next <= 0.001f) {
            expandAnim.remove(key);
            return 0f;
        }
        expandAnim.put(key, next);
        return next;
    }

    private float tickCardAnim(IdentityHashMap<BasicModule, Float> map, IdentityHashMap<BasicModule, Integer> frameMap,
                               BasicModule key, float target, float speed) {
        Integer last = frameMap.get(key);
        if (last != null && last == frameId) {
            return map.getOrDefault(key, target);
        }
        frameMap.put(key, frameId);
        float current = map.getOrDefault(key, target);
        float next = smoothTo(current, target, speed);
        if (Math.abs(next - target) < 0.001f) {
            map.put(key, target);
            return target;
        }
        map.put(key, next);
        return next;
    }

    private float tickCardPos(IdentityHashMap<BasicModule, Float> map, IdentityHashMap<BasicModule, Integer> frameMap,
                              BasicModule key, float target, float speed) {
        Integer last = frameMap.get(key);
        if (last != null && last == frameId) {
            return map.getOrDefault(key, target);
        }
        frameMap.put(key, frameId);
        float current = map.getOrDefault(key, target);
        float next = smoothTo(current, target, speed);
        if (Math.abs(next - target) < 0.001f) {
            map.put(key, target);
            return target;
        }
        map.put(key, next);
        return next;
    }

    private float tickToggle(Object key, boolean enabled) {
        Integer last = toggleFrame.get(key);
        Boolean lastState = toggleState.get(key);
        toggleState.put(key, enabled);
        if (last != null && last == frameId) {
            return toggleAnim.getOrDefault(key, enabled ? 1f : 0f);
        }
        toggleFrame.put(key, frameId);
        float current;
        if (lastState != null && lastState != enabled) {
            current = lastState ? 1f : 0f;
        } else {
            current = toggleAnim.getOrDefault(key, enabled ? 1f : 0f);
        }
        float target = enabled ? 1f : 0f;
        float next = smoothTo(current, target, 14f);
        if (!enabled && next <= 0.001f) {
            toggleAnim.remove(key);
            return 0f;
        }
        toggleAnim.put(key, next);
        return next;
    }

    private float getModeAnimT(ModeSetting setting) {
        Integer last = modeAnimFrame.get(setting);
        boolean open = openedMode == setting;
        if (last != null && last == frameId) {
            return modeAnim.getOrDefault(setting, open ? 1f : 0f);
        }
        modeAnimFrame.put(setting, frameId);
        float current = modeAnim.getOrDefault(setting, open ? 1f : 0f);
        float target = open ? 1f : 0f;
        float speed = open ? 10f : 16f;
        float next = smoothTo(current, target, speed);
        if (!open && next <= 0.001f) {
            modeAnim.remove(setting);
            return 0f;
        }
        modeAnim.put(setting, next);
        return next;
    }

    private float getColorAnimT(ColorSetting setting) {
        return tickExpand(setting, setting.isExpanded());
    }

    private boolean matchesQuery(BasicModule module, String query) {
        String name = module.getName().toLowerCase(Locale.ROOT);
        String display = module.getDisplayName().toLowerCase(Locale.ROOT);
        if (name.contains(query) || display.contains(query)) return true;
        for (Setting<?> setting : module.getSettings()) {
            if (setting.getName().toLowerCase(Locale.ROOT).contains(query)) return true;
        }
        return false;
    }

    private boolean isModeDropdownClick(float mx, float my) {
        return lastModeDropdownRect != null && lastModeDropdownRect.contains(mx, my);
    }

    private void updateNumberSettingByMouse(NumberSetting setting, float mx, Rect rect) {
        double min = setting.getMinimum().doubleValue();
        double max = setting.getMaximum().doubleValue();
        double inc = setting.getIncrement().doubleValue();
        double t = (mx - rect.getLeft()) / Math.max(1f, rect.getWidth());
        if (t < 0d) t = 0d;
        if (t > 1d) t = 1d;
        double value = min + (max - min) * t;
        if (inc > 0d) {
            value = Math.round(value / inc) * inc;
        }
        setting.setValue(value);
    }

    private void cycleMode(ModeSetting setting, boolean forward) {
        String[] modes = setting.getModes();
        if (modes.length == 0) return;
        int idx = 0;
        for (int i = 0; i < modes.length; i++) {
            if (modes[i].equals(setting.getValue())) {
                idx = i;
                break;
            }
        }
        int next = forward ? (idx + 1) % modes.length : (idx - 1 + modes.length) % modes.length;
        setting.setValue(modes[next]);
    }

    private void drawCheckerboard(Canvas canvas, float x, float y, float w, float h, float r) {
        float size = 4f;
        int c1 = 0x22FFFFFF;
        int c2 = 0x22000000;
        UiDraw.fillRRect(currentFrame, x, y, w, h, r, c1);
        for (float yy = y; yy < y + h; yy += size) {
            for (float xx = x; xx < x + w; xx += size) {
                boolean alt = ((int) ((xx - x) / size) + (int) ((yy - y) / size)) % 2 == 0;
                if (alt) {
                    UiDraw.fillRect(currentFrame, xx, yy, Math.min(size, x + w - xx), Math.min(size, y + h - yy), c2);
                }
            }
        }
    }

    private String maskText(String text) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            sb.append('•');
        }
        return sb.toString();
    }

    private String formatNumberValue(Number value) {
        if (value == null) return "0";
        double v = value.doubleValue();
        if (Math.abs(v - Math.round(v)) < 1e-6) {
            return String.valueOf((long) Math.round(v));
        }
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private int lerpColor(int a, int b, float t) {
        float tt = clamp01(t);
        int aA = (a >>> 24) & 0xFF;
        int aR = (a >>> 16) & 0xFF;
        int aG = (a >>> 8) & 0xFF;
        int aB = a & 0xFF;
        int bA = (b >>> 24) & 0xFF;
        int bR = (b >>> 16) & 0xFF;
        int bG = (b >>> 8) & 0xFF;
        int bB = b & 0xFF;
        int oA = aA + Math.round((bA - aA) * tt);
        int oR = aR + Math.round((bR - aR) * tt);
        int oG = aG + Math.round((bG - aG) * tt);
        int oB = aB + Math.round((bB - aB) * tt);
        return (oA << 24) | (oR << 16) | (oG << 8) | oB;
    }

    private float clamp01(double v) {
        if (v < 0d) return 0f;
        if (v > 1d) return 1f;
        return (float) v;
    }

    private String ellipsize(String text, float maxW) {
        if (text == null || text.isEmpty()) return "";
        if (currentFrame.text().width(FONT_TEXT, 11f, text) <= maxW) return text;
        String dots = "…";
        float dotsW = currentFrame.text().width(FONT_TEXT, 11f, dots);
        int end = text.length();
        while (end > 0) {
            String s = text.substring(0, end);
            if (currentFrame.text().width(FONT_TEXT, 11f, s) + dotsW <= maxW) return s + dots;
            end--;
        }
        return dots;
    }

    private static class ModuleCard {
        final BasicModule module;
        final float x;
        final float y;
        final float w;
        final float h;

        ModuleCard(BasicModule module, float x, float y, float w, float h) {
            this.module = module;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }

    private static class ChipHit {
        final float x;
        final float y;
        final float w;
        final float h;
        final MultiSelectSetting setting;
        final String mode;

        ChipHit(float x, float y, float w, float h, MultiSelectSetting setting, String mode) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.setting = setting;
            this.mode = mode;
        }

        boolean contains(float mx, float my) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }
    }

    private static class ModeOptionHit {
        final float x;
        final float y;
        final float w;
        final float h;
        final ModeSetting setting;
        final String mode;

        ModeOptionHit(float x, float y, float w, float h, ModeSetting setting, String mode) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.setting = setting;
            this.mode = mode;
        }

        boolean contains(float mx, float my) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }
    }

    private enum ColorDragTarget {
        SB,
        HUE,
        ALPHA
    }

    private static class ModeDropdown {
        final ModeSetting setting;
        final float x;
        final float y;
        final float w;
        final float itemH;

        ModeDropdown(ModeSetting setting, float x, float y, float w, float itemH) {
            this.setting = setting;
            this.x = x;
            this.y = y;
            this.w = w;
            this.itemH = itemH;
        }

        Rect rect() {
            float h = Math.min(setting.getModes().length * itemH + 8f, 200f);
            return Rect.makeXYWH(x, y, w, h);
        }
    }

}
