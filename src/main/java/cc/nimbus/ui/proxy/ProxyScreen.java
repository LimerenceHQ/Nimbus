package cc.nimbus.ui.proxy;

import cc.nimbus.core.Core;
import cc.nimbus.managers.ProxyManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class ProxyScreen extends Screen {
    private boolean elevated;
    private Button elevation;
    private final Screen parent;
    private final ProxyManager manager;
    private List<ProxyManager.TargetProcess> targets = List.of();
    private int selected;
    private EditBox pid;
    private EditBox port;
    private Button targetButton;
    private Button attach;
    private Button attachPid;
    private Button connect;
    private String error = "";

    public ProxyScreen(Screen parent) {
        super(Component.literal("Proxy · Nimbus (B) → A"));
        this.parent = parent;
        this.manager = Core.INSTANCE.getProxyManager();
    }

    @Override
    protected void init() {
        String previousPid = pid == null ? "" : pid.getValue();
        String previousPort = port == null ? "25565" : port.getValue();
        int left = width / 2 - 150;
        int top = formTop();

        targetButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            if (!targets.isEmpty()) selected = (selected + 1) % targets.size();
            updateButtons();
        }).bounds(left, top, 238, 20).build());
        addRenderableWidget(Button.builder(Component.literal("刷新"), button -> refresh())
                .bounds(left + 242, top, 58, 20).build());

        pid = new EditBox(font, left, top + 33, 190, 20, Component.literal("A 进程 PID"));
        pid.setMaxLength(19);
        pid.setHint(Component.literal("输入 A 的 PID"));
        pid.setValue(previousPid);
        addRenderableWidget(pid);

        port = new EditBox(font, left + 200, top + 33, 100, 20, Component.literal("A 的监听端口"));
        port.setMaxLength(5);
        port.setValue(previousPort);
        addRenderableWidget(port);

        attach = addRenderableWidget(Button.builder(Component.literal("向扫描到的 A 加载 DLL 并连接"), button -> prepare())
                .bounds(left, top + 83, 300, 20).build());
        attachPid = addRenderableWidget(Button.builder(Component.literal("按 PID 加载 DLL 并连接"), button -> preparePid())
                .bounds(left, top + 59, 300, 20).build());
        connect = addRenderableWidget(Button.builder(Component.literal("直接连接已加载的 A"), button -> {
            try { connect(readPort()); } catch (RuntimeException ex) { error = ex.getMessage(); }
        }).bounds(left, top + 107, 300, 20).build());
        elevation=addRenderableWidget(Button.builder(Component.literal(elevated?"加载权限：管理员（弹出 UAC）":"加载权限：普通（点击切换管理员）"),button->{
            elevated=!elevated;button.setMessage(Component.literal(elevated?"加载权限：管理员（弹出 UAC）":"加载权限：普通（点击切换管理员）"));
        }).bounds(left,top+131,300,20).build());
        addRenderableWidget(Button.builder(Component.literal("返回"), button -> onClose())
                .bounds(left, top + 155, 300, 20).build());
        refresh();
        setInitialFocus(pid);
    }

    private void refresh() {
        var old = targets.isEmpty() ? null : targets.get(selected);
        try {
            targets = manager.targets();
            selected = old == null ? 0 : Math.max(0, targets.indexOf(old));
            error = "";
        } catch (RuntimeException ex) {
            error = "读取进程列表失败：" + ex.getMessage();
        }
        updateButtons();
    }

    private long readPid() {
        return ProxyManager.parsePid(pid.getValue());
    }

    private int readPort() {
        try {
            int value = Integer.parseInt(port.getValue().trim());
            if (value < 1 || value > 65535) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("请输入 1–65535 的 A 端监听端口");
        }
    }

    private void prepare() {
        if (targets.isEmpty()) return;
        try {
            submit(elevated?manager.prepareElevated(targets.get(selected),readPort()):manager.prepare(targets.get(selected), readPort()));
        } catch (RuntimeException ex) {
            error = ex.getMessage();
        }
        updateButtons();
    }

    private void preparePid() {
        try {
            submit(elevated?manager.preparePidElevated(readPid(),readPort()):manager.preparePid(readPid(), readPort()));
        } catch (RuntimeException ex) {
            error = ex.getMessage();
        }
        updateButtons();
    }

    private void submit(CompletableFuture<ProxyManager.Endpoint> operation) {
        error = "";
        var client = minecraft;
        operation.whenComplete((endpoint, failure) -> client.execute(() -> {
            if (client.screen != this) return;
            if (failure == null) connect(endpoint.port());
            else error = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        }));
    }

    private void connect(int value) {
        ProxyManager.Endpoint endpoint = manager.requestConnection(value);
        ServerData data = new ServerData("Nimbus A", endpoint.address(), false);
        ConnectScreen.startConnecting(this, minecraft, ServerAddress.parseString(endpoint.address()), data, false);
    }

    @Override
    public void tick() {
        pid.tick();
        port.tick();
        updateButtons();
    }

    private void updateButtons() {
        targetButton.setMessage(Component.literal(targets.isEmpty() ? "未扫描到 Java 进程（可直接输入 PID）" :
                (selected + 1) + "/" + targets.size() + "  " + targets.get(selected).label()));
        boolean busy = manager.isBusy();
        targetButton.active = !busy && !targets.isEmpty();
        attach.active = !busy && !targets.isEmpty();
        attachPid.active = !busy;
        connect.active = !busy;
        if(elevation!=null)elevation.active=!busy;
        pid.setEditable(!busy);
        port.setEditable(!busy);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 14, 0xFFFFFF);
        graphics.drawCenteredString(font, "按布吉岛可见窗口扫描；手动 PID 使用进程快照", width / 2, 29, 0xAAAAAA);
        graphics.drawString(font, "A 进程 PID", width / 2 - 150, formTop() + 23, 0xCCCCCC);
        graphics.drawString(font, "监听端口", width / 2 + 50, formTop() + 23, 0xCCCCCC);
        super.render(graphics, mouseX, mouseY, partialTick);
        int y = formTop() + 180;
        String detail = error.isEmpty() ? manager.status().detail() : error;
        graphics.drawWordWrap(font, Component.literal(detail), Math.max(8, width / 2 - 150), y,
                Math.min(300, width - 16), error.isEmpty() ? 0xCCCCCC : 0xFF8888);
    }

    private int formTop() { return Math.max(44, height / 2 - 102); }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
