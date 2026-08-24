package cc.nimbus.utils.entity.player;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.*;

import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

public final class InventoryUtils {
    public static final int HELMET_SLOT_INVENTORY = 39;
    public static final int CHEST_SLOT_INVENTORY = 38;
    public static final int LEGS_SLOT_INVENTORY = 37;
    public static final int FEET_SLOT_INVENTORY = 36;
    public static final int OFFHAND_SLOT_INVENTORY = 40;
    public static final int HELMET_SLOT_MENU = 5;
    public static final int CHEST_SLOT_MENU = 6;
    public static final int LEGS_SLOT_MENU = 7;
    public static final int FEET_SLOT_MENU = 8;
    public static final int OFFHAND_SLOT_MENU = 45;
    public static final int  HELMET_SLOT_INTERACTION_MANAGER = 5;
    public static final int CHEST_PLATE_SLOT_INVENTORY = 38, CHEST_PLATE_SLOT_INTERACTION_MANAGER = 6;
    public static final int LEGGINGS_SLOT_INVENTORY = 37, LEGGINGS_SLOT_INTERACTION_MANAGER = 7;
    public static final int BOOTS_SLOT_INVENTORY = 36, BOOTS_SLOT_INTERACTION_MANAGER = 8;

    private static final Minecraft MINECRAFT = Minecraft.getInstance();

    private InventoryUtils() {
    }

    public static boolean isItemValid(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return !notUsefulItem(stack);
    }

