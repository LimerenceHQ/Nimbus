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
            return 0.0F;
        }

        float vanillaDistance = Math.max(0.0F, mc.player.fallDistance);
        if (vanillaDistance > 0.0F) {
            return vanillaDistance;
        }

        double verticalVelocity = mc.player.getDeltaMovement().y;
        int airborneTicks = GameStateManager.offGroundTicks;
        if (verticalVelocity >= 0.0D || airborneTicks <= 0) {
            return 0.0F;
        }

        double distance = 0.0D;
        double previousVelocity = verticalVelocity;
        for (int i = 0; i < airborneTicks; i++) {
            if (previousVelocity < 0.0D) {
                distance -= previousVelocity;
            }
            previousVelocity = (previousVelocity + 0.08D) / 0.98D;
        }

        return (float) Math.min(distance, Float.MAX_VALUE);
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
