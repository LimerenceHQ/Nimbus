package cc.nimbus.ui.clickgui.neutron.panel;

import cc.nimbus.ui.clickgui.neutron.NeutronActions;
import cc.nimbus.ui.clickgui.neutron.NeutronState;
import cc.nimbus.ui.clickgui.neutron.widget.UiDraw;
import io.github.humbleui.skija.Canvas;

public class NeutronContentPanel extends NeutronPanel {
    private static final int CARD_BG = 0x22000000;
    private static final int CARD_STROKE = 0x22FFFFFF;
    private static final float PAGE_PAD = 18f;
    private static final float INNER_PAD = 12f;

    private final NeutronState state;
    private final NeutronSettingsPanel settingsPanel;
    private final NeutronConfigPanel configPanel;
    private NeutronState.Page lastPage;

    public NeutronContentPanel(NeutronState state, NeutronActions actions) {
        this.state = state;
        this.settingsPanel = new NeutronSettingsPanel(state, actions);
        this.configPanel = new NeutronConfigPanel();
    }

    @Override
    public void render(Canvas canvas, float mx, float my, boolean leftDown, boolean rightDown, boolean leftPressed, double scrolledDelta) {
        lastPage = state.page;
        float contentX = x + PAGE_PAD;
        float contentY = y + PAGE_PAD;
        float contentW = width - PAGE_PAD * 2f;
        float contentH = height - PAGE_PAD * 2f;
        float animOffset = 0f;
        float animT = 1f;
        if (state.pageAnimStartMs > 0L) {
            long dt = System.currentTimeMillis() - state.pageAnimStartMs;
            animT = Math.min(1f, dt / 180f);
            animOffset = (1f - animT) * 12f;
            if (animT >= 1f) {
                state.pageAnimStartMs = 0L;
            }
        }
        canvas.save();
        canvas.translate(0f, animOffset);

        if (state.page == NeutronState.Page.MODULES) {
            settingsPanel.setBounds(contentX, contentY, contentW, contentH);
            settingsPanel.render(canvas, mx, my, leftDown, rightDown, leftPressed, scrolledDelta);
            canvas.restore();
            return;
        }

        UiDraw.fillRRect(canvas, contentX, contentY, contentW, contentH, 14f, CARD_BG);
        UiDraw.strokeRRect(canvas, contentX, contentY, contentW, contentH, 14f, 1f, CARD_STROKE);
        float innerX = contentX + INNER_PAD;
        float innerY = contentY + INNER_PAD;
        float innerW = contentW - INNER_PAD * 2f;
        float innerH = contentH - INNER_PAD * 2f;
        if (state.page == NeutronState.Page.CONFIG) {
            configPanel.setBounds(innerX, innerY, innerW, innerH);
            configPanel.render(canvas, mx, my, leftDown, rightDown, leftPressed, scrolledDelta);
        }
        canvas.restore();
    }

    public boolean middleClick(float mx, float my) {
        if (state.page != NeutronState.Page.MODULES) return false;
        return settingsPanel.middleClick(mx, my);
    }

    public boolean handleKeyInput(int key, int scanCode, int modifiers) {
        if (state.page == NeutronState.Page.CONFIG) {
            return configPanel.handleKeyInput(key);
        }
        return false;
    }

    public boolean handleCharInput(char codePoint, int modifiers) {
        if (state.page == NeutronState.Page.CONFIG) {
            return configPanel.handleCharInput(codePoint);
        }
        return false;
    }
}
