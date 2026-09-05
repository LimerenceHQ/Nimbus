package cc.nimbus.features.module.modules.combat.velocity.legitVelocity;

import cc.nimbus.core.Core;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.component.Priority;
import cc.nimbus.event.impl.game.EventTick;
import cc.nimbus.event.impl.network.PacketEvent;
import cc.nimbus.event.impl.player.EventMoveInput;
import cc.nimbus.features.module.api.setting.impl.MultiSelectSetting;
import cc.nimbus.features.module.api.setting.impl.NumberSetting;
import cc.nimbus.features.module.api.setting.impl.submodule.Mode;
import cc.nimbus.features.module.api.setting.impl.submodule.ModeValue;
import cc.nimbus.features.module.modules.combat.KillAura;
import cc.nimbus.features.module.modules.combat.velocity.Legit;
import cc.nimbus.managers.GameStateManager;
import cc.nimbus.features.packet.PacketDirection;
import cc.nimbus.utils.client.IMinecraft;
import cc.nimbus.utils.network.PacketUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.syncher.SynchedEntityData;

import java.util.concurrent.LinkedBlockingQueue;

public class Delay extends Mode<Legit> implements IMinecraft {
    private static final int ENTITY_FLAGS_INDEX = 0;
    private static final int SPRINTING_FLAG = 3;

    public Delay() {
        super("Delay");
    }
    public final MultiSelectSetting releaseTiming = new MultiSelectSetting(
            "Release Timing",
            new String[]{"Until Landing", "Until Sprint"}
    );
    private final ModeValue releaseMode = new ModeValue("Release Mode", new String[]{"Release All","EntityData Delay"},"Release All");
    private final NumberSetting ticks = new NumberSetting("Max Limited Tick",20,0,40,1);

    public BufferState state = BufferState.IDLE;
    public int bufferTicks = -1;
    private boolean holdingSprintEntityData;

    @Override
    protected void onDisable() {
        if (state == BufferState.BUFFERING) {
            releasePackets();
        }
        resetBuffer();
    }

    @EventTarget
    public void onTick(EventTick event) {
        if (!event.isPre()) {
            return;
        }

        if (mc.player == null) {
            return;
        }

        if (state != BufferState.BUFFERING) {
            return;
        }

        if (bufferTicks >= 0) {
            bufferTicks++;
        }

        if (holdingSprintEntityData) {
            if (!getParent().handleVelocity) {
                releasePackets();
                resetBuffer();
            }
            return;
        }

        if (!shouldRelease()) {
            return;
        }
        if (mc.player.isInWater() || mc.player.isInLava()) return;

        if (releaseMode.is("EntityData Delay")) {
            boolean stoppedAtSprintEntityData = releaseUntilSprintEntityData();
            startVelocityHandling();

            if (stoppedAtSprintEntityData) {
                holdingSprintEntityData = true;
                return;
            }
            resetBuffer();
            return;
        }
        releasePackets();
        startVelocityHandling();
        resetBuffer();
    }

    @EventTarget(priority = Priority.Lowest)
    public void onMoveInput(EventMoveInput event) {
        if (state == BufferState.BUFFERING) {
            event.setForward(1.0f);
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (mc.player == null || mc.player.tickCount <= 60 || mc.level == null || mc.getConnection() == null) {
            return;
        }

        if (event.getPacketState() != PacketEvent.PacketState.RECEIVE) {
            return;
        }

        if (event.getPacket() instanceof ClientboundPlayerPositionPacket) {
            if (state == BufferState.BUFFERING) {
                releasePackets();
            }
            resetBuffer();
        }

        if (!(event.getPacket() instanceof ClientboundSetEntityMotionPacket packet)) {
            return;
        }

        if (state != BufferState.IDLE || getParent().cantSprint() || !getParent().isValidVelocityPacket(packet)) {
            return;
        }

        if (Core.INSTANCE.getModuleManager().getModule(KillAura.class).attackTarget == null) return;

//1
        if (releaseTiming.isSelected("Until Landing")
                && releaseMode.is("Release All")
                && !mc.player.onGround()) {
            startVelocityHandling();
            return;
        }

        bufferTicks = 0;
        state = BufferState.BUFFERING;
        holdingSprintEntityData = false;
        Core.INSTANCE.getPacketManager().freeze(PacketDirection.INBOUND);
    }


    private boolean shouldRelease() {
        int maxBufferTicks = ticks.getValue().intValue();
        boolean untilLanding = releaseTiming.isSelected("Until Landing");
        boolean tillSprint = releaseTiming.isSelected("Until Sprint");
        boolean onGround = GameStateManager.clientOnGround();
        boolean sprinting = mc.player != null && mc.player.isSprinting();
        boolean timedOut = bufferTicks >= maxBufferTicks;
        boolean cantSprint = getParent().cantSprint();

        if (untilLanding && tillSprint) {
            return (onGround && sprinting) || timedOut || cantSprint;
        }
        if (untilLanding) {
            return onGround || timedOut || cantSprint;
        }
        if (tillSprint) {
            return sprinting || timedOut || cantSprint;
        }
        return timedOut || cantSprint;
    }

    private void startVelocityHandling() {
        getParent().ticksSinceVelocity = 0;
        getParent().handleVelocity = true;
        if (getParent().modules.isEnabled("Jump Reset")) {
            getParent().needHandleRotation = true;
        }
    }

    private boolean releaseUntilSprintEntityData() {
        while (Core.INSTANCE.getPacketManager().queueSize(PacketDirection.INBOUND) > 0) {
            Packet<?> packet = Core.INSTANCE.getPacketManager().peek(PacketDirection.INBOUND);
            if (isSprintEntityData(packet)) {
                return true;
            }

            packet = Core.INSTANCE.getPacketManager().take(PacketDirection.INBOUND);
            if (packet != null) {
                PacketUtils.receivePacketNoEvent(packet);
            }
        }

        Core.INSTANCE.getPacketManager().dispatch(PacketDirection.INBOUND);
        return false;
    }

    private boolean isSprintEntityData(Packet<?> packet) {
        if (mc.player == null || !(packet instanceof ClientboundSetEntityDataPacket entityDataPacket)) {
            return false;
        }
        if (entityDataPacket.id() != mc.player.getId()) {
            return false;
        }

        for (SynchedEntityData.DataValue<?> dataValue : entityDataPacket.packedItems()) {
            if (dataValue.id() != ENTITY_FLAGS_INDEX || !(dataValue.value() instanceof Byte flags)) {
                continue;
            }
            return (flags & (1 << SPRINTING_FLAG)) == 0;
        }

        return false;
    }

    private void releasePackets() {
        Core.INSTANCE.getPacketManager().dispatch(PacketDirection.INBOUND);
    }

    private void resetBuffer() {
        bufferTicks = -1;
        state = BufferState.IDLE;
        holdingSprintEntityData = false;
    }

    public enum BufferState {
        IDLE,
        BUFFERING
    }
}