    public static boolean hasValidProfileComponent(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(Items.PLAYER_HEAD)) return true;
        CompoundTag owner = stack.getTagElement("SkullOwner");
        return owner == null || !owner.contains("Name", 8)
                || owner.getString("Name").matches("[A-Za-z0-9_]{3,16}");
    }

    public static ItemStack getBestWeapon() {
        return bestStack(InventoryUtils::isWeapon, InventoryUtils::weaponScore, ItemStack.EMPTY);
    }

    public static ItemStack getBestBow() {
        return bestStack(stack -> stack.getItem() instanceof BowItem, InventoryUtils::bowScore, ItemStack.EMPTY);
    }

    public static ItemStack getBestCrossbow() {
        return bestStack(stack -> stack.getItem() instanceof CrossbowItem, InventoryUtils::crossbowScore, ItemStack.EMPTY);
    }

    public static ItemStack getBestAxe(boolean excludeWeapon) {
        return bestStack(stack -> stack.is(ItemTags.AXES), InventoryUtils::axeScore,
                excludeWeapon ? getBestWeapon() : ItemStack.EMPTY);
    }

    public static ItemStack getBestPickaxe(boolean excludeWeapon) {
        return bestStack(stack -> stack.is(ItemTags.PICKAXES), InventoryUtils::pickaxeScore,
                excludeWeapon ? getBestWeapon() : ItemStack.EMPTY);
    }

    public static ItemStack getBestShovel(boolean excludeWeapon) {
        return bestStack(stack -> stack.is(ItemTags.SHOVELS), InventoryUtils::shovelScore,
                excludeWeapon ? getBestWeapon() : ItemStack.EMPTY);
    }


    public static ItemStack getBestShears() {
        return bestStack(stack -> stack.is(Items.SHEARS), InventoryUtils::shearsScore, ItemStack.EMPTY);
    }

    public static ItemStack getBestArmor(EquipmentSlot slot) {
        return bestStack(stack -> armorSlot(stack) == slot, InventoryUtils::armorScore, ItemStack.EMPTY);
    }

    public static ItemStack getBestHelmet() {
        return getBestArmorItem(ArmorUtils::getHelmetDefense);
    }

    public static ItemStack getBestChestPlate() {
        return getBestArmorItem(ArmorUtils::getChestPlateDefense);
    }

    public static ItemStack getBestLeggings() {
        return getBestArmorItem(ArmorUtils::getLeggingsDefense);
    }

    public static ItemStack getBestBoots() {
        return getBestArmorItem(ArmorUtils::getBootsDefense);
    }


    public static ItemStack getBestFlintAndSteel() {
        return bestStack(stack -> stack.is(Items.FLINT_AND_STEEL), InventoryUtils::durability, ItemStack.EMPTY);
    }

    public static ItemStack getGodAxe() {
        return first(stack -> stack.is(Items.GOLDEN_AXE) && enchantmentLevel(stack, Enchantments.SHARPNESS) > 100);
    }

    public static ItemStack getKnockbackBall() {
        return first(stack -> stack.is(Items.SLIME_BALL) && enchantmentLevel(stack, Enchantments.KNOCKBACK) > 0);
    }

    public static boolean isWeapon(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && (stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES)
                || stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.SHOVELS));
    }

    public static double weaponScore(ItemStack stack) {
        if (!isWeapon(stack)) return -1.0;
        return legacyWeaponDamage(stack)
                + enchantmentLevel(stack, Enchantments.SHARPNESS) * 0.5 + durability(stack) * 1.0E-6;
    }

    public static double attackSpeed(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return -1.0;
        return attributeValueWithStack(stack, Attributes.ATTACK_SPEED, EquipmentSlot.MAINHAND);
    }

    public static double bowScore(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof BowItem)) return -1.0;
        return 2.0 + enchantmentLevel(stack, Enchantments.POWER_ARROWS) * 0.5
                + enchantmentLevel(stack, Enchantments.PUNCH_ARROWS) * 0.25
                + enchantmentLevel(stack, Enchantments.FLAMING_ARROWS) * 0.1
                + enchantmentLevel(stack, Enchantments.INFINITY_ARROWS) * 0.1
                + durability(stack) * 1.0E-6;
    }

    public static double crossbowScore(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof CrossbowItem)) return -1.0;
        return enchantmentLevel(stack, Enchantments.QUICK_CHARGE) * 2.0
                + enchantmentLevel(stack, Enchantments.MULTISHOT)
                + enchantmentLevel(stack, Enchantments.PIERCING) * 0.5
                + durability(stack) * 1.0E-6;
    }

    public static double axeScore(ItemStack stack) {
        return toolScore(stack, ItemTags.AXES, Blocks.OAK_LOG.defaultBlockState());
    }

    public static double pickaxeScore(ItemStack stack) {
        return toolScore(stack, ItemTags.PICKAXES, Blocks.STONE.defaultBlockState());
    }

    public static double shovelScore(ItemStack stack) {
        return toolScore(stack, ItemTags.SHOVELS, Blocks.DIRT.defaultBlockState());
    }

    public static double shearsScore(ItemStack stack) {
        if (stack == null || !stack.is(Items.SHEARS)) return -1.0;
        return stack.getDestroySpeed(Blocks.OAK_LEAVES.defaultBlockState())
                + enchantmentLevel(stack, Enchantments.BLOCK_EFFICIENCY) + durability(stack) * 1.0E-6;
    }

    public static double armorScore(ItemStack stack) {
        EquipmentSlot slot = armorSlot(stack);
        if (slot == null) return -1.0;
        ArmorItem armor = (ArmorItem) stack.getItem();
        return armor.getDefense() * 10.0
                + armor.getToughness() * 2.0
                + enchantmentLevel(stack, Enchantments.ALL_DAMAGE_PROTECTION) * 2.0
                + durability(stack) * 1.0E-6;
    }

    public static boolean sameBestRank(ItemStack stack, ItemStack best, ToDoubleFunction<ItemStack> scorer) {
        return stack != null && best != null && !stack.isEmpty() && !best.isEmpty()
                && Double.compare(scorer.applyAsDouble(stack), scorer.applyAsDouble(best)) == 0
                && durability(stack) == durability(best);
    }

    public static int durability(ItemStack stack) {
        return stack != null && stack.isDamageableItem()
                ? stack.getMaxDamage() - stack.getDamageValue() : Integer.MAX_VALUE;
    }

    public static EquipmentSlot armorSlot(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        if (stack.getItem() instanceof ArmorItem armor) return armor.getType().getSlot();
        return null;
    }

    public static boolean isWantedPotion(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION))) return false;
        return true;
    }

    public static boolean isConsumable(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && (stack.isEdible() || stack.is(Items.POTION)
                || stack.is(Items.SPLASH_POTION) || stack.is(Items.MILK_BUCKET));
    }

    public static boolean notUsefulItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return true;
        EquipmentSlot armorSlot = armorSlot(stack);
        if (armorSlot != null) return armorScore(stack) < armorScore(getBestArmor(armorSlot));
        if (stack.getItem() instanceof BowItem) return bowScore(stack) < bowScore(getBestBow());
        if (stack.getItem() instanceof CrossbowItem) return crossbowScore(stack) < crossbowScore(getBestCrossbow());
        if (isWeapon(stack)) {
            boolean useful = weaponScore(stack) >= weaponScore(getBestWeapon());
            if (stack.is(ItemTags.AXES)) useful |= axeScore(stack) >= axeScore(getBestAxe(false));
            if (stack.is(ItemTags.PICKAXES)) useful |= pickaxeScore(stack) >= pickaxeScore(getBestPickaxe(false));
            if (stack.is(ItemTags.SHOVELS)) useful |= shovelScore(stack) >= shovelScore(getBestShovel(false));
            return !useful;
        }
        if (stack.is(Items.SHEARS)) return shearsScore(stack) < shearsScore(getBestShears());
        if (stack.is(Items.FLINT_AND_STEEL)) return stack != getBestFlintAndSteel();
        if (stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION)) return !isWantedPotion(stack);
        if (stack.is(Items.PLAYER_HEAD)) return false;

        if (stack.getItem() instanceof BlockItem blockItem) return badBlock(blockItem.getBlock());

        if (stack.is(Items.SLIME_BALL) && enchantmentLevel(stack, Enchantments.KNOCKBACK) > 0) return false;
        if (stack.is(Items.FIRE_CHARGE) || stack.is(Items.TOTEM_OF_UNDYING)
                || stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE)
                || stack.is(Items.FISHING_ROD) || stack.is(Items.SNOWBALL) || stack.is(Items.EGG)
                || stack.is(Items.COAL) || stack.is(Items.IRON_INGOT) || stack.is(Items.GOLD_INGOT)
                || stack.is(Items.DIAMOND) || stack.is(Items.EMERALD) || stack.is(Items.NETHER_STAR)
                || stack.is(Items.BUCKET) || stack.is(Items.WATER_BUCKET) || stack.is(Items.LAVA_BUCKET)
                || stack.is(Items.MILK_BUCKET) || stack.is(Items.ENDER_PEARL) || stack.is(Items.ARROW)
                || stack.is(Items.COMPASS) || stack.is(Items.STICK) || stack.is(Items.ELYTRA)
                || stack.is(Items.SHIELD)
                || stack.is(Items.END_CRYSTAL)) return false;

        return !isConsumable(stack);
    }

    public static boolean shouldDisableFeatures() {
        if (MINECRAFT.player == null) return true;
        int size = MINECRAFT.player.getInventory().getContainerSize();
        for (int slot = 0; slot < size; slot++) {
            String name = MINECRAFT.player.getInventory().getItem(slot).getHoverName().getString();
            if (name.contains("长按点击") || name.contains("点击使用") || name.contains("离开游戏")
                    || name.contains("选择一个队伍") || name.contains("再来一局")) return true;
        }
        return false;
    }

    public static int enchantmentLevel(ItemStack stack, Enchantment enchantment) {
        if (stack == null || stack.isEmpty() || MINECRAFT.level == null) return 0;
        return EnchantmentHelper.getItemEnchantmentLevel(enchantment, stack);
    }

    private static ItemStack first(Predicate<ItemStack> predicate) {
        if (MINECRAFT.player == null) return ItemStack.EMPTY;
        int size = MINECRAFT.player.getInventory().getContainerSize();
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = MINECRAFT.player.getInventory().getItem(slot);
            if (!stack.isEmpty() && predicate.test(stack)) return stack;
        }
        return ItemStack.EMPTY;
    }

    private static boolean isBetterArmor(ItemStack stack, ItemStack bestStack, ToDoubleFunction<ItemStack> scorer) {
        if (bestStack.isEmpty()) {
            return true;
        }

        int material = ArmorUtils.getMaterialPriority(stack);
        int bestMaterial = ArmorUtils.getMaterialPriority(bestStack);
        if (material != bestMaterial) {
            return material > bestMaterial;
        }

        int protection = ArmorUtils.getProtectionLevel(stack);
        int bestProtection = ArmorUtils.getProtectionLevel(bestStack);
        if (protection != bestProtection) {
            return protection > bestProtection;
        }

        return getRemainingDurability(stack) > getRemainingDurability(bestStack);
    }

    public static int getRemainingDurability(ItemStack stack) {
        return stack.isDamageableItem() ? stack.getMaxDamage() - stack.getDamageValue() : Integer.MAX_VALUE;
    }

    private static ItemStack bestStack(Predicate<ItemStack> predicate, ToDoubleFunction<ItemStack> scorer,
                                       ItemStack excluded) {
        if (MINECRAFT.player == null) return ItemStack.EMPTY;
        ItemStack best = ItemStack.EMPTY;
        double bestScore = Double.NEGATIVE_INFINITY;
        int bestDurability = Integer.MIN_VALUE;
        int size = MINECRAFT.player.getInventory().getContainerSize();
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = MINECRAFT.player.getInventory().getItem(slot);
            if (stack.isEmpty() || stack == excluded || !predicate.test(stack)
                    || stack.is(Items.GOLDEN_AXE) && enchantmentLevel(stack, Enchantments.SHARPNESS) > 100) continue;
            double score = scorer.applyAsDouble(stack);
            int durability = durability(stack);
            if (score > bestScore || Double.compare(score, bestScore) == 0 && durability > bestDurability) {
                best = stack;
                bestScore = score;
                bestDurability = durability;
            }
        }
        return best;
    }

    private static double toolScore(ItemStack stack, net.minecraft.tags.TagKey<Item> tag,
                                    net.minecraft.world.level.block.state.BlockState state) {
        if (stack == null || !stack.is(tag)) return -1.0;
        int efficiency = enchantmentLevel(stack, Enchantments.BLOCK_EFFICIENCY);
        return stack.getDestroySpeed(state) + (efficiency > 0 ? efficiency * efficiency + 1 : 0)
                + durability(stack) * 1.0E-6;
    }

    private static double legacyWeaponDamage(ItemStack stack) {
        Item item = stack.getItem();
        if (item == Items.NETHERITE_SWORD || item == Items.DIAMOND_SWORD) return 8.0;
        if (item == Items.IRON_SWORD) return 7.0;
        if (item == Items.STONE_SWORD) return 6.0;
        if (item == Items.GOLDEN_SWORD || item == Items.WOODEN_SWORD) return 5.0;
        if (item == Items.NETHERITE_AXE) return 10.0;
        if (item == Items.DIAMOND_AXE) return 7.0;
        if (item == Items.IRON_AXE) return 6.0;
        if (item == Items.STONE_AXE) return 5.0;
        if (item == Items.GOLDEN_AXE || item == Items.WOODEN_AXE) return 4.0;
        if (item == Items.NETHERITE_PICKAXE || item == Items.DIAMOND_PICKAXE) return 6.0;
        if (item == Items.IRON_PICKAXE) return 5.0;
        if (item == Items.STONE_PICKAXE) return 4.0;
        if (item == Items.GOLDEN_PICKAXE || item == Items.WOODEN_PICKAXE) return 3.0;
        if (item == Items.NETHERITE_SHOVEL) return 6.5;
        if (item == Items.DIAMOND_SHOVEL) return 5.0;
        if (item == Items.IRON_SHOVEL) return 4.0;
        if (item == Items.STONE_SHOVEL) return 3.0;
        if (item == Items.GOLDEN_SHOVEL || item == Items.WOODEN_SHOVEL) return 2.0;
        return attributeValueWithStack(stack, Attributes.ATTACK_DAMAGE, EquipmentSlot.MAINHAND);
    }

    private static double attributeValueWithStack(ItemStack stack, Attribute attribute, EquipmentSlot slot) {
        if (MINECRAFT.player == null || stack == null || stack.isEmpty()) return 0.0;
        double base = MINECRAFT.player.getAttributeBaseValue(attribute);
        double addition = 0.0;
        double multiplyBase = 0.0;
        double multiplyTotal = 0.0;
        for (AttributeModifier modifier : stack.getAttributeModifiers(slot).get(attribute)) {
            switch (modifier.getOperation()) {
                case ADDITION -> addition += modifier.getAmount();
                case MULTIPLY_BASE -> multiplyBase += modifier.getAmount();
                case MULTIPLY_TOTAL -> multiplyTotal += modifier.getAmount();
            }
        }
        return (base + addition + base * multiplyBase) * (1.0 + multiplyTotal);
    }

    private static ItemStack getBestArmorItem(ToDoubleFunction<ItemStack> scorer) {
        if (MINECRAFT.player == null) {
            return ItemStack.EMPTY;
        }

        ItemStack bestStack = ItemStack.EMPTY;
        int size = MINECRAFT.player.getInventory().getContainerSize();

        for (int i = 0; i < size; i++) {
            ItemStack stack = MINECRAFT.player.getInventory().getItem(i);
            double defense = scorer.applyAsDouble(stack);
            if (defense < 0) {
                continue;
            }

            if (isBetterArmor(stack, bestStack, scorer)) {
                bestStack = stack;
            }
        }

        return bestStack;
    }


    private static boolean badBlock(Block block) {
        return block instanceof AirBlock || block instanceof StairBlock || block instanceof SlabBlock
                || block instanceof FenceBlock || block instanceof FenceGateBlock || block instanceof DoorBlock
                || block instanceof TrapDoorBlock || block instanceof PressurePlateBlock || block instanceof ButtonBlock
                || block instanceof WallBlock || block instanceof CarpetBlock || block instanceof ConcretePowderBlock
                || block instanceof TintedGlassBlock || block instanceof StainedGlassBlock
                || block instanceof StainedGlassPaneBlock || block instanceof BannerBlock || block instanceof LeavesBlock
                || block instanceof SaplingBlock || block instanceof MushroomBlock || block instanceof FlowerBlock
                || block instanceof BushBlock || block instanceof ComposterBlock || block instanceof NoteBlock
                || block instanceof JukeboxBlock || block instanceof SignBlock || block == Blocks.IRON_BARS
                || block == Blocks.IRON_BLOCK || block == Blocks.GLASS_PANE || block == Blocks.DIRT_PATH
                || block == Blocks.FARMLAND || block == Blocks.SNOW || block == Blocks.AMETHYST_CLUSTER
                || block == Blocks.MANGROVE_ROOTS || block == Blocks.SUGAR_CANE || block == Blocks.CACTUS
                || block == Blocks.LILY_PAD;
    }
}
