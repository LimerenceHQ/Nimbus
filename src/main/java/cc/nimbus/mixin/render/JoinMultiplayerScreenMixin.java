package cc.nimbus.mixin.render;

import cc.nimbus.core.Core;
import cc.nimbus.managers.ProxyManager;
import cc.nimbus.mixin.accessor.server.LanServerListAccessor;
import cc.nimbus.ui.proxy.ProxyHoldButton;
import cc.nimbus.ui.proxy.ProxyScreen;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.server.LanServerDetection;
import net.minecraft.client.server.LanServerPinger;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.CompletionException;

@Mixin(JoinMultiplayerScreen.class)
public abstract class JoinMultiplayerScreenMixin extends Screen {
    @Shadow private LanServerDetection.LanServerList lanServerList;
    @Unique private ProxyHoldButton nimbus$proxyButton;
    @Unique private int nimbus$registeredPort = -1;

    protected JoinMultiplayerScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void nimbus$addProxyEntry(CallbackInfo ci) {
        nimbus$proxyButton = addRenderableWidget(new ProxyHoldButton(
                Math.max(6, width - 166), 6, 160, 20,
                Component.literal("TakeOver"),
                this::nimbus$quickConnect,
                () -> minecraft.setScreen(new ProxyScreen(this))
        ));
        nimbus$proxyButton.setTooltip(Tooltip.create(Component.literal("短按连接，长按进入设置")));
        nimbus$syncLanEntry();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void nimbus$tickProxyEntry(CallbackInfo ci) {
        if (nimbus$proxyButton != null) {
            nimbus$proxyButton.active = !Core.INSTANCE.getProxyManager().isBusy();
            nimbus$proxyButton.tickHold();
        }
        nimbus$syncLanEntry();
    }

    @Unique
    private void nimbus$quickConnect() {
        ProxyManager manager = Core.INSTANCE.getProxyManager();
        var loaded = manager.lanEndpoint();
        if (loaded.isPresent()) {
            nimbus$connect(loaded.get());
            return;
        }
        nimbus$proxyButton.active = false;
        nimbus$proxyButton.setMessage(Component.literal("正在加载 A…"));
        manager.prepareConfigured().whenComplete((endpoint, failure) -> minecraft.execute(() -> {
            if (failure == null) {
                nimbus$connect(endpoint);
                return;
            }
            Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                    ? failure.getCause() : failure;
            String message = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
            if (minecraft.screen == this && nimbus$proxyButton != null) {
                nimbus$proxyButton.active = true;
                nimbus$proxyButton.setMessage(Component.literal("失败 - 可进入设置调整"));
                nimbus$proxyButton.setTooltip(Tooltip.create(Component.literal(message)));
            }
        }));
    }

    @Unique
    private void nimbus$connect(ProxyManager.Endpoint endpoint) {
        ProxyManager.Endpoint requested = Core.INSTANCE.getProxyManager().requestConnection(endpoint.port());
        ServerData data = new ServerData(nimbus$lanName(), requested.address(), false);
        ConnectScreen.startConnecting(this, minecraft, ServerAddress.parseString(requested.address()), data, false);
    }

    @Unique
    private void nimbus$syncLanEntry() {
        if (lanServerList == null || !Core.INSTANCE.isInitialized()) return;
        var endpoint = Core.INSTANCE.getProxyManager().lanEndpoint();
        if (endpoint.isEmpty()) {
            nimbus$removeLanEntries(-1);
            nimbus$registeredPort = -1;
            return;
        }
        int port = endpoint.get().port();
        if (nimbus$registeredPort == port) return;
        nimbus$removeLanEntries(port);
        try {
            lanServerList.addServer(LanServerPinger.createPingString(nimbus$lanName(), Integer.toString(port)),
                    InetAddress.getByAddress(new byte[]{127, 0, 0, 1}));
        } catch (UnknownHostException impossible) {
            throw new IllegalStateException("无法创建 IPv4 loopback 地址", impossible);
        }
        nimbus$registeredPort = port;
    }

    @Unique
    private void nimbus$removeLanEntries(int retainedPort) {
        LanServerListAccessor accessor = (LanServerListAccessor) lanServerList;
        synchronized (lanServerList) {
            boolean changed = accessor.nimbus$getServers().removeIf(server ->
                    nimbus$isLanName(server.getMotd())
                            && (retainedPort < 0 || !server.getAddress().equals("127.0.0.1:" + retainedPort)));
            if (changed) accessor.nimbus$setDirty(true);
        }
    }

    @Unique
    private static String nimbus$lanName() {
        return "Nimbus Proxy · TakeOver";
    }

    @Unique
    private static boolean nimbus$isLanName(String value) {
        return value.equals(nimbus$lanName());
    }
}
