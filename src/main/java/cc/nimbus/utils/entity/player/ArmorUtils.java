package cc.nimbus.utils.entity.player;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.Arrays;
import java.util.List;

public class ArmorUtils {
    public static final List<Item> armorHelmets = Arrays.asList(
            Items.LEATHER_HELMET,
            Items.GOLDEN_HELMET,
            Items.CHAINMAIL_HELMET,
            Items.IRON_HELMET,
            Items.DIAMOND_HELMET,
            Items.NETHERITE_HELMET
    );
    public static final List<Item> armorChestPlates = Arrays.asList(
            Items.LEATHER_CHESTPLATE,
            Items.GOLDEN_CHESTPLATE,
            Items.CHAINMAIL_CHESTPLATE,
            Items.IRON_CHESTPLATE,
            Items.DIAMOND_CHESTPLATE,
            Items.NETHERITE_CHESTPLATE
    );
    public static final List<Item> armorLeggings = Arrays.asList(
            Items.LEATHER_LEGGINGS,
            Items.GOLDEN_LEGGINGS,
            Items.CHAINMAIL_LEGGINGS,
            Items.IRON_LEGGINGS,
            Items.DIAMOND_LEGGINGS,
            Items.NETHERITE_LEGGINGS
    );
    public static final List<Item> armorBoots = Arrays.asList(
            Items.LEATHER_BOOTS,
            Items.GOLDEN_BOOTS,
            Items.CHAINMAIL_BOOTS,
            Items.IRON_BOOTS,
            Items.DIAMOND_BOOTS,
            Items.NETHERITE_BOOTS
    );

    public static double getHelmetDefense(ItemStack stack) {
        return getArmorDefense(stack, EquipmentSlot.HEAD);
    }

    public static double getChestPlateDefense(ItemStack stack) {
        return getArmorDefense(stack, EquipmentSlot.CHEST);
    }

    public static double getLeggingsDefense(ItemStack stack) {
        return getArmorDefense(stack, EquipmentSlot.LEGS);
    }

    public static double getBootsDefense(ItemStack stack) {
        return getArmorDefense(stack, EquipmentSlot.FEET);
    }

    public static int getProtectionLevel(ItemStack stack) {
        return stack == null || stack.isEmpty() ? -1 : InventoryUtils.enchantmentLevel(stack,(Enchantments.ALL_DAMAGE_PROTECTION));
    }

    public static int getMaterialPriority(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return -1;
        }

        Item item = stack.getItem();
        if (item == Items.LEATHER_HELMET || item == Items.LEATHER_CHESTPLATE || item == Items.LEATHER_LEGGINGS || item == Items.LEATHER_BOOTS) {
            return 0;
        }
        if (item == Items.GOLDEN_HELMET || item == Items.GOLDEN_CHESTPLATE || item == Items.GOLDEN_LEGGINGS || item == Items.GOLDEN_BOOTS) {
            return 1;
        }
        if (item == Items.CHAINMAIL_HELMET || item == Items.CHAINMAIL_CHESTPLATE || item == Items.CHAINMAIL_LEGGINGS || item == Items.CHAINMAIL_BOOTS) {
            return 2;
        }
        if (item == Items.IRON_HELMET || item == Items.IRON_CHESTPLATE || item == Items.IRON_LEGGINGS || item == Items.IRON_BOOTS) {
            return 3;
        }
        if (item == Items.DIAMOND_HELMET || item == Items.DIAMOND_CHESTPLATE || item == Items.DIAMOND_LEGGINGS || item == Items.DIAMOND_BOOTS) {
            return 4;
        }
        if (item == Items.NETHERITE_HELMET || item == Items.NETHERITE_CHESTPLATE || item == Items.NETHERITE_LEGGINGS || item == Items.NETHERITE_BOOTS) {
            return 5;
        }
        return -1;
    }


    private static double getArmorDefense(ItemStack stack, EquipmentSlot slot) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof ArmorItem armorItem) || armorItem.getEquipmentSlot() != slot) {
            return -1.0;
        }

        return armorItem.getDefense() + InventoryUtils.enchantmentLevel(stack, (Enchantments.ALL_DAMAGE_PROTECTION));
    }
}
