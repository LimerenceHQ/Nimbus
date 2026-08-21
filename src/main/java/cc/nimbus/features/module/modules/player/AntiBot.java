package cc.nimbus.features.module.modules.player;

import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.game.EventTick;
import cc.nimbus.event.impl.game.EventWorldLoad;
import cc.nimbus.event.impl.network.PacketEvent;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.MultiSelectSetting;
import cc.nimbus.features.module.api.setting.impl.NumberSetting;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ModuleInfo(
        name = "AntiBot",
        description = "Filters suspicious player entities from combat targets.",
        category = ModuleCategory.Player
)
public final class AntiBot extends BasicModule {
    public final MultiSelectSetting checks = new MultiSelectSetting(
            "Checks",
            new String[]{"Ground", "EntityID", "Sleep", "Armor", "Dead", "Name",
                    "UUID", "LivingTime", "PlayerInfo", "Hypixel"}
    );
    public final NumberSetting livingTime = new NumberSetting(
            "Living Time", 2500, 0, 10000, 100,
            () -> checks.isSelected("LivingTime"));

    private final Set<Integer> grounded = ConcurrentHashMap.newKeySet();
    private final Set<Integer> playerInfoEntityIds = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> displayNames = new ConcurrentHashMap<>();
    private final Map<UUID, Long> pendingProfiles = new ConcurrentHashMap<>();
    private final Map<UUID, Long> spawnTimes = new ConcurrentHashMap<>();

    @Override
    protected void onEnable() {
        clearCaches();
    }

    @Override
    protected void onDisable() {
        clearCaches();
    }

    @EventTarget
    private void onWorldLoad(EventWorldLoad event) {
        clearCaches();
    }

    @EventTarget
    private void onTick(EventTick event) {
        if (event.getPhase() != EventTick.Phase.PRE) return;
        long now = System.currentTimeMillis();
        pendingProfiles.entrySet().removeIf(entry -> now - entry.getValue() > 500L);
    }

    @EventTarget
    private void onPacket(PacketEvent event) {
        if (!event.isReceive()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;

        if (event.getPacket() instanceof ClientboundPlayerInfoUpdatePacket packet
                && packet.actions().contains(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER)) {
            long now = System.currentTimeMillis();
            for (ClientboundPlayerInfoUpdatePacket.Entry entry : packet.entries()) {
                if (entry.profile() == null || entry.gameMode() != GameType.SURVIVAL) continue;
                Component display = entry.displayName();
                UUID uuid = entry.profileId();
                spawnTimes.put(uuid, now);
                pendingProfiles.put(uuid, now);
                displayNames.put(uuid, display == null ? entry.profile().getName() : display.getString());
            }
            return;
        }

        if (event.getPacket() instanceof ClientboundMoveEntityPacket packet) {
            Entity entity = packet.getEntity(minecraft.level);
            if (entity != null && packet.isOnGround()) grounded.add(entity.getId());
            return;
        }

        if (event.getPacket() instanceof ClientboundAnimatePacket packet) {
            Entity entity = minecraft.level.getEntity(packet.getId());
            if (entity != null && packet.getAction() == ClientboundAnimatePacket.SWING_MAIN_HAND) {
                spawnTimes.remove(entity.getUUID());
            }
            return;
        }

        if (event.getPacket() instanceof ClientboundAddEntityPacket packet) {
            if (pendingProfiles.remove(packet.getUUID()) != null) playerInfoEntityIds.add(packet.getId());
            return;
        }

        if (event.getPacket() instanceof ClientboundRemoveEntitiesPacket packet) {
            IntList ids = packet.getEntityIds();
            for (int index = 0; index < ids.size(); index++) {
                int id = ids.getInt(index);
                grounded.remove(id);
                playerInfoEntityIds.remove(id);
            }
        }
    }

    public boolean isBot(LivingEntity entity) {
        if (!isEnabled()) return false;
        Minecraft minecraft = Minecraft.getInstance();

        if (checks.isSelected("Ground") && !grounded.contains(entity.getId())) return true;
        if (checks.isSelected("EntityID") && (entity.getId() >= 1_000_000_000 || entity.getId() <= -1)) return true;
        if (checks.isSelected("Sleep") && entity.isSleeping()) return true;
        if (checks.isSelected("Dead") && entity.isDeadOrDying()) return true;
        if (checks.isSelected("PlayerInfo") && playerInfoEntityIds.contains(entity.getId())) return true;
        if (checks.isSelected("LivingTime") && spawnTimes.containsKey(entity.getUUID())
                && System.currentTimeMillis() - spawnTimes.get(entity.getUUID()) < livingTime.getValue().longValue()) {
            return true;
        }
        if (checks.isSelected("UUID") && minecraft.getConnection() != null
                && minecraft.getConnection().getPlayerInfo(entity.getUUID()) == null) return true;
        if (checks.isSelected("Name") && entity.getDisplayName() != null) {
            String name = entity.getDisplayName().getString();
            if (name.contains("CIT-") || name.contains("[NPC]")) return true;
        }
        if (checks.isSelected("Armor") && entity instanceof Player player
                && player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
                && player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()
                && player.getItemBySlot(EquipmentSlot.LEGS).isEmpty()
                && player.getItemBySlot(EquipmentSlot.FEET).isEmpty()) return true;

        if (checks.isSelected("Hypixel")) {
            String name = entity.getName().getString();
            if (name.isEmpty() || entity.getDisplayName() == null || entity.isDeadOrDying()) return true;
            if (!tabListNames().contains(name)) return true;
            if (entity.getHealth() != 20.0f && name.startsWith("\u00a7c")) return true;
        }
        return false;
    }

    private static List<String> tabListNames() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.getConnection() == null) return List.of();
        List<PlayerInfo> entries = new ArrayList<>(minecraft.getConnection().getOnlinePlayers());
        Set<UUID> seen = new HashSet<>();
        entries.removeIf(entry -> !seen.add(entry.getProfile().getId())
                || Objects.equals(entry.getProfile().getId(), minecraft.player.getUUID()));
        return entries.stream().map(entry -> entry.getProfile().getName()).toList();
    }

    private void clearCaches() {
        grounded.clear();
        playerInfoEntityIds.clear();
        displayNames.clear();
        pendingProfiles.clear();
        spawnTimes.clear();
    }
}
