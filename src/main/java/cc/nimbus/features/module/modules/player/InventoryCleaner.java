package cc.nimbus.features.module.modules.player;

import cc.nimbus.core.Core;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.component.Priority;
import cc.nimbus.event.impl.game.EventTick;
import cc.nimbus.event.impl.game.EventWorldLoad;
import cc.nimbus.event.impl.network.PacketEvent;
import cc.nimbus.event.impl.player.EventMoveInput;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.BooleanSetting;
import cc.nimbus.features.module.api.setting.impl.ModeSetting;
import cc.nimbus.features.module.api.setting.impl.NumberSetting;
import cc.nimbus.features.module.modules.combat.KillAura;
import cc.nimbus.features.module.modules.movement.Scaffold;
import cc.nimbus.features.module.modules.movement.Sprint;
import cc.nimbus.mixin.accessor.player.LocalPlayerAccessor;
import cc.nimbus.utils.client.TimerUtils;
import cc.nimbus.utils.entity.player.InventoryUtils;
import cc.nimbus.utils.entity.player.MovementUtils;
import cc.nimbus.utils.network.PacketUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BookItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ExperienceBottleItem;
import net.minecraft.world.item.FireworkRocketItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.PlayerHeadItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.FungusBlock;
import net.minecraft.world.level.block.SlabBlock;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

@ModuleInfo(
        name = "InventoryCleaner",
        alias = {"invmanager", "invcleaner"},
        description = "Silently equips, sorts and cleans the player inventory.",
        category = ModuleCategory.Player
)
public final class InventoryCleaner extends BasicModule {
    private static final int MAIN_INVENTORY_SIZE = 36;
    private static final int MAX_QUEUED_DROPS = 512;
    private static final Set<Block> BLOCK_BLACKLIST = Set.of(
            Blocks.AIR, Blocks.WATER, Blocks.LAVA, Blocks.ENCHANTING_TABLE, Blocks.GLASS_PANE,
            Blocks.IRON_BARS, Blocks.SNOW, Blocks.COAL_ORE, Blocks.DIAMOND_ORE, Blocks.EMERALD_ORE,
            Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.TORCH, Blocks.ANVIL, Blocks.NOTE_BLOCK,
            Blocks.JUKEBOX, Blocks.TNT, Blocks.GOLD_ORE, Blocks.IRON_ORE, Blocks.LAPIS_ORE,
            Blocks.STONE_PRESSURE_PLATE, Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE,
            Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE, Blocks.STONE_BUTTON, Blocks.LEVER,
            Blocks.TALL_GRASS, Blocks.TRIPWIRE, Blocks.TRIPWIRE_HOOK, Blocks.RAIL,
            Blocks.CORNFLOWER, Blocks.RED_MUSHROOM, Blocks.BROWN_MUSHROOM, Blocks.VINE,
            Blocks.SUNFLOWER, Blocks.LADDER, Blocks.FURNACE, Blocks.SAND, Blocks.CACTUS,
            Blocks.DISPENSER, Blocks.DROPPER, Blocks.CRAFTING_TABLE, Blocks.COBWEB,
            Blocks.PUMPKIN, Blocks.COBBLESTONE_WALL, Blocks.OAK_FENCE, Blocks.REDSTONE_TORCH,
            Blocks.FLOWER_POT
    );

