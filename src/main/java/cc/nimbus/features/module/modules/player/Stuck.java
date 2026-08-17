package cc.nimbus.features.module.modules.player;

import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.component.Priority;
import cc.nimbus.event.impl.game.EventTick;
import cc.nimbus.event.impl.game.EventWorldLoad;
import cc.nimbus.event.impl.network.EventPacketReceive;
import cc.nimbus.event.impl.network.EventPacketSend;
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
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;

@ModuleInfo(
        name = "Stuck",
        description = "Freezes your position until damage or a server correction is received.",
        category = ModuleCategory.Player
)
public final class Stuck extends BasicModule {
    private static final int MAX_MOVEMENT_PACKETS_WITHOUT_POSITION = 19;
    private static final int MAX_TICKS_WITHOUT_POSITION = 20;

    private int movementPacketsWithoutPosition;
    private int ticksWithoutPosition;
    private boolean releaseTick;
    private boolean releasing;
    
    public void release(InteractionHand hand) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.player.getItemInHand(hand).is(Items.ENDER_PEARL)) {
            setEnabled(false);
        }
    }


    @Override
    protected void onEnable() {
        movementPacketsWithoutPosition = 0;
        ticksWithoutPosition = 0;
        releaseTick = false;
        releasing = false;
        if (Minecraft.getInstance().player != null) Disabler.setStuck();
    }

    @Override
    protected void onDisable() {
        movementPacketsWithoutPosition = 0;
        ticksWithoutPosition = 0;
        releaseTick = false;
        releasing = false;
        Disabler.offStuck();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player instanceof LocalPlayerStateAccess access) {
            access.nimbus$setPositionReminder(20);
        }
    }

    @EventTarget
    private void onTravel(EventTravel event) {
        if (releasing) return;
        event.cancelEvent();
    }

    @EventTarget
    private void onMoveInput(EventMoveInput event) {
        if (releasing) return;
        event.cancelEvent();
    }

    @EventTarget
    private void onTick(EventTick event) {
        if (event.getPhase() != EventTick.Phase.PRE) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.player.hurtTime > 0) {
            setEnabled(false);
            return;
        }
        if (releasing) {
            releasing = false;
            movementPacketsWithoutPosition = 0;
            ticksWithoutPosition = 0;
            Disabler.setStuck();
        }
        if (++ticksWithoutPosition >= MAX_TICKS_WITHOUT_POSITION) releaseTick = true;
        if (releaseTick) {
            releaseTick = false;
            releasing = true;
            Disabler.offStuck();
            if (minecraft.player instanceof LocalPlayerStateAccess access) {
                access.nimbus$setPositionReminder(20);
            }
        }
    }

    @EventTarget(priority = Priority.Highest)
    private void onPacketSend(EventPacketSend event) {
        if (event.getPacket() instanceof ServerboundUseItemPacket useItem) {
            release(useItem.getHand());
            return;
        }
        if (!(event.getPacket() instanceof ServerboundMovePlayerPacket packet)) return;
        if (packet.hasPosition()) {
            movementPacketsWithoutPosition = 0;
            ticksWithoutPosition = 0;
            releaseTick = false;
            return;
        }
        movementPacketsWithoutPosition++;
        if (movementPacketsWithoutPosition >= MAX_MOVEMENT_PACKETS_WITHOUT_POSITION) releaseTick = true;
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
        if (event.isPre() && !releasing && minecraft.player instanceof LocalPlayerStateAccess access) {
            access.nimbus$setPositionReminder(0);
        }
    }

    @EventTarget
    private void onWorldLoad(EventWorldLoad event) {
        setEnabled(false);
    }
}
