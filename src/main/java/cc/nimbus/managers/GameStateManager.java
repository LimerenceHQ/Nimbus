package cc.nimbus.managers;

import cc.nimbus.core.interfaces.InstanceManager;
import cc.nimbus.event.EventBus;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.game.EventTick;
import cc.nimbus.event.impl.network.PacketEvent;
import cc.nimbus.mixin.accessor.player.MultiPlayerGameModeAccessor;
import cc.nimbus.utils.network.PacketUtils;
import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.prediction.PredictiveAction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Objects;

import static net.minecraft.client.player.LocalPlayer.LOGGER;

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

    public static void handleInventoryMouseClick(int p_171800_, int p_171801_, int p_171802_, ClickType p_171803_, Player p_171804_) {
        AbstractContainerMenu abstractcontainermenu = p_171804_.containerMenu;
        if (p_171800_ != abstractcontainermenu.containerId) {
            LOGGER.warn("Ignoring click in mismatching container. Click in {}, player has {}.", p_171800_, abstractcontainermenu.containerId);
        } else {
            NonNullList<Slot> nonnulllist = abstractcontainermenu.slots;
            int i = nonnulllist.size();
            List<ItemStack> list = Lists.newArrayListWithCapacity(i);

            for(Slot slot : nonnulllist) {
                list.add(slot.getItem().copy());
            }

            abstractcontainermenu.clicked(p_171801_, p_171802_, p_171803_, p_171804_);
            Int2ObjectMap<ItemStack> int2objectmap = new Int2ObjectOpenHashMap();

            for(int j = 0; j < i; ++j) {
                ItemStack itemstack = list.get(j);
                ItemStack itemstack1 = nonnulllist.get(j).getItem();
                if (!ItemStack.matches(itemstack, itemstack1)) {
                    int2objectmap.put(j, itemstack1.copy());
                }
            }

            PacketUtils.sendPacketNoEvent(new ServerboundContainerClickPacket(p_171800_, abstractcontainermenu.getStateId(), p_171801_, p_171802_, p_171803_, abstractcontainermenu.getCarried().copy(), int2objectmap));
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
            ServerboundUseItemPacket packet = new ServerboundUseItemPacket(hand,sequence);
            ItemStack original = player.getItemInHand(hand);
            if (player.getCooldowns().isOnCooldown(original.getItem())) return packet;

            InteractionResultHolder<ItemStack> useResult = original.use(level, player, hand);
            InteractionResult interaction = useResult.getResult();
            ItemStack transformed = useResult.getObject();
            if (transformed != original) player.setItemInHand(hand, transformed);
            result[0] = interaction;
            return packet;
        });
        return result[0];
    }
}