    public final NumberSetting delay = new NumberSetting("Delay (Ticks)", 350, 0, 2000, 1);
    public final ModeSetting offhandItems = new ModeSetting(
            "Offhand Items", new String[]{"None", "Golden Apple", "Projectile", "Fishing Rod", "Block"});
    public final BooleanSetting autoArmor = new BooleanSetting("Auto Armor", true);
    public final BooleanSetting disOnKillaura = new BooleanSetting("Dis Only KillAura", false);
    public final BooleanSetting fastdrop = new BooleanSetting("Fast Drop", true);
    public final BooleanSetting inventoryOnly = new BooleanSetting("Inventory Only", false);
    public final BooleanSetting noMove = new BooleanSetting("NoMove Only", false);
    public final BooleanSetting switchSword = new BooleanSetting("Switch Sword", true);
    public final NumberSetting swordSlot = new NumberSetting("Sword Slot", 1, 1, 9, 1);
    public final BooleanSetting switchBlock = new BooleanSetting("Switch Block", true);
    public final NumberSetting blockSlot = new NumberSetting("Block Slot", 2, 1, 9, 1);
    public final NumberSetting maxBlockSize = new NumberSetting("Max Block Size", 256, 64, 512, 64);
    public final BooleanSetting switchPickaxe = new BooleanSetting("Switch Pickaxe", true);
    public final NumberSetting pickaxeSlot = new NumberSetting("Pickaxe Slot", 3, 1, 9, 1);
    public final BooleanSetting switchAxe = new BooleanSetting("Switch Axe", true);
    public final NumberSetting axeSlot = new NumberSetting("Axe Slot", 4, 1, 9, 1);
    public final BooleanSetting switchBow = new BooleanSetting("Switch Bow or Crossbow", true);
    public final NumberSetting bowSlot = new NumberSetting("Bow Slot", 5, 1, 9, 1);
    public final ModeSetting preferBow = new ModeSetting(
            "Bow Priority", new String[]{"Crossbow", "Power Bow", "Punch Bow"});
    public final NumberSetting maxArrowSize = new NumberSetting("Max Arrow Size", 256, 64, 512, 64);
    public final BooleanSetting switchWaterBucket = new BooleanSetting("Switch Water Bucket", true);
    public final NumberSetting waterBucketSlot = new NumberSetting("Water Bucket Slot", 6, 1, 9, 1);
    public final BooleanSetting switchEnderPearl = new BooleanSetting("Switch Ender Pearl", true);
    public final NumberSetting enderPearlSlot = new NumberSetting("Ender Pearl Slot", 7, 1, 9, 1);
    public final BooleanSetting switchFireball = new BooleanSetting("Switch Fireball", true);
    public final NumberSetting fireballSlot = new NumberSetting("Fireball Slot", 8, 1, 9, 1);
    public final BooleanSetting switchGoldenApple = new BooleanSetting("Switch Golden Apple", true);
    public final NumberSetting goldenAppleSlot = new NumberSetting("Golden Apple Slot", 9, 1, 9, 1);
    public final BooleanSetting throwItems = new BooleanSetting("Throw Items", true);
    public final NumberSetting waterBucketCount = new NumberSetting("Keep Water Buckets", 1, 0, 5, 1);
    public final NumberSetting lavaBucketCount = new NumberSetting("Keep Lava Buckets", 1, 0, 5, 1);
    public final BooleanSetting keepProjectile = new BooleanSetting("Keep Eggs & Snowballs", true);
    public final BooleanSetting switchProjectile = new BooleanSetting("Switch Eggs & Snowballs", false);
    public final NumberSetting projectileSlot = new NumberSetting("Eggs & Snowballs Slot", 9, 1, 9, 1);
    public final NumberSetting maxProjectileSize = new NumberSetting("Max Eggs & Snowballs Size", 64, 16, 256, 16);
    public final BooleanSetting switchRod = new BooleanSetting("Switch Rod", false);
    public final NumberSetting rodSlot = new NumberSetting("Rod Slot", 9, 1, 9, 1);

    private final Minecraft minecraft = Minecraft.getInstance();
    private final TimerUtils timer = new TimerUtils();
    private final Deque<Packet<?>> packetQueue = new ArrayDeque<>();
    private boolean clickOffHand;
    private boolean inventoryOpen;
    private boolean stopSprint;
    private int noMoveTicks;

    @Override
    protected void onEnable() {
        resetState(false);
    }

    @Override
    protected void onDisable() {
        resetState(true);
    }

    @EventTarget
    private void onWorldLoad(EventWorldLoad event) {
        resetState(false);
    }

    @EventTarget(priority = Priority.Highest)
    private void onPacket(PacketEvent event) {
        if (!event.isSend() || minecraft.player == null || minecraft.level == null) return;
        Packet<?> packet = event.getPacket();

        if (fastdrop.getValue() && inventoryOpen
                && packet instanceof ServerboundContainerClickPacket click
                && click.getClickType() == ClickType.THROW) {
            event.cancelEvent();
            if (packetQueue.size() < MAX_QUEUED_DROPS) packetQueue.addLast(packet);
            else PacketUtils.sendPacketNoEvent(packet);
            return;
        }

        if (packet instanceof ServerboundContainerClosePacket) inventoryOpen = false;
        if (!noMove.getValue()) return;
        if (!inventoryOpen || inventoryOnly.getValue()) return;

        boolean interrupts = packet instanceof ServerboundMovePlayerPacket && MovementUtils.isMoving()
                || packet instanceof ServerboundUseItemOnPacket
                || packet instanceof ServerboundUseItemPacket
                || packet instanceof ServerboundInteractPacket
                || packet instanceof ServerboundPlayerActionPacket;
        if (interrupts) finishSession();
    }

    @EventTarget
    private void onTick(EventTick event) {
        if (event.isPost()) {
            releaseSprint();
            return;
        }
        if (!event.isPre()) return;
        setSuffix(delay.getValue().intValue());

        if (!canRun()) {
            pauseSession();
            return;
        }

        boolean needsOrganizing = needsInventoryOrganizing();
        if (needsOrganizing && !inventoryOpen) inventoryOpen = true;
        if (inventoryOpen) organizeInventory();

        needsOrganizing = needsInventoryOrganizing();
        if (!needsOrganizing && inventoryOpen && isInventoryOrganizingComplete()) finishSession();
    }

    @EventTarget(priority = Priority.Lowest)
    private void onMoveInput(EventMoveInput event) {
        if (!stopSprint) return;
        Sprint sprint = Core.INSTANCE.getModuleManager().getModule(Sprint.class);
        if (sprint != null) sprint.canSprint = false;
        event.setSprint(false);
        stopPlayerSprint();
    }

