package cc.nimbus.features.module.modules.render;

import cc.nimbus.core.Core;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.game.EventTick;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.BooleanSetting;
import cc.nimbus.features.module.modules.player.Teams;
import cc.nimbus.ui.notification.NotificationType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@ModuleInfo(
        name = "ItemWarn",
        displayName = "Item Warn",
        description = "Warns about dangerous player effects and held items.",
        category = ModuleCategory.Render
)
public final class ItemWarn extends BasicModule {
    private static final Warning EMPTY = new Warning("", "", 0xFFFFFFFF, Kind.NONE);
    private final Map<Integer, Warning> previous = new HashMap<>();
    private ClientLevel level;
    private boolean initialized;

    public final BooleanSetting nametag = new BooleanSetting("Nametag", true);
    public final BooleanSetting clientNotification = new BooleanSetting("Client Notification", true);

    @EventTarget
    private void onTick(EventTick event) {
        if (!event.isPost()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            reset();
            return;
        }
        if (level != minecraft.level) {
            reset();
            level = minecraft.level;
        }

        Set<Integer> active = new HashSet<>();
        for (Player player : minecraft.level.players()) {
            if (player == minecraft.player || player.isRemoved() || player.isSpectator()) continue;
            active.add(player.getId());
            Warning current = detect(minecraft.player, player);
            Warning old = previous.put(player.getId(), current);
            if (!initialized || !clientNotification.getValue() || current.kind() == Kind.NONE
                    || current.equals(old)) continue;
            Core.INSTANCE.getNotificationManager().postClient(
                    "Item Warning",
                    player.getName().getString() + " " + current.label(),
                    2200L,
                    NotificationType.ERROR
            );
        }
        previous.keySet().removeIf(id -> !active.contains(id));
        initialized = true;
    }

    public Warning nametagWarning(Player self, Player target) {
        if (!isEnabled() || !nametag.getValue()) return EMPTY;
        return detect(self, target);
    }

    public static Warning detect(Player self, Player target) {
        if (self == null || target == null || self == target) return EMPTY;

        Teams teams = Core.INSTANCE.isInitialized()
                ? Core.INSTANCE.getModuleManager().getModule(Teams.class) : null;
        if (teams != null && teams.isSameTeam(self, target)) {
            return new Warning("team", "[Team] ", 0xFF96C077, Kind.TEAM);
        }
        if (target.hasEffect(MobEffects.DAMAGE_BOOST)) {
            return new Warning("strength", "[Strength] ", 0xFFE06C75, Kind.ITEM);
        }
        if (target.hasEffect(MobEffects.REGENERATION)) {
            return new Warning("regen", "[Regen] ", 0xFFE06C75, Kind.ITEM);
        }
        ItemStack held = target.getMainHandItem();
        if (isGodAxe(held)) {
            return new Warning("god_axe", "[GodAxe] ", 0xFFE06C75, Kind.ITEM);
        }
        if (isKnockbackBall(held)) {
            return new Warning("knockback_ball", "[KBBall] ", 0xFFE06C75, Kind.ITEM);
        }
        if (isGappleActive(target, held)) {
            return new Warning("gapple", "[GApple] ", 0xFFE06C75, Kind.ITEM);
        }
        return EMPTY;
    }

    private static boolean isGodAxe(ItemStack stack) {
        return stack != null && stack.is(Items.GOLDEN_AXE)
                && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SHARPNESS, stack) > 100;
    }

    private static boolean isKnockbackBall(ItemStack stack) {
        return stack != null && stack.is(Items.SLIME_BALL)
                && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.KNOCKBACK, stack) > 0;
    }

    private static boolean isGappleActive(Player player, ItemStack held) {
        return player.getAbsorptionAmount() > 0f || held.is(Items.ENCHANTED_GOLDEN_APPLE);
    }

    private void reset() {
        previous.clear();
        level = null;
        initialized = false;
    }

    @Override
    protected void onDisable() {
        reset();
    }

    public enum Kind {
        NONE,
        TEAM,
        ITEM
    }

    public record Warning(String key, String label, int color, Kind kind) {
        public boolean isEmpty() {
            return kind == Kind.NONE;
        }
    }
}
