package cc.nimbus.features.module.modules.player;

import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.ModeSetting;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

@ModuleInfo(
        name = "Teams",
        description = "Prevents combat modules from selecting teammates.",
        category = ModuleCategory.Player
)
public final class Teams extends BasicModule {
    public final ModeSetting mode = new ModeSetting(
            "Mode", new String[]{"Color", "Scoreboard", "Armor"}, "Color");

    public boolean isSameTeam(Player self, Player target) {
        if (!isEnabled() || self == target) return false;
        return switch (mode.getValue()) {
            case "Scoreboard" -> self.isAlliedTo(target);
            case "Armor" -> sameArmor(self, target);
            default -> self.getTeam() != null && target.getTeam() != null
                    && self.getTeamColor() == target.getTeamColor();
        };
    }

    private static boolean sameArmor(Player self, Player target) {
        boolean foundArmor = false;
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack own = self.getItemBySlot(slot);
            ItemStack other = target.getItemBySlot(slot);
            if (own.isEmpty() && other.isEmpty()) continue;
            foundArmor = true;
            if (!ItemStack.isSameItemSameComponents(own, other)) return false;
        }
        return foundArmor;
    }
}
