package cc.nimbus.ui.gateway;

import cc.nimbus.core.Core;
import cc.nimbus.features.gateway.model.BujiIslandSnapshot;
import cc.nimbus.features.gateway.model.GatewayAccount;
import cc.nimbus.features.gateway.model.GatewayCaptcha;
import cc.nimbus.features.gateway.model.GatewayRole;
import cc.nimbus.features.gateway.model.WhiteClientInstallation;
import cc.nimbus.features.gateway.model.WhiteClientManifest;
import cc.nimbus.features.gateway.launch.GatewayJoinAuthenticator;
import cc.nimbus.features.gateway.launch.GatewayLaunchTarget;
import cc.nimbus.gateway.GatewaySession;
import cc.nimbus.gateway.GatewayState;
import cc.nimbus.managers.GatewayManager;
import cc.nimbus.features.gateway.net.GatewayProtocolException;
import cc.nimbus.ui.screen.AbstractSkijaScreen;
import cc.nimbus.utils.render.font.NimbusFonts;
import cc.nimbus.utils.render.skija.api.SkijaFrame;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import io.github.humbleui.skija.Image;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.nio.file.Path;

/** Development-only diagnostics page for Nimbus Gateway authentication and Buji Island queries. */
public final class GatewayDiagnosticsScreen extends AbstractSkijaScreen {
    private static final int MAX_COOKIE_LENGTH = 1_048_576;
    private final net.minecraft.client.gui.screens.Screen parent;
    private EditBox email;
    private EditBox emailPassword;
    private EditBox cookie;
    private EditBox channelUser;
    private EditBox channelPassword;
    private EditBox captcha;
    private EditBox roleName;
    private final List<ActionButton> actionButtons = new java.util.ArrayList<>();
    private final java.util.Set<String> disabledButtons = new java.util.HashSet<>();
    private String status = "Ready";
    private GatewayCaptcha captchaChallenge;
    private BujiIslandSnapshot server;
    private int selectedRole;
    private WhiteClientManifest manifest;
    private WhiteClientInstallation installation;
    private GatewaySession session;

    public GatewayDiagnosticsScreen() {
        this(null);
    }

    public GatewayDiagnosticsScreen(net.minecraft.client.gui.screens.Screen parent) {
        super(Component.literal("Nimbus Gateway Diagnostics"), "nimbus-gateway-diagnostics");
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = Math.max(16, width / 2 - 310);
        int top = 72;
        email = field(left, top, 280, "网易邮箱");
        emailPassword = field(left, top + 25, 280, "网易密码");
        emailPassword.setFormatter((value, cursor) -> FormattedCharSequence.forward("*".repeat(value.length()), Style.EMPTY));
        actionButtons.add(new ActionButton("email", left, top + 51, 135, 20, "网易邮箱登录"));

        cookie = field(left, top + 105, 280, "X19 SAuth Cookie / sauth_json");
        cookie.setMaxLength(MAX_COOKIE_LENGTH);
        actionButtons.add(new ActionButton("cookie", left, top + 131, 135, 20, "Cookie 登录"));

        int right = Math.min(width - 296, width / 2 + 20);
        channelUser = field(right, top, 280, "4399 用户名");
        channelPassword = field(right, top + 25, 280, "4399 密码");
        channelPassword.setFormatter((value, cursor) -> FormattedCharSequence.forward("*".repeat(value.length()), Style.EMPTY));
        captcha = field(right, top + 50, 280, "验证码（如需要）");
        actionButtons.add(new ActionButton("captcha", right, top + 76, 135, 20, "获取 4399 验证码"));
        actionButtons.add(new ActionButton("channel", right + 145, top + 76, 135, 20, "4399 登录"));

        roleName = field(left, top + 154, 280, "新角色名称");

        actionButtons.add(new ActionButton("server", left, top + 178, 135, 20, "查询布吉岛"));
        actionButtons.add(new ActionButton("role", left + 145, top + 178, 135, 20, "选择角色"));
        actionButtons.add(new ActionButton("createRole", left + 290, top + 204, 135, 20, "创建角色"));
        actionButtons.add(new ActionButton("prepare", left, top + 204, 135, 20, "准备白端"));
        actionButtons.add(new ActionButton("launch", left + 145, top + 204, 135, 20, "启动布吉岛"));
        actionButtons.add(new ActionButton("close", left + 290, top + 178, 135, 20, "关闭"));
        setInitialFocus(email);
        updateButtons();
    }

