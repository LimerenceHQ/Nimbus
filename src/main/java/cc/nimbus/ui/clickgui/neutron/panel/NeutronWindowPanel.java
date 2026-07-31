package cc.nimbus.ui.clickgui.neutron.panel;

import cc.nimbus.ui.clickgui.neutron.NeutronActions;
import cc.nimbus.ui.clickgui.neutron.NeutronState;
import cc.nimbus.ui.clickgui.neutron.widget.UiDraw;
import cc.nimbus.utils.render.skija.api.SkijaFrame;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.types.RRect;

public class NeutronWindowPanel extends NeutronPanel {
    private static final int LEFT_OVERLAY = 0xB01B1E22;
    private static final int RIGHT_OVERLAY = 0x9A0A0B0D;

    private final NeutronSidebarPanel sidebar;
    private final NeutronTopBarPanel topBar;
    private final NeutronContentPanel content;

    public NeutronWindowPanel(NeutronState state, NeutronActions actions) {
        this.sidebar = new NeutronSidebarPanel(state, actions);
        this.topBar = new NeutronTopBarPanel(state, actions);
        this.content = new NeutronContentPanel(state, actions);
    }

    @Override
    public void render(SkijaFrame frame, float mx, float my, boolean leftDown, boolean rightDown, boolean leftPressed, double scrolledDelta) {
        Canvas canvas = frame.canvas();
        float winW = Math.min(1120f, width - 120f);
        float winH = Math.min(700f, height - 120f);
        float winX = x + (width - winW) / 2f;
        float winY = y + (height - winH) / 2f;
        float radius = 16f;

        float leftW = 260f;
        float rightW = winW - leftW;

        float leftX = winX;
        float rightX = winX + leftW;
        float leftY = winY;
        float rightY = winY;

        frame.postProcess().add(RRect.makeXYWH(winX, winY, winW, winH, radius));
        frame.postProcess().draw();
        canvas.save();
        UiDraw.clipRRect(frame, winX, winY, winW, winH, radius);
        UiDraw.fillRect(frame, leftX, leftY, leftW, winH, LEFT_OVERLAY);
        UiDraw.fillRect(frame, rightX, rightY, rightW, winH, RIGHT_OVERLAY);
        canvas.restore();

        // Minecraft composites this premultiplied Skija texture as a regular GUI texture.
        // An opaque surface-colored edge prevents transparent black from bleeding into the AA rim.
        UiDraw.strokeRRect(frame, winX + 0.5f, winY + 0.5f,
                winW - 1f, winH - 1f, radius - 0.5f, 1f, 0xFF292D30);

        UiDraw.fillRect(frame, rightX, rightY, 1f, winH, 0x22FFFFFF);

        float topBarH = 52f;

        sidebar.setBounds(leftX, leftY, leftW, winH);
        topBar.setBounds(rightX, rightY, rightW, topBarH);
        content.setBounds(rightX, rightY + topBarH, rightW, winH - topBarH);

        sidebar.render(frame, mx, my, leftDown, rightDown, leftPressed, scrolledDelta);
        topBar.render(frame, mx, my, leftDown, rightDown, leftPressed, scrolledDelta);
        content.render(frame, mx, my, leftDown, rightDown, leftPressed, scrolledDelta);
    }

    public boolean middleClick(float mx, float my) {
        return content.middleClick(mx, my);
    }

    public boolean handleKeyInput(int key, int scanCode, int modifiers) {
        return content.handleKeyInput(key, scanCode, modifiers);
    }

    public boolean handleCharInput(char codePoint, int modifiers) {
        return content.handleCharInput(codePoint, modifiers);
    }


}
