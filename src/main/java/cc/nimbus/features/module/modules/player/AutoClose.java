package cc.nimbus.features.module.modules.player;

import cc.nimbus.core.Core;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.game.EventWorldLoad;
import cc.nimbus.event.impl.network.PacketEvent;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.BooleanSetting;
import cc.nimbus.features.module.modules.combat.KillAura;
import cc.nimbus.ui.notification.NotificationType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;

import java.util.Locale;

@ModuleInfo(
        name = "AutoClose",
        description = "Closes selected modules after world changes or match result messages.",
        category = ModuleCategory.Player
)
public final class AutoClose extends BasicModule {
    private static final long DUPLICATE_WINDOW_MS = 500L;

    public final BooleanSetting worldChange = new BooleanSetting("World Change", true);
    public final BooleanSetting autoCloseOnWinOrSpectator =
            new BooleanSetting("Win Or Spectator Message", true);

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Object closeRequestLock = new Object();
    private long lastCloseRequestAt = Long.MIN_VALUE;

    @EventTarget
    private void onWorldLoad(EventWorldLoad event) {
        if (!worldChange.getValue() || !reserveCloseRequest()) return;

        postCloseNotification("世界变化，功能已自动关闭");
    }

    @EventTarget
    private void onPacket(PacketEvent event) {
        if (!autoCloseOnWinOrSpectator.getValue() || !event.isReceive()
                || !(event.getPacket() instanceof ClientboundSystemChatPacket packet)) return;

        String text = packet.content().getString();
        if (text == null || text.isBlank()) return;
        String lower = text.toLowerCase(Locale.ROOT);
        boolean spectatorDetected = text.contains("你现在是观察者") || text.contains("旁观者") || text.contains("可以用 /hub 退出观察者模式并返回大厅") || lower.contains("spectator");
        boolean victoryDetected = text.contains("胜利") || text.contains("获胜") || text.contains("赢了") || lower.contains("victory") || lower.contains("you won");
        if (!spectatorDetected && !victoryDetected || !reserveCloseRequest()) return;

        minecraft.execute(() -> postCloseNotification("游戏结束，功能已自动关闭"));
    }

    private boolean closeModules() {
        if (!Core.INSTANCE.isInitialized()) return false;

        boolean closed = false;
        KillAura killAura = Core.INSTANCE.getModuleManager().getModule(KillAura.class);
        ContainerStealer containerStealer = Core.INSTANCE.getModuleManager().getModule(ContainerStealer.class);
        InventoryManager inventoryManager = Core.INSTANCE.getModuleManager().getModule(InventoryManager.class);

        if (killAura != null && killAura.isEnabled()) {
            killAura.setEnabled(false);
            closed = true;
        }
        if (containerStealer != null && containerStealer.isEnabled()) {
            containerStealer.setEnabled(false);
            closed = true;
        }
        if (inventoryManager != null && inventoryManager.isEnabled()) {
            inventoryManager.setEnabled(false);
            closed = true;
        }
        return closed;
    }

    private void postCloseNotification(String message) {
        if (!closeModules()) return;
        Core.INSTANCE.getNotificationManager().postClient("自动关闭", message, 3000, NotificationType.INFO);
    }

    private boolean reserveCloseRequest() {
        long now = System.currentTimeMillis();
        synchronized (closeRequestLock) {
            if (lastCloseRequestAt != Long.MIN_VALUE
                    && now >= lastCloseRequestAt
                    && now - lastCloseRequestAt < DUPLICATE_WINDOW_MS) {
                return false;
            }
            lastCloseRequestAt = now;
            return true;
        }
    }

    @Override
    protected void onDisable() {
        synchronized (closeRequestLock) {
            lastCloseRequestAt = Long.MIN_VALUE;
        }
    }
}
