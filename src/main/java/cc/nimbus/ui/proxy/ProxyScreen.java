package cc.nimbus.ui.proxy;

import cc.nimbus.core.Core;
import cc.nimbus.managers.ProxyManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public final class ProxyScreen extends Screen {
    private final Screen parent;
    private final ProxyManager manager;
    private List<ProxyManager.TargetProcess> targets = List.of();
    private int selected;
    private boolean elevated;
    private Button targetButton;
    private Button permissionButton;
    private EditBox port;
    private String message = "";

    public ProxyScreen(Screen parent) {
        super(Component.literal(tr("nimbus.takeover.settings.title", "TakeOver Settings")));
        this.parent = parent;
        manager = Core.INSTANCE.getProxyManager();
    }

    @Override
    protected void init() {
        ProxyManager.Configuration saved = manager.configuration();
        elevated = saved.elevated();
        int left = width / 2 - 150;
        int top = Math.max(48, height / 2 - 78);

        targetButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            if (!targets.isEmpty()) {
                selected = (selected + 1) % targets.size();
            }
            updateLabels();
        }).bounds(left, top, 238, 20).build());
        addRenderableWidget(Button.builder(Component.literal(tr("nimbus.takeover.settings.refresh", "Refresh")),
                button -> refresh(currentPid()))
                .bounds(left + 242, top, 58, 20).build());

        String portLabel = tr("nimbus.takeover.settings.port", "Listening port");
        port = new EditBox(font, left, top + 42, 300, 20, Component.literal(portLabel));
        port.setMaxLength(5);
        port.setValue(Integer.toString(saved.port()));
        port.setHint(Component.literal(portLabel));
        addRenderableWidget(port);

        permissionButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            elevated = !elevated;
            updateLabels();
        }).bounds(left, top + 70, 300, 20).build());

        addRenderableWidget(Button.builder(Component.literal(tr("nimbus.takeover.settings.save", "Save")),
                button -> saveAndClose())
                .bounds(left, top + 102, 146, 20).build());
        addRenderableWidget(Button.builder(Component.literal(tr("nimbus.takeover.settings.cancel", "Cancel")),
                button -> onClose())
                .bounds(left + 154, top + 102, 146, 20).build());

        refresh(saved.preferredPid());
        setInitialFocus(port);
    }

    @Override
    public void tick() {
        port.tick();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int top = Math.max(48, height / 2 - 78);
        graphics.drawCenteredString(font, title, width / 2, top - 34, 0xFFFFFF);
        graphics.drawString(font, tr("nimbus.takeover.settings.target", "Target process"),
                width / 2 - 150, top - 12, 0xA0A0A0, false);
        graphics.drawString(font, tr("nimbus.takeover.settings.port", "Listening port"),
                width / 2 - 150, top + 30, 0xA0A0A0, false);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!message.isBlank()) {
            graphics.drawCenteredString(font, message, width / 2, top + 132, 0xFF7777);
        }
    }

    private void refresh(long preferredPid) {
        try {
            targets = manager.targets();
            selected = 0;
            for (int index = 0; index < targets.size(); index++) {
                if (targets.get(index).pid() == preferredPid) {
                    selected = index;
                    break;
                }
            }
            message = targets.isEmpty()
                    ? tr("nimbus.takeover.settings.no_target", "No usable A process found") : "";
        } catch (RuntimeException error) {
            targets = List.of();
            selected = 0;
            message = error.getMessage() == null
                    ? tr("nimbus.takeover.settings.scan_failed", "Process scan failed") : error.getMessage();
        }
        updateLabels();
    }

    private void updateLabels() {
        if (targetButton != null) {
            targetButton.setMessage(Component.literal(targets.isEmpty()
                    ? tr("nimbus.takeover.settings.no_java", "No Java process found")
                    : targetLabel(targets.get(selected))));
        }
        if (permissionButton != null) {
            permissionButton.setMessage(Component.literal(elevated
                    ? tr("nimbus.takeover.settings.permission_admin", "Load permission: Administrator (UAC)")
                    : tr("nimbus.takeover.settings.permission_normal", "Load permission: Normal")));
        }
    }

    private void saveAndClose() {
        try {
            int parsedPort = Integer.parseInt(port.getValue().strip());
            if (parsedPort < 1 || parsedPort > 65535) throw new NumberFormatException();
            manager.configure(currentPid(), parsedPort, elevated);
            onClose();
        } catch (NumberFormatException error) {
            message = tr("nimbus.takeover.settings.invalid_port", "Port must be between 1 and 65535");
            setFocused(port);
        } catch (RuntimeException error) {
            message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        }
    }

    private long currentPid() {
        return targets.isEmpty() ? manager.configuration().preferredPid() : targets.get(selected).pid();
    }

    private String targetLabel(ProxyManager.TargetProcess target) {
        return tr("nimbus.takeover.settings.target_value", "%d/%d  %s",
                selected + 1, targets.size(), target.label());
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            saveAndClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }

    private static String tr(String key, String fallback, Object... arguments) {
        return Core.INSTANCE.isInitialized()
                ? Core.INSTANCE.getI18nManager().get(key, fallback, arguments) : fallback;
    }

}
