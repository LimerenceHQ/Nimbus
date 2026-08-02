package cc.nimbus.ui.alt;

import cc.nimbus.core.Core;
import cc.nimbus.managers.AccountManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import cc.nimbus.ui.alt.model.*;

public final class AccountScreen extends Screen {
    private final Screen parent;
    private Alt selected;
    private EditBox name, token;
    private Button login, delete;
    private String status = "等待操作";
    private int listScroll;

    public AccountScreen(Screen parent) {
        super(Component.literal("Alt Manager"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = Math.max(12, width / 2 - Math.min(140, width / 2 - 12));
        int top = formTop();
        name = field(left, top, 280, "用户名");
        token = field(left, top + 24, 280, "Token（原始账号）");

        int buttonY = top + 52;
        addRenderableWidget(Button.builder(Component.literal("添加离线"), b -> addOffline()).bounds(left, buttonY, 135, 20).build());
        addRenderableWidget(Button.builder(Component.literal("添加 Token"), b -> addOriginal()).bounds(left + 145, buttonY, 135, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Microsoft 登录"), b -> startMicrosoft()).bounds(left, buttonY + 26, 135, 20).build());
        login = addRenderableWidget(Button.builder(Component.literal("登录选中"), b -> loginSelected()).bounds(left + 145, buttonY + 26, 135, 20).build());
        delete = addRenderableWidget(Button.builder(Component.literal("删除选中"), b -> deleteSelected()).bounds(left, buttonY + 52, 135, 20).build());
        addRenderableWidget(Button.builder(Component.literal("返回"), b -> onClose()).bounds(left + 145, buttonY + 52, 135, 20).build());
        setInitialFocus(name);
        updateButtons();
    }

    private EditBox field(int x, int y, int width, String hint) {
        EditBox box = addRenderableWidget(new EditBox(font, x, y, width, 20, Component.literal(hint)));
        box.setHint(Component.literal(hint));
        box.setMaxLength(512);
        return box;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (minecraft.level == null) extractPanorama(graphics, partialTick); else extractBlurredBackground(graphics);
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), 0xCC0B1015);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int panelLeft = width / 2 - 160;
        int panelRight = width / 2 + 160;
        int listTop = 86;
        int listBottom = formTop() - 26;
        graphics.fill(panelLeft, 16, panelRight, height - 16, 0xE9161D25);
        graphics.fill(panelLeft, 16, panelLeft + 4, height - 16, 0xFF39C59A);
        graphics.text(font, title, panelLeft + 16, 28, 0xFFFFFFFF, false);
        graphics.text(font, "当前: " + minecraft.getUser().getName(), panelLeft + 16, 48, 0xFF9AA6B2, false);
        graphics.text(font, "选择账号", panelLeft + 16, 68, 0xFFB9C4CC, false);
        graphics.text(font, status, panelRight - 150, 68, 0xFFB9C4CC, false);
        int y = listTop - listScroll;
        for (Alt alt : accounts().getAccounts()) {
            if (y + 28 >= listTop && y <= listBottom) {
            boolean active = alt == selected;
            graphics.fill(panelLeft + 14, y, panelRight - 14, y + 28, active ? 0xFF25483F : 0x66232B33);
            graphics.text(font, alt.getUserName(), panelLeft + 24, y + 6, 0xFFF1F5F7, false);
            graphics.text(font, alt.getAccountType().getWriteName(), panelRight - 120, y + 6, 0xFF9AA6B2, false);
            }
            y += 32;
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && event.x() >= width / 2 - 146 && event.x() <= width / 2 + 146 && event.y() >= 86 && event.y() < formTop() - 26) {
            int index = ((int) event.y() - 86 + listScroll) / 32;
            if (index >= 0 && index < accounts().getAccounts().size()) selected = accounts().getAccounts().get(index);
            updateButtons();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        int visibleHeight = Math.max(0, formTop() - 26 - 86);
        int contentHeight = accounts().getAccounts().size() * 32;
        int maxScroll = Math.max(0, contentHeight - visibleHeight);
        listScroll = Math.max(0, Math.min(maxScroll, listScroll - (int) (vertical * 32)));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(event);
    }

    private void addBasic() {
        String user = name.getValue().trim();
        if (user.isEmpty()) { status = "请输入用户名"; return; }
        Alt alt = new OfflineAlt(user);
        accounts().addAccount(alt); selected = alt; status = "已添加 " + user; save(); updateButtons();
    }

    private void addOffline() { addBasic(); }

    private void addOriginal() {
        String user = name.getValue().trim(), access = token.getValue().trim();
        if (user.isEmpty() || access.isEmpty()) { status = "请输入用户名和 Token"; return; }
        OriginalAlt alt = new OriginalAlt(user, access, "");
        accounts().addAccount(alt); selected = alt; status = "已添加原始账号"; save(); updateButtons();
    }

    private void loginSelected() {
        if (selected == null) return;
        status = "正在登录..."; updateButtons();
        accounts().login(
                selected,
                result -> minecraft.execute(() -> loginSucceeded(result)),
                value -> minecraft.execute(() -> { status = value; updateButtons(); })
        );
    }

    private void startMicrosoft() {
        status = "正在打开 Microsoft 登录..."; updateButtons();
        accounts().loginMicrosoft(
                result -> minecraft.execute(() -> loginSucceeded(result)),
                value -> minecraft.execute(() -> { status = value; updateButtons(); })
        );
    }

    private void loginSucceeded(AccountManager.LoginResult result) {
        selected = result.account();
        status = result.saved()
                ? "登录成功: " + result.account().getUserName()
                : "已登录，但账号保存失败";
        updateButtons();
    }

    private void deleteSelected() { if (selected != null) { accounts().removeAccount(selected); status = "已删除"; selected = null; save(); updateButtons(); } }
    private void updateButtons() { if (login != null) login.active = selected != null; if (delete != null) delete.active = selected != null; }
    private void save() { if (!accounts().save()) status = "保存失败"; }
    private AccountManager accounts() { return Core.INSTANCE.getAccountManager(); }

    private int formTop() { return Math.max(220, Math.min(height - 210, (int) (height * 0.58f))); }

    @Override public void onClose() { accounts().cancelMicrosoftLogin(); save(); minecraft.setScreenAndShow(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
