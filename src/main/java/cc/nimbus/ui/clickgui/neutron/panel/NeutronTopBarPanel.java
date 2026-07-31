package cc.nimbus.ui.clickgui.neutron.panel;

import cc.nimbus.ui.clickgui.neutron.NeutronActions;
import cc.nimbus.ui.clickgui.neutron.NeutronState;
import cc.nimbus.ui.clickgui.neutron.widget.*;
import cc.nimbus.utils.render.skija.text.factory.TextFactory;
import cc.nimbus.utils.render.skija.draw.SkijaIcons;
import io.github.humbleui.skija.Canvas;

public class NeutronTopBarPanel extends NeutronPanel {
    private static final String FONT_TEXT = "SarasaUiSC-Regular";
    private static final String FONT_BOLD = "SarasaUiSC-SemiBold";
    private static final int DIVIDER = 0x1CFFFFFF;

    private final NeutronState state;
    private final NeutronActions actions;

    public NeutronTopBarPanel(NeutronState state, NeutronActions actions) {
        this.state = state;
        this.actions = actions;
    }

    @Override
    public void render(Canvas canvas, float mx, float my, boolean leftDown, boolean rightDown, boolean leftPressed, double scrolledDelta) {
        UiDraw.fillRect(canvas, x, y + height - 1f, width, 1f, DIVIDER);

        float pad = 20f;
        float btnH = 30f;
        float btnW = 124f;
        float btnX = x + pad;
        float btnY = y + (height - btnH) / 2f;
        boolean btnHover = mx >= btnX && mx <= btnX + btnW && my >= btnY && my <= btnY + btnH;
        UiDraw.fillRRect(canvas, btnX, btnY, btnW, btnH, 13f, btnHover ? UiColors.HOVER : 0x14000000);
        UiDraw.strokeRRect(canvas, btnX, btnY, btnW, btnH, 13f, 1f, UiColors.STROKE);
        UiPaints.TEXT.setColor(UiColors.TEXT_PRIMARY);
        UiText.drawXY(canvas, FONT_BOLD, 12.5f, "快速保存", btnX, btnY, btnW, btnH, UiPaints.TEXT);

        if (btnHover && leftPressed) {
            actions.quickSave();
        }

        float searchW = 240f;
        float searchH = 32f;
        float searchX = x + width - pad - searchW;
        float searchY = y + (height - searchH) / 2f;
        boolean searchHover = mx >= searchX && mx <= searchX + searchW && my >= searchY && my <= searchY + searchH;

        if (searchHover && leftPressed) {
            actions.setSearchFocused(true);
        } else if (leftPressed && !searchHover) {
            actions.setSearchFocused(false);
        }

        UiDraw.fillRRect(canvas, searchX, searchY, searchW, searchH, 13f, state.searchFocused ? UiColors.ACTIVE : 0x14000000);
        UiDraw.strokeRRect(canvas, searchX, searchY, searchW, searchH, 13f, 1f, UiColors.STROKE);

        float iconSize = 15f;
        float ix = searchX + 10f;
        float iy = searchY + (searchH - iconSize) / 2f;
        UiPaints.FILL.setColor(state.searchFocused ? UiTheme.accent() : UiColors.TEXT_SECONDARY);
        SkijaIcons.drawSvgTopLeft(canvas, SkijaIcons.SEARCH_ICON, ix, iy, iconSize, UiPaints.FILL);

        String text = state.searchText.isEmpty() ? "搜索参数" : state.searchText.toString();
        UiPaints.TEXT.setColor(state.searchText.isEmpty() ? UiColors.TEXT_SECONDARY : UiColors.TEXT_PRIMARY);
        UiText.drawLeftY(canvas, FONT_BOLD, 12.5f, text, searchX + 32f, searchY, searchH, UiPaints.TEXT);

        if (state.searchFocused) {
            actions.updateCaret();
            if (state.caretVisible) {
                float tw = TextFactory.getTextWidth(FONT_BOLD, 12.5f, state.searchText.toString());
                float cx = searchX + 32f + tw + 2f;
                float cy = searchY + 8f;
                UiDraw.fillRect(canvas, cx, cy, 1.2f, searchH - 16f, 0xCCFFFFFF);
            }
        }

        if (state.toast != null && System.currentTimeMillis() < state.toastUntilMs) {
            UiPaints.TEXT.setColor(UiColors.TEXT_SECONDARY);
            UiText.drawLeftY(canvas, FONT_TEXT, 12f, state.toast, btnX + btnW + 12f, btnY, btnH, UiPaints.TEXT);
        }
    }
}
