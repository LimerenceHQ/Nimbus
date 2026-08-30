package cc.nimbus.managers;

import cc.nimbus.core.interfaces.InstanceManager;
import cc.nimbus.event.EventBus;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.game.EventTick;
import cc.nimbus.event.impl.network.PacketEvent;
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
    private static Level fallDistanceLevel;
    private static Player fallDistancePlayer;
    private static long fallDistanceTick = Long.MIN_VALUE;
    private static double fallDistanceY;
    private static float trackedFallDistance;

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

    public static float fallDist() {
        if (mc.player == null || mc.player.onGround()) {
            resetFallDistanceTracking();
            return 0.0F;
        }

        updateFallDistanceTracking();
        return Math.max(0.0F, Math.max(mc.player.fallDistance, trackedFallDistance));
    }

    private static void updateFallDistanceTracking() {
        Player player = mc.player;
        Level level = mc.level;
        if (player == null || level == null) {
            resetFallDistanceTracking();
            return;
        }

        long gameTime = level.getGameTime();
        if (fallDistancePlayer != player || fallDistanceLevel != level) {
            fallDistancePlayer = player;
            fallDistanceLevel = level;
            fallDistanceTick = gameTime;
            fallDistanceY = player.getY();
            trackedFallDistance = 0.0F;
            return;
        }
        if (fallDistanceTick == gameTime) return;

        double deltaY = player.getY() - fallDistanceY;
        fallDistanceY = player.getY();
        fallDistanceTick = gameTime;
        if (deltaY > 0.0D || player.onGround() || player.isInWater() || player.onClimbable()) {
            trackedFallDistance = 0.0F;
        } else if (deltaY < 0.0D) {
            trackedFallDistance = Math.min(Float.MAX_VALUE, trackedFallDistance - (float) deltaY);
        }
    }

    private static void resetFallDistanceTracking() {
        fallDistancePlayer = null;
        fallDistanceLevel = null;
        fallDistanceTick = Long.MIN_VALUE;
        fallDistanceY = 0.0D;
        trackedFallDistance = 0.0F;
    }


    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!event.isSend()) return;
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
            ServerboundUseItemPacket packet = new ServerboundUseItemPacket(hand, sequence, player.getYRot(), player.getXRot());
            ItemStack original = player.getItemInHand(hand);
            if (player.getCooldowns().isOnCooldown(original)) return packet;

            result[0] = original.use(level, player, hand);
            return packet;
        });
        return result[0];
    }
}
