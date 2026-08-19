package cc.nimbus.features.module.modules.movement;

import cc.nimbus.core.Core;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.game.EventClick;
import cc.nimbus.event.impl.game.EventTick;
import cc.nimbus.event.impl.network.EventPacketReceive;
import cc.nimbus.event.impl.network.EventPacketSend;
import cc.nimbus.event.impl.player.EventMotion;
import cc.nimbus.event.impl.player.EventSlowDown;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.ModeSetting;
import cc.nimbus.features.module.api.setting.impl.NumberSetting;
import cc.nimbus.features.module.modules.combat.KillAura;
import cc.nimbus.features.module.modules.player.GhostHand;
import cc.nimbus.managers.ClientManger;
import cc.nimbus.utils.client.TimerUtils;
import cc.nimbus.utils.network.PacketUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.phys.BlockHitResult;

import java.nio.charset.CoderResult;
import java.util.concurrent.LinkedBlockingDeque;

@ModuleInfo(name = "NoSlow", category = ModuleCategory.Move, description = "Removes movement slowdown from items, honey, cobwebs and soul sand")
public class NoSlow extends BasicModule {
    private static final BlockPos SWAP_POS = BlockPos.ZERO;
    private static final Direction SWAP_DIRECTION = Direction.DOWN;

    private final ModeSetting mode = new ModeSetting("Mode", new String[]{"Normal", "GrimFull"}, "Normal");
    private final NumberSetting releaseTick = new NumberSetting("Release Ticks", 32.0, 0.0, 150.0, 1.0,() -> mode.is("GrimFull"));
    private final LinkedBlockingDeque<Packet<ClientGamePacketListener>> inBound = new LinkedBlockingDeque<>();
    private final TimerUtils timer = new TimerUtils();

    private int useTicks = -1;
    private int swapCount = 0;
    private boolean onceUse = true;
    private boolean isActive = false;
    private boolean isUse = false;
    private boolean shouldPush = false;
    private int pauseTimer = 0;
    private boolean pendingNoSlow = false;
    private boolean pendingUseItem = false;

    @Override
    public void onEnable() {
        resetState();
        timer.reset();
    }

    @Override
    public void onDisable() {
        flushInbound();
        resetState();
    }

    @EventTarget
    public void onSlowDown(EventSlowDown event) {
        if (mc.player == null) {
            return;
        }

        switch (mode.getValue()) {
            case "GrimFull" -> {
                if (mc.player.getTicksUsingItem() > 2 && isUse) {
                    event.cancelEvent();
                    mc.player.setSprinting(true);
                }
            }
            case "Normal" -> {
                boolean shouldActivate = mc.player.getTicksUsingItem() % 3 != 0;
                if (shouldActivate) {
                    if (!isActive) {
                        shouldPush = true;
                        pauseTimer = 0;
                        isActive = true;
                        return;
                    }

                    if (shouldPush) {
                        pauseTimer--;
                        if (pauseTimer <= 0) {
                            shouldPush = false;
                            event.cancelEvent();
                            mc.player.setSprinting(true);
                        }
                        return;
                    }

                    event.cancelEvent();
                    mc.player.setSprinting(true);
                } else {
                    isActive = false;
                    shouldPush = false;
                    pauseTimer = 0;
                }
            }
        }
    }
    @EventTarget
    public void onPacketReceive(EventPacketReceive event) {
        if (mc.player == null || mc.level == null || mc.getConnection() == null || !mode.is("GrimFull")) {
            return;
        }
        Packet<?> packet = event.getPacket();

        if (packet instanceof ClientboundPlayerPositionPacket) {
            flushInbound();
            resetState();
            return;
        }

        if (packet instanceof ClientboundContainerSetSlotPacket || packet instanceof ClientboundContainerSetContentPacket) {
            if (isUse || !timer.hasTimeElapsed(100L)) {
                event.cancelEvent();
                return;
            }
        }

        if (isHoldingConsumableForGrimFull()
                && isUse
                && (packet instanceof ClientboundPingPacket || packet instanceof ClientboundSetEntityMotionPacket)) {
            if (useTicks == -1) {
                useTicks = 0;
            }
            event.cancelEvent();
            inBound.add((Packet<ClientGamePacketListener>) packet);
        }

    }
    @EventTarget
    public void onPacketSend(EventPacketSend event) {
        if (mc.player == null || mc.level == null || mc.getConnection() == null || !mode.is("GrimFull")) {
            return;
        }

        Packet<?> packet = event.getPacket();
        if (isHoldingConsumableForGrimFull()
                && packet instanceof ServerboundPlayerActionPacket actionPacket
                && actionPacket.getAction() == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM
                && (isUse || !inBound.isEmpty())) {
            mc.options.keyUse.setDown(false);
            event.cancelEvent();
        }
    }