    @Override
    protected void renderSkija(SkijaFrame frame) {
        float scale = Math.max(1f, frame.width() / 960f);
        float panelWidth = 900f * scale;
        float panelHeight = Math.min(frame.height() - 48f * scale, 540f * scale);
        float x = (frame.width() - panelWidth) * .5f;
        float y = (frame.height() - panelHeight) * .5f;
        frame.draw().roundedRect(x, y, panelWidth, panelHeight, 12f * scale, 0xF20E1319);
        frame.draw().outline(x, y, panelWidth, panelHeight, 12f * scale, 1f * scale, 0xFF2D3A45);
        frame.text().draw(NimbusFonts.DISPLAY, 22f * scale, "Nimbus Gateway", x + 28f * scale,
                y + 26f * scale, 0xFFFFFFFF);
        frame.text().draw(NimbusFonts.BODY, 12f * scale,
                "网易兼容登录与布吉岛连接诊断（开发环境）", x + 30f * scale,
                y + 57f * scale, 0xFF9AA6B2);
        frame.text().draw(NimbusFonts.BODY_MEDIUM, 13f * scale, "网易账号", x + 30f * scale,
                y + 88f * scale, 0xFFB9C4CC);
        frame.text().draw(NimbusFonts.BODY_MEDIUM, 13f * scale, "4399 账号", x + panelWidth * .53f,
                y + 88f * scale, 0xFFB9C4CC);
        frame.text().draw(NimbusFonts.BODY, 12f * scale, status, x + 30f * scale,
                y + panelHeight - 64f * scale, 0xFFB9C4CC);
        if (captchaChallenge != null) {
            Image image = frame.images().encoded("nimbus-gateway-4399-captcha", captchaChallenge.image());
            if (image != null && !image.isClosed()) {
                float imageHeight = 58f * scale;
                float imageWidth = imageHeight * image.getWidth() / Math.max(1f, image.getHeight());
                frame.draw().image(image, x + panelWidth * .53f, y + 196f * scale, imageWidth, imageHeight);
            }
            frame.text().draw(NimbusFonts.BODY, 11f * scale, "请输入图片中的验证码",
                    x + panelWidth * .53f, y + 260f * scale, 0xFFF0C674);
        }
        if (server != null) {
            String details = server.name() + "  " + server.serverHost() + ":" + server.serverPort()
                    + "  版本 " + server.gameVersion();
            frame.text().draw(NimbusFonts.BODY, 12f * scale, details, x + 30f * scale,
                    y + panelHeight - 38f * scale, 0xFF70D6B0);
            frame.text().draw(NimbusFonts.BODY, 11f * scale,
                    "角色：" + roles(server.roles()), x + 30f * scale,
                    y + panelHeight - 20f * scale, 0xFF9AA6B2);
            if (!server.roles().isEmpty()) {
                GatewayRole role = selectedRoleValue();
                frame.text().draw(NimbusFonts.BODY, 11f * scale,
                        "当前角色：" + role.name(), x + 30f * scale,
                        y + panelHeight - 2f * scale, 0xFFF0C674);
            }
        }
        float guiScaleX = frame.width() / Math.max(1f, (float) width);
        float guiScaleY = frame.height() / Math.max(1f, (float) height);
        for (ActionButton button : actionButtons) {
            float bx = button.x() * guiScaleX;
            float by = button.y() * guiScaleY;
            float bw = button.width() * guiScaleX;
            float bh = button.height() * guiScaleY;
            boolean enabled = isButtonEnabled(button.id());
            boolean hover = framebufferMouseX() >= bx && framebufferMouseX() <= bx + bw
                    && framebufferMouseY() >= by && framebufferMouseY() <= by + bh;
            int color = !enabled ? 0x66232B32 : hover ? 0xFF31505B : 0xFF243943;
            frame.draw().roundedRect(bx, by, bw, bh, 4f * guiScaleX, color);
            frame.text().center(NimbusFonts.BODY_MEDIUM, 11f * guiScaleX, button.label(),
                    bx + bw * .5f, by + 4f * guiScaleY, enabled ? 0xFFE5F2F4 : 0xFF718087);
        }
    }