    private boolean canRun() {
        if (minecraft.player == null || minecraft.level == null || minecraft.gameMode == null) return false;
        if (InventoryUtils.shouldDisableFeatures() || minecraft.player.isUsingItem()) return false;

        KillAura killAura = Core.INSTANCE.getModuleManager().getModule(KillAura.class);
        if (disOnKillaura.getValue() && killAura != null && killAura.isEnabled() && killAura.attackTarget != null) {
            return false;
        }

        Scaffold scaffold = Core.INSTANCE.getModuleManager().getModule(Scaffold.class);
        if (scaffold != null && scaffold.isEnabled()) return false;

        if (MovementUtils.isMoving() && noMove.getValue()) noMoveTicks = 0;
        else noMoveTicks++;
        if (inventoryOnly.getValue()) {
            if (!(minecraft.screen instanceof InventoryScreen)) return false;
        } else if (noMoveTicks <= 1) {
            return false;
        }

        if (minecraft.player.containerMenu != minecraft.player.inventoryMenu) return false;
        if (minecraft.screen instanceof AbstractContainerScreen<?> screen
                && screen.getMenu().containerId != minecraft.player.inventoryMenu.containerId) return false;
        return minecraft.screen == null || minecraft.screen instanceof InventoryScreen;
    }

    private void organizeInventory() {
        if (autoArmor.getValue()) organizeArmor();

        if (clickOffHand) {
            clickSlot(InventoryUtils.OFFHAND_SLOT_MENU, 0, ClickType.PICKUP, false);
            clickOffHand = false;
        }
        organizeOffhand();

        if (switchGoldenApple.getValue() && !offhandItems.is("Golden Apple")) {
            swapItem(hotbarSlot(goldenAppleSlot), Items.GOLDEN_APPLE);
        }
        if (switchBlock.getValue()) {
            int target = hotbarSlot(blockSlot);
            ItemStack current = mainStack(target);
            ItemStack best = bestBlock();
            if (!offhandItems.is("Block") && !best.isEmpty()
                    && (best.getCount() > current.getCount() || !isValidBlock(current))) {
                swapItem(target, best);
            }
            if (blockCount() > maxBlockSize.getValue().intValue()) throwItem(worstBlock());
        }
        if (switchSword.getValue()) {
            int target = hotbarSlot(swordSlot);
            ItemStack current = mainStack(target);
            ItemStack best = bestWeapon();
            if (!best.isEmpty() && InventoryUtils.weaponScore(best) > InventoryUtils.weaponScore(current)) {
                swapItem(target, best);
            }
        }
        if (switchPickaxe.getValue()) {
            int target = hotbarSlot(pickaxeSlot);
            ItemStack current = mainStack(target);
            ItemStack best = bestPickaxe();
            if (!best.isEmpty() && (InventoryUtils.pickaxeScore(best) > InventoryUtils.pickaxeScore(current)
                    || !(current.getItem() instanceof PickaxeItem))) swapItem(target, best);
        }
        if (switchAxe.getValue()) {
            int target = hotbarSlot(axeSlot);
            ItemStack current = mainStack(target);
            ItemStack best = bestRegularAxe();
            if (!best.isEmpty() && (InventoryUtils.axeScore(best) > InventoryUtils.axeScore(current)
                    || !(current.getItem() instanceof AxeItem))) swapItem(target, best);
        }
        if (switchRod.getValue() && !offhandItems.is("Fishing Rod")) {
            int target = hotbarSlot(rodSlot);
            if (!(mainStack(target).getItem() instanceof FishingRodItem)) swapItem(target, firstItem(Items.FISHING_ROD));
        }
        if (switchBow.getValue()) {
            int target = hotbarSlot(bowSlot);
            ItemStack current = mainStack(target);
            ItemStack best = preferredBow();
            if (!best.isEmpty() && bowPreferenceScore(best) > bowPreferenceScore(current)) swapItem(target, best);
            if (itemCount(Items.ARROW) > maxArrowSize.getValue().intValue()) throwItem(worstStack(stack -> stack.is(Items.ARROW)));
        }
        if (switchEnderPearl.getValue()) swapItem(hotbarSlot(enderPearlSlot), Items.ENDER_PEARL);
        if (switchWaterBucket.getValue()) swapItem(hotbarSlot(waterBucketSlot), Items.WATER_BUCKET);
        if (switchFireball.getValue()) swapItem(hotbarSlot(fireballSlot), Items.FIRE_CHARGE);

        if (keepProjectile.getValue()) {
            if (projectileCount() > maxProjectileSize.getValue().intValue()) throwItem(worstProjectile());
            if (switchProjectile.getValue() && !offhandItems.is("Projectile")) {
                int target = hotbarSlot(projectileSlot);
                if (itemCount(Items.EGG) > 0) swapItem(target, Items.EGG);
                else if (itemCount(Items.SNOWBALL) > 0) swapItem(target, Items.SNOWBALL);
            }
        }

        if (throwItems.getValue()) throwUselessItems();
    }

