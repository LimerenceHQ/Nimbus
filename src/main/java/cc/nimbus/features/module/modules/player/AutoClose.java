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
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;

import java.util.Locale;

@ModuleInfo(
        name = "AutoClose",
        description = "Closes selected modules after world changes or match result messages.",
        category = ModuleCategory.Player
)
public final class AutoClose extends BasicModule {
    public final BooleanSetting worldChange = new BooleanSetting("World Change", true);
    public final BooleanSetting autoCloseOnWinOrSpectator =
            new BooleanSetting("Win Or Spectator Message", true);

    private final Minecraft minecraft = Minecraft.getInstance();

    @EventTarget
    private void onWorldLoad(EventWorldLoad event) {
        if (worldChange.getValue()) closeModules();
    }

    @EventTarget
    private void onPacket(PacketEvent event) {
        if (!autoCloseOnWinOrSpectator.getValue() || !event.isReceive()
                || !(event.getPacket() instanceof ClientboundSystemChatPacket packet)) return;

        String text = packet.content().getString();
        if (text == null || text.isBlank()) return;
        String lower = text.toLowerCase(Locale.ROOT);
        boolean spectatorDetected = text.contains("你现在是观察者") || text.contains("旁观者")
                || text.contains("可以用 /hub 退出观察者模式并返回大厅")
                || lower.contains("spectator");
        boolean victoryDetected = text.contains("胜利") || text.contains("获胜") || text.contains("赢了")
                || lower.contains("victory") || lower.contains("you won");
        if (spectatorDetected || victoryDetected) minecraft.execute(this::closeModules);
    }

    private void closeModules() {
        if (!Core.INSTANCE.isInitialized()) return;
        KillAura killAura = Core.INSTANCE.getModuleManager().getModule(KillAura.class);
        ContainerStealer containerStealer = Core.INSTANCE.getModuleManager().getModule(ContainerStealer.class);
        InventoryManager inventoryManager = Core.INSTANCE.getModuleManager().getModule(InventoryManager.class);
        if (killAura != null && killAura.isEnabled()) killAura.setEnabled(false);
        if (containerStealer != null && containerStealer.isEnabled()) containerStealer.setEnabled(false);
        if (inventoryManager != null && inventoryManager.isEnabled()) inventoryManager.setEnabled(false);
    }
}
