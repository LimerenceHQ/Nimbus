package cc.nimbus.managers;

import cc.nimbus.core.interfaces.InstanceManager;
import cc.nimbus.event.EventBus;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.game.EventTick;
import cc.nimbus.event.impl.network.EventPacketSend;
import cc.nimbus.mixin.accessor.player.MultiPlayerGameModeAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.prediction.PredictiveAction;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

public class GameStateManager implements InstanceManager {
    private static final Minecraft mc = Minecraft.getInstance();
    public static float timerSpeed = 1.0f;
    public static int onGroundTicks = 0, offGroundTicks = 0, skipTicks = 0;
    public static Vec3 eyePos = null;

    private static boolean packetOnGround;

    public static float getTimerSpeed() {
        return timerSpeed;
    }

    public GameStateManager(Minecraft minecraft) {
        Objects.requireNonNull(minecraft, "minecraft");
    }

    @Override
    public synchronized void init() {
        EventBus.register(this);
    }

    @Override
    public synchronized void shutdown() {
        EventBus.unregister(this);
    }

    public static void sendSequencedPacket(PredictiveAction packetCreator) {
        Objects.requireNonNull(packetCreator, "packetCreator");
        if (mc.level == null || mc.getConnection() == null || mc.gameMode == null) return;

        ((MultiPlayerGameModeAccessor) mc.gameMode).nimbus$startPrediction(mc.level, packetCreator);
    }

    public static boolean clientOnGround() {
        if (mc.player == null) return false;
        return packetOnGround && mc.player.onGround();
    }

    @EventTarget
    public void onPacket(EventPacketSend event) {
        if (mc.level == null) {
            return;
        }
        if (event.getPacket() instanceof ServerboundMovePlayerPacket packet) {
            packetOnGround = packet.isOnGround();
        }

    }

    @EventTarget
    private void onTick(EventTick event) {
        if (!event.isPre()) return;
        if (mc.player != null) {
            if (mc.player.onGround()) {
                onGroundTicks++;
                offGroundTicks = 0;
            } else {
                onGroundTicks = 0;
                offGroundTicks++;
            }
        } else {
            onGroundTicks = 0;
            offGroundTicks = 0;
        }
    }

    public static InteractionResult useItem(Player player, Level level, InteractionHand hand) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(hand, "hand");
        if (mc.gameMode == null || mc.getConnection() == null || mc.level == null) {
            return InteractionResult.PASS;
        }

        MultiPlayerGameModeAccessor gameMode = (MultiPlayerGameModeAccessor) mc.gameMode;
        if (gameMode.nimbus$getLocalPlayerMode() == GameType.SPECTATOR) {
            return InteractionResult.PASS;
        }

        gameMode.nimbus$ensureHasSentCarriedItem();
        InteractionResult[] result = {InteractionResult.PASS};
        sendSequencedPacket(sequence -> {
            ServerboundUseItemPacket packet = new ServerboundUseItemPacket(
                    hand, sequence, player.getYRot(), player.getXRot());
            ItemStack original = player.getItemInHand(hand);
            if (player.getCooldowns().isOnCooldown(original)) return packet;

            InteractionResult interaction = original.use(level, player, hand);
            ItemStack transformed = interaction instanceof InteractionResult.Success success
                    && success.heldItemTransformedTo() != null
                    ? success.heldItemTransformedTo()
                    : player.getItemInHand(hand);
            if (transformed != original) player.setItemInHand(hand, transformed);
            result[0] = interaction;
            return packet;
        });
        return result[0];
    }
}
