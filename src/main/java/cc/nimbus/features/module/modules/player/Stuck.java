package cc.nimbus.features.module.modules.player;

import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.game.EventTick;
import cc.nimbus.event.impl.game.EventWorldLoad;
import cc.nimbus.event.impl.network.EventPacketReceive;
import cc.nimbus.event.impl.player.EventMotion;
import cc.nimbus.event.impl.player.EventMoveInput;
import cc.nimbus.event.impl.player.EventTravel;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.modules.exploit.Disabler;
import cc.nimbus.utils.mixin.LocalPlayerStateAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;

@ModuleInfo(
        name = "Stuck",
        description = "Freezes your position until damage or a server correction is received.",
        category = ModuleCategory.Player
)
public final class Stuck extends BasicModule {
    @Override
    protected void onEnable() {
        if (Minecraft.getInstance().player != null) Disabler.setStuck();
    }

    @Override
    protected void onDisable() {
        Disabler.offStuck();
    }

    @EventTarget
    private void onTravel(EventTravel event) {
        event.cancelEvent();
    }

    @EventTarget
    private void onMoveInput(EventMoveInput event) {
        event.cancelEvent();
    }

    @EventTarget
    private void onTick(EventTick event) {
        if (event.getPhase() != EventTick.Phase.PRE) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.player.hurtTime > 0) setEnabled(false);
    }

    @EventTarget
    private void onPacketReceive(EventPacketReceive event) {
        if (event.getPacket() instanceof ClientboundPlayerPositionPacket) {
            Minecraft.getInstance().execute(() -> {
                if (isEnabled()) setEnabled(false);
            });
        }
    }

    @EventTarget
    private void onMotion(EventMotion event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.isPre() && minecraft.player instanceof LocalPlayerStateAccess access) {
            access.nimbus$setPositionReminder(0);
        }
    }

    @EventTarget
    private void onWorldLoad(EventWorldLoad event) {
        setEnabled(false);
    }
}
