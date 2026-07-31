package cc.nimbus.ui.clickgui.neutron.panel;

import io.github.humbleui.skija.Canvas;
import org.lwjgl.glfw.GLFW;
import cc.nimbus.core.Core;
import cc.nimbus.managers.ConfigManager;
import cc.nimbus.ui.clickgui.neutron.widget.*;
import cc.nimbus.utils.render.skija.text.factory.TextFactory;

import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class NeutronConfigPanel extends NeutronPanel {
    private static final String FONT_TITLE = "SarasaUiSC-SemiBold";
    private static final String FONT_TEXT = "SarasaUiSC-Regular";
    private static final float INPUT_H = 38f;
    private static final float BUTTON_H = 34f;
    private static final float ROW_H = 28f;

    private List<String> configList = new ArrayList<>();
    private String configSelected;
    private String configInput = "";
    private boolean inputFocused;
    private boolean cursorVisible = true;
    private long lastBlink;
    private float scroll;
    private float targetScroll;
    private long lastRefresh;
    private String toast = "";
    private long toastUntil;
    private long deleteConfirmUntil;
    private String deleteCandidate;
    private long openDirCooldownUntil;

    public NeutronConfigPanel() { refreshConfigs(); }

    @Override
    public void render(Canvas canvas, float mx, float my, boolean leftDown, boolean rightDown, boolean leftPressed, double scrolledDelta) {
        long now = System.currentTimeMillis();
        if (now - lastRefresh > 1000L) { refreshConfigs(); lastRefresh = now; }
        float headerH = 42f, inputY = y + headerH + 8f, inputW = width * .55f, gap = 8f;
        UiPaints.TEXT.setColor(UiColors.TEXT_PRIMARY);
        UiText.drawLeftY(canvas, FONT_TITLE, 22f, "CONFIG", x + 6f, y - 2f, headerH, UiPaints.TEXT);
        float titleW = TextFactory.getTextWidth(FONT_TITLE, 22f, "CONFIG");
        UiPaints.TEXT.setColor(UiColors.TEXT_SECONDARY);
        UiText.drawLeftY(canvas, FONT_TITLE, 16.5f, "当前配置: " + configs().getCurrentConfig(), x + titleW + 18f, y - 2f, headerH, UiPaints.TEXT);
        boolean inputHover = mx >= x && mx <= x + inputW && my >= inputY && my <= inputY + INPUT_H;
        if (leftPressed) inputFocused = inputHover;
        UiDraw.fillRRect(canvas, x, inputY, inputW, INPUT_H, 10f, inputFocused ? 0x22000000 : 0x14000000);
        UiDraw.strokeRRect(canvas, x, inputY, inputW, INPUT_H, 10f, 1f, UiColors.STROKE);
        String display = configInput.isEmpty() && !inputFocused ? "配置名..." : configInput;
        UiPaints.TEXT.setColor(configInput.isEmpty() && !inputFocused ? UiColors.TEXT_SECONDARY : UiColors.TEXT_PRIMARY);
        UiText.drawLeftY(canvas, FONT_TEXT, 14.5f, ellipsize(display, inputW - 20f), x + 10f, inputY, INPUT_H, UiPaints.TEXT);
        if (now - lastBlink > 500) { cursorVisible = !cursorVisible; lastBlink = now; }
        if (inputFocused && cursorVisible) UiDraw.fillRect(canvas, x + 10f + Math.min(TextFactory.getTextWidth(FONT_TEXT, 14.5f, configInput), inputW - 20f), inputY + 7f, 1f, INPUT_H - 14f, UiColors.TEXT_PRIMARY);

        float buttonW = (width - inputW - gap * 4f) / 4f, buttonX = x + inputW + gap, buttonY = inputY + 2f;
        if (button(canvas, "打开", buttonX, buttonY, buttonW, mx, my, leftPressed)) openConfigDirectory(); buttonX += buttonW + gap;
        if (button(canvas, "加载", buttonX, buttonY, buttonW, mx, my, leftPressed)) loadConfig(); buttonX += buttonW + gap;
        if (button(canvas, "保存", buttonX, buttonY, buttonW, mx, my, leftPressed)) saveConfig(); buttonX += buttonW + gap;
        if (button(canvas, deleteLabel(), buttonX, buttonY, buttonW, mx, my, leftPressed)) deleteConfig();

        float listY = inputY + INPUT_H + 10f, listH = height - (listY - y) - 10f;
        if (scrolledDelta != 0 && mx >= x && mx <= x + width && my >= listY && my <= listY + listH) targetScroll += (float) scrolledDelta * 30f;
        float maxScroll = Math.max(0f, configList.size() * ROW_H - listH);
        targetScroll = Math.max(-maxScroll, Math.min(0f, targetScroll)); scroll += (targetScroll - scroll) * .2f;
        UiDraw.fillRRect(canvas, x, listY, width, listH, 10f, 0x14000000); UiDraw.strokeRRect(canvas, x, listY, width, listH, 10f, 1f, UiColors.STROKE);
        canvas.save(); UiDraw.clipRRect(canvas, x, listY, width, listH, 10f);
        float rowY = listY + scroll;
        for (String name : configList) {
            boolean hover = mx >= x + 6f && mx <= x + width - 6f && my >= rowY && my <= rowY + ROW_H, active = name.equalsIgnoreCase(configSelected);
            if (hover || active) UiDraw.fillRRect(canvas, x + 6f, rowY + 4f, width - 12f, ROW_H - 4f, 8f, active ? UiTheme.withAlpha(UiTheme.accent(), 0x55) : UiColors.HOVER);
            UiPaints.TEXT.setColor(active ? UiColors.TEXT_PRIMARY : UiColors.TEXT_SECONDARY); UiText.drawLeftY(canvas, FONT_TEXT, 16f, name, x + 14f, rowY + 2f, ROW_H, UiPaints.TEXT);
            if (hover && leftPressed) { configSelected = name; configInput = name; } rowY += ROW_H;
        }
        if (configList.isEmpty()) { UiPaints.TEXT.setColor(UiColors.TEXT_SECONDARY); UiText.drawXY(canvas, FONT_TEXT, 13f, "暂无配置", x, listY, width, listH, UiPaints.TEXT); }
        canvas.restore();
        if (!toast.isEmpty() && now < toastUntil) { UiPaints.TEXT.setColor(UiColors.TEXT_SECONDARY); TextFactory.drawText(canvas, FONT_TEXT, 13f, toast, x + (width - TextFactory.getTextWidth(FONT_TEXT, 13f, toast)) / 2f, y + height - 18f, UiPaints.TEXT); }
    }

    public boolean handleKeyInput(int key) { if (!inputFocused) return false; if (key == GLFW.GLFW_KEY_ESCAPE) { inputFocused = false; return true; } if (key == GLFW.GLFW_KEY_BACKSPACE && !configInput.isEmpty()) configInput = configInput.substring(0, configInput.length() - 1); if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) saveConfig(); return true; }
    public boolean handleCharInput(char c) { if (!inputFocused || Character.isISOControl(c) || configInput.length() >= 32) return inputFocused; if (Character.isLetterOrDigit(c) || c == '_' || c == '-') configInput += c; return true; }
    private void refreshConfigs() { configList = new ArrayList<>(configs().getConfigs()); configList.sort(Comparator.comparing(v -> v.toLowerCase(Locale.ROOT))); if (configSelected != null && configList.stream().noneMatch(v -> v.equalsIgnoreCase(configSelected))) configSelected = null; }
    private void saveConfig() { String name = ConfigManager.sanitizeConfigName(configInput == null ? "" : configInput.trim()); if (name == null || name.isEmpty()) { showToast("配置名无效"); return; } if (configs().saveConfig(name)) { configSelected = name; configInput = name; refreshConfigs(); showToast("已保存"); } else showToast("保存失败"); }
    private void loadConfig() { if (configSelected != null && configs().loadConfig(configSelected)) { configInput = configSelected; showToast("已加载"); } else showToast("请选择配置"); }
    private void deleteConfig() { if (configSelected == null) { showToast("请选择配置"); return; } String key = "local:" + configSelected; if (!key.equals(deleteCandidate) || System.currentTimeMillis() >= deleteConfirmUntil) { deleteCandidate = key; deleteConfirmUntil = System.currentTimeMillis() + 2000L; showToast("再次点击确认删除"); return; } if (configs().deleteConfig(configSelected)) { configSelected = null; configInput = ""; refreshConfigs(); showToast("已删除"); } }
    private void openConfigDirectory() { try { if (System.currentTimeMillis() < openDirCooldownUntil) return; openDirCooldownUntil = System.currentTimeMillis() + 1000L; File dir = configs().getConfigDir(); if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(dir); else Runtime.getRuntime().exec(new String[]{"explorer.exe", dir.getAbsolutePath()}); } catch (Exception ignored) {} }
    private ConfigManager configs() { return Core.INSTANCE.getConfigManager(); }
    private void showToast(String value) { toast = value; toastUntil = System.currentTimeMillis() + 2500L; }
    private boolean button(Canvas c, String label, float bx, float by, float bw, float mx, float my, boolean pressed) { boolean hover = mx >= bx && mx <= bx + bw && my >= by && my <= by + BUTTON_H; UiDraw.fillRRect(c, bx, by, bw, BUTTON_H, 10f, hover ? 0x22FFFFFF : 0x14000000); UiDraw.strokeRRect(c, bx, by, bw, BUTTON_H, 10f, 1f, 0x33FFFFFF); UiPaints.TEXT.setColor(0xFFFFFFFF); UiText.drawXY(c, FONT_TEXT, 14f, label, bx, by, bw, BUTTON_H, UiPaints.TEXT); return hover && pressed; }
    private String deleteLabel() { return deleteCandidate != null && System.currentTimeMillis() < deleteConfirmUntil ? "确认" : "删除"; }
    private String ellipsize(String value, float max) { if (TextFactory.getTextWidth(FONT_TEXT, 14.5f, value) <= max) return value; String out = value; while (!out.isEmpty() && TextFactory.getTextWidth(FONT_TEXT, 14.5f, out + "...") > max) out = out.substring(0, out.length() - 1); return out + "..."; }
}