    private EditBox field(int x, int y, int width, String hint) {
        EditBox value = addRenderableWidget(new EditBox(font, x, y, width, 20, Component.literal(hint)));
        value.setHint(Component.literal(hint));
        value.setMaxLength(8192);
        return value;
    }

    private void loginEmail() {
        char[] secret = emailPassword.getValue().toCharArray();
        setBusy("正在登录网易账号…");
        complete(gateway().loginWithEmail(email.getValue(), secret), account -> success("网易登录成功：" + account.displayName()));
    }

    private void loginCookie() {
        if (cookie.getValue().length() >= MAX_COOKIE_LENGTH) {
            status = "失败：Cookie 超过 1 MiB，输入可能仍被截断";
            updateButtons();
            return;
        }
        char[] value = cookie.getValue().toCharArray();
        setBusy("正在导入 X19 Cookie…");
        complete(gateway().loginWithCookie(value), account -> success("Cookie 登录成功：" + account.displayName()));
    }

    private void requestCaptcha() {
        setBusy("正在获取 4399 验证码…");
        complete(gateway().request4399Captcha(), value -> {
            captchaChallenge = value;
            status = "验证码已获取，sessionId=" + value.sessionId();
            updateButtons();
        });
    }

    private void login4399() {
        char[] secret = channelPassword.getValue().toCharArray();
        String sessionId = captchaChallenge == null ? "" : captchaChallenge.sessionId();
        setBusy("正在登录 4399…");
        complete(gateway().loginWith4399(channelUser.getValue(), secret, sessionId, captcha.getValue()),
                account -> success("4399 登录成功：" + account.displayName()));
    }

    private void loadServer() {
        setBusy("正在查询布吉岛…");
        complete(gateway().loadBujiIsland(), value -> {
            server = value;
            selectedRole = 0;
            manifest = null;
            installation = null;
            status = "布吉岛查询成功";
            updateButtons();
        });
    }

    private void createRole() {
        if (server == null) {
            status = "失败：请先查询布吉岛";
            updateButtons();
            return;
        }
        String name = roleName.getValue().trim();
        if (name.isBlank()) {
            status = "失败：请输入新角色名称";
            updateButtons();
            return;
        }
        setBusy("正在创建角色…");
        complete(gateway().createBujiIslandCharacter(name), ignored -> {
            status = "角色创建成功，正在刷新角色列表…";
            complete(gateway().loadBujiIsland(), value -> {
                server = value;
                selectedRole = 0;
                status = "角色列表已刷新";
                updateButtons();
            });
        });
    }

    private void cycleRole() {
        if (server == null || server.roles().isEmpty()) return;
        selectedRole = (selectedRole + 1) % server.roles().size();
        status = "已选择角色：" + selectedRoleValue().name();
        updateButtons();
    }

    private GatewayRole selectedRoleValue() {
        if (server == null || server.roles().isEmpty()) throw new IllegalStateException("没有可用角色");
        return server.roles().get(Math.min(selectedRole, server.roles().size() - 1));
    }

    private void prepareWhiteClient() {
        if (server == null || server.roles().isEmpty()) {
            status = "失败：请先查询布吉岛并确认有角色";
            updateButtons();
            return;
        }
        setBusy("正在获取白端清单…");
        complete(gateway().loadWhiteClientManifest(), value -> {
            manifest = value;
            status = "白端清单已获取，正在准备资源…";
            updateButtons();
            complete(gateway().prepareWhiteClient(value), prepared -> {
                installation = prepared;
                success("白端资源准备完成");
            });
        });
    }

    private void launchBujiIsland() {
        if (server == null || installation == null) {
            status = "失败：请先查询布吉岛并点击准备白端";
            updateButtons();
            return;
        }
        GatewayAccount account = gateway().selectedAccount();
        GatewayLaunchTarget target;
        try {
            target = new GatewayLaunchTarget(account, server, selectedRoleValue());
        } catch (RuntimeException error) {
            status = "失败：" + error.getMessage();
            updateButtons();
            return;
        }
        Path java = gateway().resolveJava17Executable();
        if (java == null) {
            setBusy("正在下载官方 Java 17…");
            complete(gateway().prepareJava17(), prepared -> startWhiteClient(target, prepared));
            return;
        }
        startWhiteClient(target, java);
    }

