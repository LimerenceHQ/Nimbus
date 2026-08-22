package cc.nimbus.ui.alt;

import cc.nimbus.core.Core;
import cc.nimbus.managers.AccountManager;
import cc.nimbus.ui.alt.model.Alt;
import cc.nimbus.ui.alt.model.OfflineAlt;
import cc.nimbus.ui.alt.model.OriginalAlt;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class AccountScreen extends Screen {
    private final Screen parent;
    private Alt selected;
    private EditBox name;
    private EditBox token;
    private Button login;
    private Button delete;
    private String status = tr("nimbus.account.status.ready", "Ready");
    private int listScroll;

    public AccountScreen(Screen parent) {
        super(component("nimbus.account.title", "Alt Manager"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = Math.max(12, width / 2 - Math.min(140, width / 2 - 12));
        int top = formTop();
        name = field(left, top, 280, tr("nimbus.account.username", "Username"));
        token = field(left, top + 24, 280, tr("nimbus.account.token", "Token (token account)"));

        int buttonY = top + 52;
        addRenderableWidget(Button.builder(component("nimbus.account.add_offline", "Add Offline"),
                button -> addOffline()).bounds(left, buttonY, 135, 20).build());
        addRenderableWidget(Button.builder(component("nimbus.account.add_token", "Add Token"),
                button -> addOriginal()).bounds(left + 145, buttonY, 135, 20).build());
        addRenderableWidget(Button.builder(component("nimbus.account.microsoft_login", "Microsoft Login"),
                button -> startMicrosoft()).bounds(left, buttonY + 26, 135, 20).build());
        login = addRenderableWidget(Button.builder(component("nimbus.account.login_selected", "Login Selected"),
                button -> loginSelected()).bounds(left + 145, buttonY + 26, 135, 20).build());
        delete = addRenderableWidget(Button.builder(component("nimbus.account.delete_selected", "Delete Selected"),
                button -> deleteSelected()).bounds(left, buttonY + 52, 135, 20).build());
        addRenderableWidget(Button.builder(component("nimbus.account.back", "Back"),
                button -> onClose()).bounds(left + 145, buttonY + 52, 135, 20).build());
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
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (minecraft.level == null) renderPanorama(graphics, partialTick);
        else renderBlurredBackground();
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), 0xCC0B1015);
        int panelLeft = width / 2 - 160;
        int panelRight = width / 2 + 160;
        int listTop = 86;
        int listBottom = formTop() - 26;
        graphics.fill(panelLeft, 16, panelRight, height - 16, 0xE9161D25);
        graphics.fill(panelLeft, 16, panelLeft + 4, height - 16, 0xFF39C59A);
        graphics.drawString(font, title, panelLeft + 16, 28, 0xFFFFFFFF, false);
        graphics.drawString(font, tr("nimbus.account.current", "Current: %s", minecraft.getUser().getName()),
                panelLeft + 16, 48, 0xFF9AA6B2, false);
        graphics.drawString(font, tr("nimbus.account.select", "Select account"),
                panelLeft + 16, 68, 0xFFB9C4CC, false);
        graphics.drawString(font, status, panelRight - 150, 68, 0xFFB9C4CC, false);
        int y = listTop - listScroll;
        for (Alt alt : accounts().getAccounts()) {
            if (y + 28 >= listTop && y <= listBottom) {
                boolean active = alt == selected;
                graphics.fill(panelLeft + 14, y, panelRight - 14, y + 28,
                        active ? 0xFF25483F : 0x66232B33);
                graphics.drawString(font, alt.getUserName(), panelLeft + 24, y + 6, 0xFFF1F5F7, false);
                graphics.drawString(font, alt.getAccountType().getWriteName(), panelRight - 120, y + 6,
                        0xFF9AA6B2, false);
            }
            y += 32;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && x >= width / 2 - 146 && x <= width / 2 + 146
                && y >= 86 && y < formTop() - 26) {
            int index = ((int) y - 86 + listScroll) / 32;
            if (index >= 0 && index < accounts().getAccounts().size()) {
                selected = accounts().getAccounts().get(index);
            }
            updateButtons();
            return true;
        }
        return super.mouseClicked(x, y, button);
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void addOffline() {
        String user = name.getValue().trim();
        if (user.isEmpty()) {
            status = tr("nimbus.account.status.enter_username", "Enter a username");
            return;
        }
        Alt alt = new OfflineAlt(user);
        accounts().addAccount(alt);
        selected = alt;
        status = tr("nimbus.account.status.added", "Added %s", user);
        save();
        updateButtons();
    }

    private void addOriginal() {
        String user = name.getValue().trim();
        String access = token.getValue().trim();
        if (user.isEmpty() || access.isEmpty()) {
            status = tr("nimbus.account.status.enter_token", "Enter a username and token");
            return;
        }
        OriginalAlt alt = new OriginalAlt(user, access, "");
        accounts().addAccount(alt);
        selected = alt;
        status = tr("nimbus.account.status.token_added", "Token account added");
        save();
        updateButtons();
    }

    private void loginSelected() {
        if (selected == null) return;
        status = tr("nimbus.account.status.logging_in", "Signing in...");
        updateButtons();
        accounts().login(
                selected,
                result -> minecraft.execute(() -> loginSucceeded(result)),
                value -> minecraft.execute(() -> {
                    status = value;
                    updateButtons();
                })
        );
    }

    private void startMicrosoft() {
        status = tr("nimbus.account.status.opening_microsoft", "Opening Microsoft sign-in...");
        updateButtons();
        accounts().loginMicrosoft(
                result -> minecraft.execute(() -> loginSucceeded(result)),
                value -> minecraft.execute(() -> {
                    status = value;
                    updateButtons();
                })
        );
    }

    private void loginSucceeded(AccountManager.LoginResult result) {
        selected = result.account();
        status = result.saved()
                ? tr("nimbus.account.status.success", "Signed in: %s", result.account().getUserName())
                : tr("nimbus.account.status.save_warning", "Signed in, but the account could not be saved");
        updateButtons();
    }

    private void deleteSelected() {
        if (selected == null) return;
        accounts().removeAccount(selected);
        status = tr("nimbus.account.status.deleted", "Deleted");
        selected = null;
        save();
        updateButtons();
    }

    private void updateButtons() {
        if (login != null) login.active = selected != null;
        if (delete != null) delete.active = selected != null;
    }

    private void save() {
        if (!accounts().save()) status = tr("nimbus.account.status.save_failed", "Save failed");
    }

    private AccountManager accounts() {
        return Core.INSTANCE.getAccountManager();
    }

    private int formTop() {
        return Math.max(220, Math.min(height - 210, (int) (height * 0.58f)));
    }

    private static Component component(String key, String fallback) {
        return Component.literal(tr(key, fallback));
    }

    private static String tr(String key, String fallback, Object... arguments) {
        return Core.INSTANCE.isInitialized()
                ? Core.INSTANCE.getI18nManager().get(key, fallback, arguments) : fallback;
    }

    @Override
    public void onClose() {
        accounts().cancelMicrosoftLogin();
        save();
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