    @EventTarget
    public void onMotion(EventMotion event) {
        if (mc.player == null || mc.level == null || mc.getConnection() == null || !mode.is("GrimFull")) {
            return;
        }

        if (event.isPre()) {
            if (pendingNoSlow) {
                mc.getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, SWAP_POS, SWAP_DIRECTION));
                PacketUtils.sendPacketNoEvent(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM, SWAP_POS, SWAP_DIRECTION));
                pendingNoSlow = false;
                pendingUseItem = true;
            }

            if (useTicks >= 0) {
                useTicks++;
            }
        }
    }

    @EventTarget
    public void onRotationApplied(EventClick event) {
        if (mc.player == null || mc.getConnection() == null || !mode.is("GrimFull")) {
            return;
        }

        if (useTicks >= releaseTick.getValue().doubleValue() && !inBound.isEmpty()) {
            flushInbound();
            if (swapCount < 1 && isHoldingConsumableForGrimFull()) {
                mc.getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, SWAP_POS, SWAP_DIRECTION));
                swapCount++;
            }
            useTicks = -1;
            onceUse = true;
            isUse = false;
            timer.reset();
            swapCount = 0;
        }
    }

    @EventTarget
    public void onTick(EventTick event) {
        if (mc.player == null || mc.level == null || mc.getConnection() == null || !mode.is("GrimFull")) {
            return;
        }

        if (event.isPost()) {
            if (pendingUseItem) {
                InteractionHand hand = isHoldingConsumable(mc.player.getMainHandItem())
                        ? InteractionHand.OFF_HAND
                        : InteractionHand.MAIN_HAND;
                ClientManger.useItem(mc.player, mc.level, hand);
                pendingUseItem = false;
            }
            return;
        }

        if (mc.player.getTicksUsingItem() >= 33 && isUse) {
            mc.options.keyUse.setDown(false);
            return;
        }

        KillAura killAura =  Core.INSTANCE.getModuleManager().getModule(KillAura.class);
        if (((killAura != null &&killAura.attackTarget != null) || !mc.player.isUsingItem()) && isUse) {
            while (!inBound.isEmpty()) {
                Packet<ClientGamePacketListener> poll = this.inBound.poll();
                poll.handle(mc.getConnection());
            }
            mc.getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, SWAP_POS, SWAP_DIRECTION));
            PacketUtils.sendPacketNoEvent(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM, SWAP_POS, SWAP_DIRECTION));
            useTicks = -1;
            onceUse = true;
            isUse = false;
            swapCount = 0;
            timer.reset();
            return;
        }
        BlockHitResult hitResult = GhostHand.raycastForChest();
        if (hitResult != null) return;
        if (mc.player.getTicksUsingItem() == 1 && !isUse && isHoldingConsumableForGrimFull()) {
            isUse = true;
            if (onceUse) {
                pendingNoSlow = true;
                onceUse = false;
            }
        }
    }

    public static boolean isHoldingConsumable(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return false;
        }
        Item item = itemStack.getItem();
        boolean isFood = itemStack.has(DataComponents.FOOD);
        boolean isPotion = item instanceof PotionItem;
        boolean isShield = item == Items.SHIELD;
        boolean isBow = item instanceof BowItem;
        boolean isCrossBow = item instanceof CrossbowItem;
        return isFood || isShield || isBow || isCrossBow || isPotion;
    }

    private boolean isHoldingConsumableForGrimFull() {
        if (mc.player == null) {
            return false;
        }

        ItemStack main = mc.player.getMainHandItem();
        ItemStack off = mc.player.getOffhandItem();
        if (!isHoldingConsumable(main) && !isHoldingConsumable(off)) {
            return false;
        }

        Item mainItem = main.isEmpty() ? null : main.getItem();
        Item offItem = off.isEmpty() ? null : off.getItem();
        return !(mainItem instanceof BowItem || mainItem instanceof CrossbowItem
                || offItem instanceof BowItem || offItem instanceof CrossbowItem);
    }

    private void flushInbound() {
        while (!inBound.isEmpty() && mc.getConnection() != null) {
            Packet<ClientGamePacketListener> packet = inBound.poll();
            if (packet != null) {
                packet.handle(mc.getConnection());
            }
        }
    }

    private void resetState() {
        useTicks = -1;
        isUse = false;
        swapCount = 0;
        onceUse = true;
        isActive = false;
        shouldPush = false;
        pauseTimer = 0;
        pendingNoSlow = false;
        pendingUseItem = false;
        inBound.clear();
    }
}