    private void startWhiteClient(GatewayLaunchTarget target, Path java) {
        setBusy("正在按白端顺序启动 Java…");
        CompletableFuture
                .supplyAsync(() -> {
                    try {
                        GatewayJoinAuthenticator authenticator = gateway().createJoinAuthenticator(target, installation);
                        // Keep the diagnostic launch white-only until the real
                        // white-client connection is verified.  This isolates
                        // server-entry failures from the optional native bridge;
                        // the A/B bridge can be enabled in a separate test once
                        // the white client reaches the server reliably.
                        return gateway().startBujiIslandWhiteOnly(target, installation, java, 4096, authenticator,
                                state -> minecraft.execute(() -> onGatewayState(state)));
                    } catch (Exception error) {
                        throw new java.util.concurrent.CompletionException(error);
                    }
                })
                .whenComplete((started, error) -> minecraft.execute(() -> {
                    if (error != null) {
                        status = "启动失败：" + failureMessage(error);
                        updateButtons();
                        return;
                    }
                    session = started;
                    started.ready().whenComplete((state, bridgeError) -> minecraft.execute(() -> {
                        if (bridgeError != null) {
                            status = "桥接失败：" + failureMessage(bridgeError);
                            updateButtons();
                        } else if (started.isWhiteOnly()) {
                            status = "白端已启动（Native 桥接已禁用），请先确认能正常进入布吉岛";
                            updateButtons();
                        } else {
                            status = "桥接就绪，正在连接 Nimbus…";
                            started.connectMinecraft(parent == null ? this : parent);
                        }
                    }));
                }));
    }

    private void onGatewayState(GatewayState state) {
        status = "Gateway " + state.stage() + (state.detail() == null || state.detail().isBlank() ? "" : "：" + state.detail());
        updateButtons();
    }


    private void setBusy(String value) {
        status = value;
        updateButtons();
    }

    private void success(String value) {
        status = value;
        updateButtons();
    }

    private <T> void complete(CompletableFuture<T> future, java.util.function.Consumer<T> success) {
        future.whenComplete((value, error) -> minecraft.execute(() -> {
            if (error != null) {
                status = "失败：" + failureMessage(error);
            } else {
                success.accept(value);
            }
            updateButtons();
        }));
    }

    private static String failureMessage(Throwable error) {
        Throwable value = error;
        while (value != null) {
            if (value instanceof GatewayProtocolException && value.getMessage() != null) return value.getMessage();
            value = value.getCause();
        }
        value = error;
        while (value.getCause() != null) value = value.getCause();
        return value.getMessage() == null ? value.getClass().getSimpleName() : value.getMessage();
    }

    private void updateButtons() {
        boolean ready = status.startsWith("正在");
        disabledButtons.clear();
        if (ready) disabledButtons.addAll(actionButtons.stream().map(ActionButton::id).toList());
        else {
            if (gateway().selectedAccount() == null) disabledButtons.add("server");
            if (server == null || server.roles().isEmpty()) {
                disabledButtons.add("role");
                disabledButtons.add("prepare");
            }
            if (server == null) disabledButtons.add("createRole");
            if (server == null || installation == null || session != null) disabledButtons.add("launch");
        }
    }

    private boolean isButtonEnabled(String id) {
        return !disabledButtons.contains(id);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        if (mouseButton == 0) {
            for (ActionButton action : actionButtons) {
                if (!isButtonEnabled(action.id()) || !action.contains(mouseX, mouseY)) continue;
                switch (action.id()) {
                    case "email" -> loginEmail();
                    case "cookie" -> loginCookie();
                    case "captcha" -> requestCaptcha();
                    case "channel" -> login4399();
                    case "server" -> loadServer();
                    case "role" -> cycleRole();
                    case "createRole" -> createRole();
                    case "prepare" -> prepareWhiteClient();
                    case "launch" -> launchBujiIsland();
                    case "close" -> onClose();
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private record ActionButton(String id, int x, int y, int width, int height, String label) {
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }

    private GatewayManager gateway() {
        return Core.INSTANCE.getGatewayManager();
    }

    private static String roles(List<cc.nimbus.features.gateway.model.GatewayRole> values) {
        if (values == null || values.isEmpty()) return "无";
        return String.join(", ", values.stream().map(value -> value.name()).toList());
    }

    @Override
    protected void removedSkijaScreen() {
        captchaChallenge = null;
        server = null;
        manifest = null;
        installation = null;
    }

    @Override
    public void onClose() {
        if (session != null) {
            session.close();
            session = null;
        }
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