    private void organizeArmor() {
        for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            ItemStack equipped = minecraft.player.getInventory().getItem(armorInventorySlot(slot));
            ItemStack best = bestArmor(slot);
            if (!equipped.isEmpty() && !best.isEmpty()
                    && InventoryUtils.armorScore(best) > InventoryUtils.armorScore(equipped)) {
                clickSlot(armorMenuSlot(slot), 1, ClickType.THROW, true);
            }
        }

        for (int slot = 0; slot < MAIN_INVENTORY_SIZE; slot++) {
            ItemStack stack = mainStack(slot);
            EquipmentSlot equipmentSlot = InventoryUtils.armorSlot(stack);
            if (equipmentSlot == null) continue;
            ItemStack best = bestArmor(equipmentSlot);
            ItemStack equipped = minecraft.player.getInventory().getItem(armorInventorySlot(equipmentSlot));
            if (stack == best && InventoryUtils.armorScore(stack) > InventoryUtils.armorScore(equipped)) {
                clickSlot(toMenuSlot(slot), 0, ClickType.QUICK_MOVE, false);
            }
        }
    }

    private void organizeOffhand() {
        ItemStack offhand = minecraft.player.getOffhandItem();
        if (offhandItems.is("Golden Apple")) {
            int slot = firstItemSlot(Items.GOLDEN_APPLE);
            if (slot == -1) return;
            ItemStack source = mainStack(slot);
            if (offhand.is(Items.GOLDEN_APPLE) && offhand.getCount() + source.getCount() <= source.getMaxStackSize()) {
                if (clickSlot(toMenuSlot(slot), 0, ClickType.PICKUP, false)) clickOffHand = true;
            } else if (!offhand.is(Items.GOLDEN_APPLE)) {
                swapOffhand(slot);
            }
        } else if (offhandItems.is("Projectile")) {
            ItemStack best = bestProjectile();
            if (!best.isEmpty() && (!isProjectile(offhand) || offhand.getCount() < best.getCount())) {
                swapOffhand(findMainSlot(best));
            }
        } else if (offhandItems.is("Fishing Rod")) {
            int slot = firstItemSlot(Items.FISHING_ROD);
            if (slot != -1 && !offhand.is(Items.FISHING_ROD)) swapOffhand(slot);
        } else if (offhandItems.is("Block")) {
            ItemStack best = bestBlock();
            if (!best.isEmpty() && (!isValidBlock(offhand) || offhand.getCount() < best.getCount())) {
                swapOffhand(findMainSlot(best));
            }
        }
    }

    private boolean needsInventoryOrganizing() {
        if (minecraft.player == null) return false;
        if (autoArmor.getValue() && armorNeedsWork()) return true;
        if (clickOffHand || offhandNeedsWork()) return true;

        if (switchGoldenApple.getValue() && !offhandItems.is("Golden Apple")
                && targetNeedsItem(hotbarSlot(goldenAppleSlot), Items.GOLDEN_APPLE)) return true;
        if (switchBlock.getValue()) {
            ItemStack current = mainStack(hotbarSlot(blockSlot));
            ItemStack best = bestBlock();
            if (!offhandItems.is("Block") && !best.isEmpty()
                    && (best.getCount() > current.getCount() || !isValidBlock(current))) return true;
            if (blockCount() > maxBlockSize.getValue().intValue()) return true;
        }
        if (switchSword.getValue()) {
            ItemStack best = bestWeapon();
            if (!best.isEmpty() && InventoryUtils.weaponScore(best)
                    > InventoryUtils.weaponScore(mainStack(hotbarSlot(swordSlot)))) return true;
        }
        if (switchPickaxe.getValue()) {
            ItemStack current = mainStack(hotbarSlot(pickaxeSlot));
            ItemStack best = bestPickaxe();
            if (!best.isEmpty() && (InventoryUtils.pickaxeScore(best) > InventoryUtils.pickaxeScore(current)
                    || !(current.getItem() instanceof PickaxeItem))) return true;
        }
        if (switchAxe.getValue()) {
            ItemStack current = mainStack(hotbarSlot(axeSlot));
            ItemStack best = bestRegularAxe();
            if (!best.isEmpty() && (InventoryUtils.axeScore(best) > InventoryUtils.axeScore(current)
                    || !(current.getItem() instanceof AxeItem))) return true;
        }
        if (switchBow.getValue()) {
            ItemStack current = mainStack(hotbarSlot(bowSlot));
            ItemStack best = preferredBow();
            if (!best.isEmpty() && bowPreferenceScore(best) > bowPreferenceScore(current)) return true;
            if (itemCount(Items.ARROW) > maxArrowSize.getValue().intValue()) return true;
        }
        if (switchWaterBucket.getValue() && targetNeedsItem(hotbarSlot(waterBucketSlot), Items.WATER_BUCKET)) return true;
        if (switchEnderPearl.getValue() && targetNeedsItem(hotbarSlot(enderPearlSlot), Items.ENDER_PEARL)) return true;
        if (switchFireball.getValue() && targetNeedsItem(hotbarSlot(fireballSlot), Items.FIRE_CHARGE)) return true;
        if (keepProjectile.getValue()) {
            if (projectileCount() > maxProjectileSize.getValue().intValue()) return true;
            if (switchProjectile.getValue()) {
                ItemStack current = mainStack(hotbarSlot(projectileSlot));
                if (!bestProjectile().isEmpty() && !isProjectile(current)) return true;
            }
        }
        if (switchRod.getValue() && !offhandItems.is("Fishing Rod")
                && targetNeedsItem(hotbarSlot(rodSlot), Items.FISHING_ROD)) return true;
        if (throwItems.getValue() && hasUselessItem()) return true;
        return false;
    }

    private boolean isInventoryOrganizingComplete() {
        return !hasUselessItem() && !armorNeedsWork() && !clickOffHand && !offhandNeedsWork();
    }

    private boolean armorNeedsWork() {
        if (!autoArmor.getValue()) return false;
        for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            ItemStack best = bestArmor(slot);
            ItemStack equipped = minecraft.player.getInventory().getItem(armorInventorySlot(slot));
            if (!best.isEmpty() && InventoryUtils.armorScore(best) > InventoryUtils.armorScore(equipped)) return true;
        }
        return false;
    }

    private boolean offhandNeedsWork() {
        ItemStack offhand = minecraft.player.getOffhandItem();
        if (offhandItems.is("Golden Apple")) {
            int slot = firstItemSlot(Items.GOLDEN_APPLE);
            if (slot == -1) return false;
            return !offhand.is(Items.GOLDEN_APPLE)
                    || offhand.getCount() + mainStack(slot).getCount() <= offhand.getMaxStackSize();
        }
        if (offhandItems.is("Projectile")) {
            ItemStack best = bestProjectile();
            return !best.isEmpty() && (!isProjectile(offhand) || offhand.getCount() < best.getCount());
        }
        if (offhandItems.is("Fishing Rod")) return firstItemSlot(Items.FISHING_ROD) != -1 && !offhand.is(Items.FISHING_ROD);
        if (offhandItems.is("Block")) {
            ItemStack best = bestBlock();
            return !best.isEmpty() && (!isValidBlock(offhand) || offhand.getCount() < best.getCount());
        }
        return false;
    }

    private boolean hasUselessItem() {
        if (!throwItems.getValue()) return false;
        for (int slot = 0; slot < MAIN_INVENTORY_SIZE; slot++) {
            ItemStack stack = mainStack(slot);
            if (!stack.isEmpty() && !isItemUseful(stack)) return true;
        }
        return false;
    }

    private void throwUselessItems() {
        List<Integer> slots = new ArrayList<>(MAIN_INVENTORY_SIZE);
        for (int slot = 0; slot < MAIN_INVENTORY_SIZE; slot++) slots.add(slot);
        Collections.shuffle(slots);
        for (int slot : slots) {
            ItemStack stack = mainStack(slot);
            if (!stack.isEmpty() && !isItemUseful(stack)) throwItem(stack);
        }
    }

    private boolean isItemUseful(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (isGodItem(stack) || stack.getHoverName().getString().contains("\u70b9\u51fb\u4f7f\u7528")) return true;

        EquipmentSlot armorSlot = InventoryUtils.armorSlot(stack);
        if (armorSlot != null) {
            ItemStack equipped = minecraft.player.getInventory().getItem(armorInventorySlot(armorSlot));
            if (InventoryUtils.armorScore(equipped) >= InventoryUtils.armorScore(stack)) return false;
            return InventoryUtils.sameBestRank(stack, bestArmor(armorSlot), InventoryUtils::armorScore);
        }
        if (stack.getItem() instanceof SwordItem) return stack == bestSword();
        if (stack.getItem() instanceof PickaxeItem) return stack == bestPickaxe();
        if (stack.getItem() instanceof AxeItem && !isSharpnessAxe(stack)) return stack == bestRegularAxe();
        if (stack.getItem() instanceof ShovelItem) return stack == bestShovel();
        if (stack.getItem() instanceof CrossbowItem) return stack == bestCrossbow();
        if (stack.getItem() instanceof BowItem && isPunchBow(stack)) return stack == bestPunchBow();
        if (stack.getItem() instanceof BowItem && isPowerBow(stack)) return stack == bestPowerBow();
        if (stack.getItem() instanceof BowItem && itemCount(Items.BOW) > 1) return false;
        if (stack.is(Items.WATER_BUCKET) && itemCount(Items.WATER_BUCKET) > waterBucketCount.getValue().intValue()) return false;
        if (stack.is(Items.LAVA_BUCKET) && itemCount(Items.LAVA_BUCKET) > lavaBucketCount.getValue().intValue()) return false;
        if (stack.getItem() instanceof FishingRodItem && itemCount(Items.FISHING_ROD) > 1) return false;
        if (isProjectile(stack) && !keepProjectile.getValue()) return false;
        if (stack.getItem() instanceof ItemNameBlockItem) return false;
        return isCommonItemUseful(stack);
    }

    private void swapOffhand(int inventorySlot) {
        if (inventorySlot != -1) clickSlot(toMenuSlot(inventorySlot), InventoryUtils.OFFHAND_SLOT_INVENTORY, ClickType.SWAP, false);
    }

    private void throwItem(ItemStack stack) {
        int slot = findMainSlot(stack);
        if (slot != -1) clickSlot(toMenuSlot(slot), 1, ClickType.THROW, false);
    }

    private void swapItem(int targetSlot, ItemStack bestItem) {
        if (bestItem == null || bestItem.isEmpty()) return;
        ItemStack current = mainStack(targetSlot);
        int source = findMainSlot(bestItem);
        if (source != -1 && source != targetSlot && isMoveableSlot(current)) {
            clickSlot(toMenuSlot(source), targetSlot, ClickType.SWAP, false);
        }
    }

    private void swapItem(int targetSlot, Item item) {
        int source = firstItemSlot(item);
        if (source == -1 || source == targetSlot || !isMoveableSlot(mainStack(targetSlot))) return;
        ItemStack current = mainStack(targetSlot);
        ItemStack sourceStack = mainStack(source);
        if (!current.is(item) || current.getCount() < sourceStack.getCount()) {
            clickSlot(toMenuSlot(source), targetSlot, ClickType.SWAP, false);
        }
    }

    private boolean clickSlot(int slot, int button, ClickType type, boolean armorDrop) {
        stopSprint = true;
        suppressSprint();
        if (!timer.hasTimeElapsed(delay.getValue().longValue())) return false;

        if (type == ClickType.THROW && fastdrop.getValue() && !armorDrop) {
            minecraft.gameMode.handleInventoryMouseClick(
                    minecraft.player.inventoryMenu.containerId, slot, button, type, minecraft.player);
            return true;
        }

        if (!inventoryOnly.getValue() && wasSprinting()) return false;
        if (armorDrop) handleInventoryMouseClickNoEvent(slot, button, type);
        else minecraft.gameMode.handleInventoryMouseClick(
                minecraft.player.inventoryMenu.containerId, slot, button, type, minecraft.player);
        timer.reset();
        if (!inventoryOnly.getValue()) sendClosePacket();
        return true;
    }

    private void handleInventoryMouseClickNoEvent(int slotId, int button, ClickType clickType) {
        AbstractContainerMenu menu = minecraft.player.containerMenu;
        if (menu.containerId != minecraft.player.inventoryMenu.containerId) return;

        List<ItemStack> previous = new ArrayList<>(menu.slots.size());
        for (Slot slot : menu.slots) previous.add(slot.getItem().copy());
        menu.clicked(slotId, button, clickType, minecraft.player);

        Int2ObjectMap<ItemStack> changed = new Int2ObjectOpenHashMap<>();
        for (int index = 0; index < menu.slots.size(); index++) {
            ItemStack current = menu.slots.get(index).getItem();
            if (!ItemStack.matches(previous.get(index), current)) changed.put(index, current.copy());
        }
        PacketUtils.sendPacketNoEvent(new ServerboundContainerClickPacket(
                menu.containerId,
                menu.getStateId(),
                slotId,
                button,
                clickType,
                menu.getCarried().copy(),
                changed
        ));
    }

    private void finishSession() {
        stopSprint = !packetQueue.isEmpty();
        suppressSprint();
        if (wasSprinting()) return;
        while (fastdrop.getValue() && !packetQueue.isEmpty()) PacketUtils.sendPacketNoEvent(packetQueue.removeFirst());
        if (!fastdrop.getValue()) packetQueue.clear();
        if (!packetQueue.isEmpty()) return;
        sendClosePacket();
        inventoryOpen = false;
        clickOffHand = false;
    }

    private void pauseSession() {
        if (inventoryOpen && packetQueue.isEmpty()) {
            sendClosePacket();
            inventoryOpen = false;
            clickOffHand = false;
        }
        if (!packetQueue.isEmpty()) {
            stopSprint = true;
            suppressSprint();
        }
    }

    private void sendClosePacket() {
        if (minecraft.player != null && minecraft.getConnection() != null) {
            PacketUtils.sendPacketNoEvent(new ServerboundContainerClosePacket(minecraft.player.inventoryMenu.containerId));
        }
    }

    private void suppressSprint() {
        if (!stopSprint || !Core.INSTANCE.isInitialized()) return;
        Sprint sprint = Core.INSTANCE.getModuleManager().getModule(Sprint.class);
        if (sprint != null) sprint.canSprint = false;
        stopPlayerSprint();
    }

    private void releaseSprint() {
        stopSprint = false;
        if (!Core.INSTANCE.isInitialized()) return;
        Sprint sprint = Core.INSTANCE.getModuleManager().getModule(Sprint.class);
        if (sprint != null && sprint.isEnabled()) sprint.canSprint = true;
    }

    private void stopPlayerSprint() {
        if (minecraft.options != null) minecraft.options.keySprint.setDown(false);
        if (minecraft.player != null) minecraft.player.setSprinting(false);
    }

    private boolean wasSprinting() {
        return minecraft.player != null && ((LocalPlayerAccessor) minecraft.player).isWasSprinting();
    }

    private void resetState(boolean close) {
        if (close && minecraft.player != null) {
            while (!packetQueue.isEmpty()) PacketUtils.sendPacketNoEvent(packetQueue.removeFirst());
            if (inventoryOpen) sendClosePacket();
        }
        packetQueue.clear();
        inventoryOpen = false;
        stopSprint = false;
        clickOffHand = false;
        noMoveTicks = 0;
        releaseSprint();
    }

    private ItemStack preferredBow() {
        ItemStack preferred = switch (preferBow.getValue()) {
            case "Crossbow" -> bestCrossbow();
            case "Power Bow" -> bestPowerBow();
            default -> bestPunchBow();
        };
        if (!preferred.isEmpty()) return preferred;
        ItemStack crossbow = bestCrossbow();
        if (!crossbow.isEmpty()) return crossbow;
        ItemStack power = bestPowerBow();
        return power.isEmpty() ? bestPunchBow() : power;
    }

    private double bowPreferenceScore(ItemStack stack) {
        if (preferBow.is("Crossbow")) return InventoryUtils.crossbowScore(stack);
        if (!(stack.getItem() instanceof BowItem)) return -1.0;
        int primary = InventoryUtils.enchantmentLevel(stack,
                preferBow.is("Power Bow") ? Enchantments.POWER_ARROWS : Enchantments.PUNCH_ARROWS);
        return primary * 1000.0 + InventoryUtils.bowScore(stack);
    }

    private ItemStack bestPowerBow() {
        return bestMainStack(stack -> stack.getItem() instanceof BowItem,
                stack -> InventoryUtils.enchantmentLevel(stack, Enchantments.POWER_ARROWS) * 1000.0
                        + InventoryUtils.bowScore(stack));
    }

    private ItemStack bestPunchBow() {
        return bestMainStack(stack -> stack.getItem() instanceof BowItem,
                stack -> InventoryUtils.enchantmentLevel(stack, Enchantments.PUNCH_ARROWS) * 1000.0
                        + InventoryUtils.bowScore(stack));
    }

    private boolean isPowerBow(ItemStack stack) {
        return InventoryUtils.enchantmentLevel(stack, Enchantments.POWER_ARROWS) > 0;
    }

    private boolean isPunchBow(ItemStack stack) {
        return InventoryUtils.enchantmentLevel(stack, Enchantments.PUNCH_ARROWS) > 0;
    }

    private ItemStack bestWeapon() {
        ItemStack sword = bestSword();
        ItemStack sharpAxe = bestMainStack(
                stack -> stack.getItem() instanceof AxeItem && isSharpnessAxe(stack) && !isGodItem(stack),
                InventoryUtils::weaponScore);
        return InventoryUtils.weaponScore(sharpAxe) > InventoryUtils.weaponScore(sword) ? sharpAxe : sword;
    }

    private ItemStack bestSword() {
        return bestMainStack(stack -> stack.getItem() instanceof SwordItem, InventoryUtils::weaponScore);
    }

    private ItemStack bestPickaxe() {
        return bestMainStack(stack -> stack.getItem() instanceof PickaxeItem, InventoryUtils::pickaxeScore);
    }

    private ItemStack bestShovel() {
        return bestMainStack(stack -> stack.getItem() instanceof ShovelItem, InventoryUtils::shovelScore);
    }

    private ItemStack bestCrossbow() {
        return bestMainStack(stack -> stack.getItem() instanceof CrossbowItem, InventoryUtils::crossbowScore);
    }

    private ItemStack bestArmor(EquipmentSlot equipmentSlot) {
        ItemStack best = ItemStack.EMPTY;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int slot = 0; slot < InventoryUtils.OFFHAND_SLOT_INVENTORY; slot++) {
            ItemStack stack = minecraft.player.getInventory().getItem(slot);
            if (InventoryUtils.armorSlot(stack) != equipmentSlot) continue;
            double score = InventoryUtils.armorScore(stack);
            if (score > bestScore) {
                best = stack;
                bestScore = score;
            }
        }
        return best;
    }

    private ItemStack bestRegularAxe() {
        return bestMainStack(stack -> stack.getItem() instanceof AxeItem && !isSharpnessAxe(stack), InventoryUtils::axeScore);
    }

    private boolean isSharpnessAxe(ItemStack stack) {
        return stack.getItem() instanceof AxeItem
                && InventoryUtils.enchantmentLevel(stack, Enchantments.SHARPNESS) > 0;
    }

    private boolean isGodItem(ItemStack stack) {
        return (stack.is(Items.GOLDEN_AXE)
                && InventoryUtils.enchantmentLevel(stack, Enchantments.SHARPNESS) > 100)
                || stack.is(Items.SLIME_BALL) && InventoryUtils.enchantmentLevel(stack, Enchantments.KNOCKBACK) > 1
                || stack.is(Items.TOTEM_OF_UNDYING)
                || stack.is(Items.END_CRYSTAL);
    }

    private ItemStack bestBlock() {
        return bestMainStack(this::isValidBlock, ItemStack::getCount);
    }

    private ItemStack worstBlock() {
        return worstStack(this::isValidBlock);
    }

    private boolean isValidBlock(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BlockItem)
                || stack.getItem() instanceof ItemNameBlockItem || !isMoveableSlot(stack)) return false;
        Block block = ((BlockItem) stack.getItem()).getBlock();
        return !(block instanceof FlowerBlock) && !(block instanceof BushBlock)
                && !(block instanceof FungusBlock) && !(block instanceof CropBlock)
                && !(block instanceof SlabBlock) && !BLOCK_BLACKLIST.contains(block);
    }

    private boolean isCommonItemUseful(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof BlockItem blockItem) {
            return blockItem.getBlock() != Blocks.ENCHANTING_TABLE && blockItem.getBlock() != Blocks.COBWEB;
        }
        if (item instanceof BookItem || item instanceof ExperienceBottleItem || item instanceof FireworkRocketItem) {
            return false;
        }
        return item != Items.WHEAT_SEEDS && item != Items.BEETROOT_SEEDS
                && item != Items.MELON_SEEDS && item != Items.PUMPKIN_SEEDS
                && item != Items.FLINT_AND_STEEL;
    }

    private int blockCount() {
        int count = 0;
        for (int slot = 0; slot < MAIN_INVENTORY_SIZE; slot++) {
            ItemStack stack = mainStack(slot);
            if (isValidBlock(stack)) count += stack.getCount();
        }
        return count;
    }

    private ItemStack bestProjectile() {
        return bestMainStack(this::isProjectile, ItemStack::getCount);
    }

    private ItemStack worstProjectile() {
        return worstStack(this::isProjectile);
    }

    private int projectileCount() {
        return itemCount(Items.EGG) + itemCount(Items.SNOWBALL);
    }

    private boolean isProjectile(ItemStack stack) {
        return stack != null && (stack.is(Items.EGG) || stack.is(Items.SNOWBALL));
    }

    private ItemStack bestMainStack(Predicate<ItemStack> predicate, ToDoubleFunction<ItemStack> score) {
        ItemStack best = ItemStack.EMPTY;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int slot = 0; slot < MAIN_INVENTORY_SIZE; slot++) {
            ItemStack stack = mainStack(slot);
            if (stack.isEmpty() || !predicate.test(stack)) continue;
            double current = score.applyAsDouble(stack);
            if (current > bestScore) {
                best = stack;
                bestScore = current;
            }
        }
        return best;
    }

    private ItemStack worstStack(Predicate<ItemStack> predicate) {
        return bestMainStack(predicate, stack -> -stack.getCount());
    }

    private ItemStack firstItem(Item item) {
        int slot = firstItemSlot(item);
        return slot == -1 ? ItemStack.EMPTY : mainStack(slot);
    }

    private int firstItemSlot(Item item) {
        for (int slot = 0; slot < MAIN_INVENTORY_SIZE; slot++) if (mainStack(slot).is(item)) return slot;
        return -1;
    }

    private int findMainSlot(ItemStack target) {
        if (target == null || target.isEmpty()) return -1;
        for (int slot = 0; slot < MAIN_INVENTORY_SIZE; slot++) if (mainStack(slot) == target) return slot;
        return -1;
    }

    private int itemCount(Item item) {
        int count = 0;
        for (int slot = 0; slot < MAIN_INVENTORY_SIZE; slot++) {
            ItemStack stack = mainStack(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private boolean targetNeedsItem(int target, Item item) {
        return firstItemSlot(item) != -1 && !mainStack(target).is(item);
    }

    private boolean isMoveableSlot(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return true;
        if (stack.getItem() instanceof PlayerHeadItem) return false;
        String name = stack.getHoverName().getString();
        return !name.contains("Click") && !name.contains("Right") && !name.contains("Teleport")
                && !name.contains("点击") && !name.contains("使用")
                && !name.contains("传送") && !name.contains("再来");
    }

    private ItemStack mainStack(int slot) {
        return minecraft.player.getInventory().getItem(slot);
    }

    private int hotbarSlot(NumberSetting setting) {
        return setting.getValue().intValue() - 1;
    }

    private int toMenuSlot(int inventorySlot) {
        return inventorySlot < 9 ? inventorySlot + 36 : inventorySlot;
    }

    private int armorInventorySlot(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> InventoryUtils.HELMET_SLOT_INVENTORY;
            case CHEST -> InventoryUtils.CHEST_SLOT_INVENTORY;
            case LEGS -> InventoryUtils.LEGS_SLOT_INVENTORY;
            case FEET -> InventoryUtils.FEET_SLOT_INVENTORY;
            default -> -1;
        };
    }

    private int armorMenuSlot(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> InventoryUtils.HELMET_SLOT_MENU;
            case CHEST -> InventoryUtils.CHEST_SLOT_MENU;
            case LEGS -> InventoryUtils.LEGS_SLOT_MENU;
            case FEET -> InventoryUtils.FEET_SLOT_MENU;
            default -> -1;
        };
    }
}
