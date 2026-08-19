package cc.nimbus.features.module.modules.player;

import cc.nimbus.core.Core;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.component.Priority;
import cc.nimbus.event.impl.game.EventTick;
import cc.nimbus.event.impl.game.EventWorldLoad;
import cc.nimbus.event.impl.network.EventPacketSend;
import cc.nimbus.event.impl.player.EventMoveInput;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.BooleanSetting;
import cc.nimbus.features.module.api.setting.impl.ModeSetting;
import cc.nimbus.features.module.api.setting.impl.NumberSetting;
import cc.nimbus.features.module.api.setting.impl.modes.Modes;
import cc.nimbus.features.module.modules.combat.KillAura;
import cc.nimbus.features.module.modules.movement.Scaffold;
import cc.nimbus.mixin.accessor.player.LocalPlayerAccessor;
import cc.nimbus.utils.entity.player.InventoryUtils;
import cc.nimbus.utils.network.PacketUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.BlastFurnaceMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

@ModuleInfo(name = "InventoryManager", description = "Sorts, equips and cleans the player inventory.", category = ModuleCategory.Player)
public final class InventoryManager extends BasicModule {
    private static final String SORT_TYPE_TRANSLATION_SCOPE =
            "nimbus.module.inventory_manager.setting.sort_type";
    private static final String[] SORT_TYPES = {
            "None", "Sword", "Bow", "Crossbow", "Pickaxe", "Axe", "Shovel", "Shears",
            "Food", "Block", "Water Bucket", "Lava Bucket", "Golden Apple", "Pearl", "Potion",
            "Splash Potion", "Projectile", "Fishing Rod", "TNT", "Totem", "Shield", "Fire Charge",
            "Flint And Steel", "God Axe", "Knockback Ball"
    };
    private static final String[] DEFAULT_SLOT_TYPES = {
            "Sword", "Block", "Golden Apple", "Pearl", "Projectile",
            "Water Bucket", "Bow", "Pickaxe", "Axe"
    };
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    public final ModeSetting clickMode = new ModeSetting("Click Mode", new String[]{"Normal", "Packet"}, "Normal");
    public final BooleanSetting sortInventory = new BooleanSetting("Sort Inventory", false);
    public final ModeSetting[] slotTypes = new ModeSetting[10];
    public final NumberSetting sortDelay = new NumberSetting("Sort Delay", 0, 0, 500, 1, sortInventory::getValue);
    public final BooleanSetting throwUseless = new BooleanSetting("Throw Useless Items", false);
    public final NumberSetting throwDelay = new NumberSetting("Throw Delay", 0, 0, 500, 1, throwUseless::getValue);
    public final NumberSetting maxBlockCount = new NumberSetting("Max Block Count", 512, 0, 512, 1, throwUseless::getValue);
    public final BooleanSetting autoCraft = new BooleanSetting("Auto Craft", false);
    public final NumberSetting autoCraftDelay = new NumberSetting("Auto Craft Delay", 0, 0, 500, 1, autoCraft::getValue);
    public final BooleanSetting autoArmor = new BooleanSetting("Auto Armor", false);
    public final NumberSetting autoArmorDelay = new NumberSetting("Auto Armor Delay", 0, 0, 500, 1, autoArmor::getValue);
    public final BooleanSetting randomSlot = new BooleanSetting("Random Slot", false);
    public final BooleanSetting openInventory = new BooleanSetting("Open Inventory", false);
    public final BooleanSetting spectatorCheck = new BooleanSetting("Spectator Check", false);

    private final Minecraft minecraft = Minecraft.getInstance();
    private final List<Integer> slots = new ArrayList<>();
    private long lastSort;
    private long lastThrow;
    private long lastCraft;
    private long lastArmor;
    private boolean randomized;
    private boolean serverInventoryOpened;
    private boolean stopSprint;
    private boolean packetClick;

    public InventoryManager() {
        for (int i = 0; i < 9; i++) {
            slotTypes[i] = new ModeSetting(
                    "Slot " + (i + 1),
                    new Modes(SORT_TYPES),
                    DEFAULT_SLOT_TYPES[i],
                    sortInventory::getValue
            );
            slotTypes[i].bindOptionTranslationScope(SORT_TYPE_TRANSLATION_SCOPE);
            addSetting(slotTypes[i]);
        }
        slotTypes[9] = new ModeSetting("Offhand", new Modes(SORT_TYPES), "None", sortInventory::getValue);
        slotTypes[9].bindOptionTranslationScope(SORT_TYPE_TRANSLATION_SCOPE);
        addSetting(slotTypes[9]);
    }

    @Override protected void onEnable() { resetState(); }
    @Override protected void onDisable() { closeSpoofedInventory(); resetState(); }
    @EventTarget private void onWorldLoad(EventWorldLoad event) { resetState(); }

    @EventTarget
    private void onTick(EventTick event) {
        if (event.getPhase() == EventTick.Phase.POST) {
            stopSprint = false;
            return;
        }
        if (!canRun()) { closeSpoofedInventory(); return; }
        if (isBusy()) { closeSpoofedInventory(); stopSprint = false; return; }
        if (!canManageScreen()) { closeSpoofedInventory(); randomized = false; return; }

        rebuildSlots();
        boolean needsAction = autoCraft.getValue() && canAutoCraftPlanks()
                || throwUseless.getValue() && hasUselessItem()
                || autoArmor.getValue() && undressed()
                || sortInventory.getValue() && unsorted();
        if (!needsAction) { closeSpoofedInventory(); return; }

        stopSprint = true;
        if (minecraft.player.isSprinting()) { stopPlayerSprint(); return; }
        openSpoofedInventory();
        if (autoCraft.getValue() && autoCraftPlanks()) return;
        if (throwUseless.getValue() && autoThrowUseless()) return;
        if (autoArmor.getValue() && autoWear()) return;
        if (sortInventory.getValue()) autoSort();
    }

    @EventTarget(priority = Priority.Lowest)
    private void onMoveInput(EventMoveInput event) {
        if (!stopSprint) return;
        event.setSprint(false);
        stopPlayerSprint();
    }

    @EventTarget(priority = Priority.Highest)
    private void onPacketSend(EventPacketSend event) {
        if (!packetClick || !(event.getPacket() instanceof ServerboundContainerClickPacket packet)) return;
        event.cancelEvent();
        PacketUtils.sendPacketNoEvent(packet);
    }

    public static double handAttributeValueWithStack(ItemStack stack, Holder<Attribute> attribute, InteractionHand hand) {
        return attributeValueWithStack(stack, attribute,
                hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
    }

    public static double attributeValueWithStack(ItemStack stack, Holder<Attribute> attribute, EquipmentSlot slot) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || stack == null || stack.isEmpty()) return 0.0;
        double base = minecraft.player.getAttributeBaseValue(attribute);
        double[] modifiers = {0.0, 0.0, 0.0};
        stack.forEachModifier(slot, (candidate, modifier) -> {
            if (!candidate.equals(attribute)) return;
            switch (modifier.operation()) {
                case ADD_VALUE -> modifiers[0] += modifier.amount();
                case ADD_MULTIPLIED_BASE -> modifiers[1] += modifier.amount();
                case ADD_MULTIPLIED_TOTAL -> modifiers[2] += modifier.amount();
            }
        });
        return (base + modifiers[0] + base * modifiers[1]) * (1.0 + modifiers[2]);
    }

    private boolean canRun() {
        return minecraft.player != null && minecraft.level != null && minecraft.gameMode != null
                && !minecraft.player.isUsingItem() && !InventoryUtils.shouldDisableFeatures()
                && (!spectatorCheck.getValue() || !minecraft.player.isSpectator());
    }

    private boolean isBusy() {
        Scaffold scaffold = Core.INSTANCE.getModuleManager().getModule(Scaffold.class);
        KillAura aura = Core.INSTANCE.getModuleManager().getModule(KillAura.class);
        return scaffold != null && scaffold.isEnabled()
                || aura != null && aura.getAttackTarget() != null
                || isContainerMenu(minecraft.player.containerMenu);
    }

    private boolean canManageScreen() {
        return !openInventory.getValue() && minecraft.screen == null || minecraft.screen instanceof InventoryScreen;
    }

    private void rebuildSlots() {
        if (!randomized || !randomSlot.getValue()) {
            slots.clear();
            int size = minecraft.player.getInventory().getContainerSize();
            for (int slot = 0; slot < size; slot++) slots.add(slot);
        }
        if (randomSlot.getValue() && !randomized) { Collections.shuffle(slots); randomized = true; }
    }

    private boolean autoCraftPlanks() {
        if (!(minecraft.player.containerMenu instanceof InventoryMenu menu)
                || !menu.getCarried().isEmpty() || !elapsed(lastCraft, autoCraftDelay)) return false;
        ItemStack result = menu.getSlot(0).getItem();
        if (managedWoodGrid(menu) && result.is(ItemTags.PLANKS) && hasSpaceFor(result)) {
            click(menu, 0, 0, ClickType.QUICK_MOVE);
            lastCraft = now();
            return true;
        }
        if (!craftingGridEmpty(menu)) return false;
        int woodSlot = findFirstMatching(stack -> stack.is(ItemTags.LOGS)
                || stack.is(Items.BAMBOO_BLOCK) || stack.is(Items.STRIPPED_BAMBOO_BLOCK));
        if (woodSlot == -1 || !hasSpaceForAnyPlanks()) return false;
        click(menu, toContainerSlot(woodSlot), 0, ClickType.PICKUP);
        click(menu, 1, 0, ClickType.PICKUP);
        lastCraft = now();
        return true;
    }

    private boolean canAutoCraftPlanks() {
        if (!(minecraft.player.containerMenu instanceof InventoryMenu menu) || !menu.getCarried().isEmpty()) return false;
        ItemStack result = menu.getSlot(0).getItem();
        if (managedWoodGrid(menu) && result.is(ItemTags.PLANKS)) return hasSpaceFor(result);
        return craftingGridEmpty(menu) && findFirstMatching(stack -> stack.is(ItemTags.LOGS)
                || stack.is(Items.BAMBOO_BLOCK) || stack.is(Items.STRIPPED_BAMBOO_BLOCK)) != -1
                && hasSpaceForAnyPlanks();
    }

    private boolean craftingGridEmpty(InventoryMenu menu) {
        for (int slot = 1; slot <= 4; slot++) if (!menu.getSlot(slot).getItem().isEmpty()) return false;
        return true;
    }

    private boolean managedWoodGrid(InventoryMenu menu) {
        ItemStack input = menu.getSlot(1).getItem();
        if (!(input.is(ItemTags.LOGS) || input.is(Items.BAMBOO_BLOCK) || input.is(Items.STRIPPED_BAMBOO_BLOCK))) return false;
        for (int slot = 2; slot <= 4; slot++) if (!menu.getSlot(slot).getItem().isEmpty()) return false;
        return true;
    }

    private boolean hasSpaceForAnyPlanks() {
        for (int slot = 0; slot < minecraft.player.getInventory().getContainerSize(); slot++) {
            if (isEquipmentSlot(slot)) continue;
            ItemStack stack = minecraft.player.getInventory().getItem(slot);
            if (stack.isEmpty() || stack.is(ItemTags.PLANKS) && stack.getCount() < stack.getMaxStackSize()) return true;
        }
        return false;
    }

    private boolean hasSpaceFor(ItemStack result) {
        for (int slot = 0; slot < minecraft.player.getInventory().getContainerSize(); slot++) {
            if (isEquipmentSlot(slot)) continue;
            ItemStack stack = minecraft.player.getInventory().getItem(slot);
            if (stack.isEmpty() || ItemStack.isSameItemSameComponents(stack, result)
                    && stack.getCount() < stack.getMaxStackSize()) return true;
        }
        return false;
    }

    private boolean autoThrowUseless() {
        if (!elapsed(lastThrow, throwDelay)) return false;
        for (int slot : slots) {
            if (isEquipmentSlot(slot)) continue;
            ItemStack stack = minecraft.player.getInventory().getItem(slot);
            if (stack.isEmpty() || !shouldThrow(slot, stack)) continue;
            click(minecraft.player.containerMenu, toContainerSlot(slot), 1, ClickType.THROW);
            lastThrow = now();
            return true;
        }
        return false;
    }

    private boolean hasUselessItem() {
        for (int slot : slots) {
            if (isEquipmentSlot(slot)) continue;
            ItemStack stack = minecraft.player.getInventory().getItem(slot);
            if (!stack.isEmpty() && shouldThrow(slot, stack)) return true;
        }
        return false;
    }

    private boolean shouldThrow(int slot, ItemStack stack) {
        if (stack == InventoryUtils.getGodAxe()) return false;
        if (InventoryUtils.notUsefulItem(stack)) return true;
        if (isCountedBlock(stack) && slot == extraBlockDropSlot()) return true;
        if ((stack.is(Items.WATER_BUCKET) || stack.is(Items.LAVA_BUCKET)) && hasDuplicate(stack.getItem(), slot)) return true;
        if (stack.getItem() instanceof BowItem)
            return duplicateBest(slot, stack, "Bow", InventoryUtils.getBestBow(), InventoryUtils::bowScore);
        if (stack.getItem() instanceof CrossbowItem)
            return duplicateBest(slot, stack, "Crossbow", InventoryUtils.getBestCrossbow(), InventoryUtils::crossbowScore);
        if (stack.is(Items.SHEARS))
            return duplicateBest(slot, stack, "Shears", InventoryUtils.getBestShears(), InventoryUtils::shearsScore);
        if (InventoryUtils.armorSlot(stack) != null) return duplicateArmor(slot, stack);
        if (InventoryUtils.isWeapon(stack)) return duplicateWeaponOrTool(slot, stack);
        return false;
    }

    private boolean duplicateWeaponOrTool(int slot, ItemStack stack) {
        if (sameRank(stack, InventoryUtils.getBestWeapon(), InventoryUtils::weaponScore))
            return !preferredOrExact(slot, stack, "Sword", InventoryUtils.getBestWeapon(), InventoryUtils::weaponScore);
        if (stack.is(ItemTags.AXES) && sameRank(stack, InventoryUtils.getBestAxe(true), InventoryUtils::axeScore))
            return !preferredOrExact(slot, stack, "Axe", InventoryUtils.getBestAxe(true), InventoryUtils::axeScore);
        if (stack.is(ItemTags.PICKAXES) && sameRank(stack, InventoryUtils.getBestPickaxe(true), InventoryUtils::pickaxeScore))
            return !preferredOrExact(slot, stack, "Pickaxe", InventoryUtils.getBestPickaxe(true), InventoryUtils::pickaxeScore);
        if (stack.is(ItemTags.SHOVELS) && sameRank(stack, InventoryUtils.getBestShovel(true), InventoryUtils::shovelScore))
            return !preferredOrExact(slot, stack, "Shovel", InventoryUtils.getBestShovel(true), InventoryUtils::shovelScore);
        return true;
    }

    private boolean duplicateBest(int slot, ItemStack stack, String type, ItemStack best,
                                  ToDoubleFunction<ItemStack> scorer) {
        return sameRank(stack, best, scorer) && !preferredOrExact(slot, stack, type, best, scorer);
    }

    private boolean preferredOrExact(int slot, ItemStack stack, String type, ItemStack best,
                                     ToDoubleFunction<ItemStack> scorer) {
        for (int target = 0; target < slotTypes.length; target++) {
            if (!slotTypes[target].is(type)) continue;
            int targetSlot = targetInventorySlot(target);
            if (sameRank(minecraft.player.getInventory().getItem(targetSlot), best, scorer)) return slot == targetSlot;
        }
        return stack == best;
    }

    private boolean duplicateArmor(int slot, ItemStack stack) {
        EquipmentSlot equipmentSlot = InventoryUtils.armorSlot(stack);
        ItemStack best = InventoryUtils.getBestArmor(equipmentSlot);
        if (!sameRank(stack, best, InventoryUtils::armorScore)) return true;
        int inventorySlot = armorInventorySlot(equipmentSlot);
        ItemStack equipped = minecraft.player.getInventory().getItem(inventorySlot);
        if (sameRank(equipped, best, InventoryUtils::armorScore)) return slot != inventorySlot;
        return stack != best;
    }

    private int extraBlockDropSlot() {
        int total = totalBlockCount();
        int maximum = maxBlockCount.getValue().intValue();
        if (total <= maximum) return -1;
        int excess = total - maximum;
        boolean unreservedExists = hasUnreservedBlock();
        int closestSlot = -1, closestCount = Integer.MAX_VALUE, largestSlot = -1, largestCount = -1;
        for (int slot = 0; slot < minecraft.player.getInventory().getContainerSize(); slot++) {
            if (isEquipmentSlot(slot) || unreservedExists && isBlockTargetSlot(slot)) continue;
            ItemStack stack = minecraft.player.getInventory().getItem(slot);
            if (!isCountedBlock(stack)) continue;
            if (stack.getCount() >= excess && stack.getCount() < closestCount) {
                closestCount = stack.getCount(); closestSlot = slot;
            }
            if (stack.getCount() > largestCount) { largestCount = stack.getCount(); largestSlot = slot; }
        }
        return closestSlot != -1 ? closestSlot : largestSlot;
    }

    private boolean hasUnreservedBlock() {
        for (int slot = 0; slot < minecraft.player.getInventory().getContainerSize(); slot++)
            if (!isEquipmentSlot(slot) && !isBlockTargetSlot(slot)
                    && isCountedBlock(minecraft.player.getInventory().getItem(slot))) return true;
        return false;
    }

    private int totalBlockCount() {
        int total = 0;
        for (int slot = 0; slot < minecraft.player.getInventory().getContainerSize(); slot++)
            if (!isEquipmentSlot(slot) && isCountedBlock(minecraft.player.getInventory().getItem(slot)))
                total += minecraft.player.getInventory().getItem(slot).getCount();
        return total;
    }

    private boolean isCountedBlock(ItemStack stack) {
        return stack.getItem() instanceof BlockItem && !stack.is(Items.TNT);
    }

    private boolean isBlockTargetSlot(int slot) {
        for (int target = 0; target < slotTypes.length; target++)
            if (slotTypes[target].is("Block") && targetInventorySlot(target) == slot) return true;
        return false;
    }

    private boolean autoWear() {
        if (!elapsed(lastArmor, autoArmorDelay) || !minecraft.player.containerMenu.getCarried().isEmpty()) return false;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack best = InventoryUtils.getBestArmor(slot);
            int inventorySlot = armorInventorySlot(slot);
            if (best.isEmpty() || minecraft.player.getInventory().getItem(inventorySlot) == best) continue;
            int bestSlot = findInventorySlot(best);
            if (bestSlot == -1) continue;
            ItemStack equipped = minecraft.player.getInventory().getItem(inventorySlot);
            if (equipped.isEmpty()) click(minecraft.player.containerMenu, toContainerSlot(bestSlot), 0, ClickType.QUICK_MOVE);
            else {
                click(minecraft.player.containerMenu, toContainerSlot(bestSlot), 0, ClickType.PICKUP);
                click(minecraft.player.containerMenu, armorMenuSlot(slot), 0, ClickType.PICKUP);
                click(minecraft.player.containerMenu, toContainerSlot(bestSlot), 0, ClickType.PICKUP);
            }
            lastArmor = now();
            return true;
        }
        return false;
    }

    private boolean undressed() {
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack best = InventoryUtils.getBestArmor(slot);
            if (!best.isEmpty() && minecraft.player.getInventory().getItem(armorInventorySlot(slot)) != best) return true;
        }
        return false;
    }

    private boolean autoSort() {
        if (!elapsed(lastSort, sortDelay)) return false;
        for (int currentSlot : slots) {
            if (minecraft.player.getInventory().getItem(currentSlot).isEmpty()) continue;
            for (int target = 0; target < slotTypes.length; target++) {
                if (sortIntoTarget(currentSlot, target)) { lastSort = now(); return true; }
            }
        }
        return false;
    }

    private boolean sortIntoTarget(int currentSlot, int target) {
        String type = slotTypes[target].getValue();
        if (type.equals("None") || currentSlot == targetInventorySlot(target)) return false;
        int sourceSlot = sourceSlot(type, target);
        if (sourceSlot != currentSlot || sourceReserved(currentSlot, target, type)) return false;
        ItemStack source = minecraft.player.getInventory().getItem(sourceSlot);
        ItemStack destination = minecraft.player.getInventory().getItem(targetInventorySlot(target));
        if (sameSortedStack(type, destination, source)) return false;
        click(minecraft.player.containerMenu, toContainerSlot(currentSlot), swapButton(target), ClickType.SWAP);
        return true;
    }

    private boolean unsorted() {
        for (int target = 0; target < slotTypes.length; target++) {
            String type = slotTypes[target].getValue();
            if (type.equals("None")) continue;
            int source = sourceSlot(type, target);
            if (source == -1 || source == targetInventorySlot(target)) continue;
            if (!sameSortedStack(type, minecraft.player.getInventory().getItem(targetInventorySlot(target)),
                    minecraft.player.getInventory().getItem(source))) return true;
        }
        return false;
    }

    private int sourceSlot(String type, int target) {
        return switch (type) {
            case "Sword" -> findInventorySlot(InventoryUtils.getBestWeapon());
            case "Bow" -> findInventorySlot(InventoryUtils.getBestBow());
            case "Crossbow" -> findInventorySlot(InventoryUtils.getBestCrossbow());
            case "Pickaxe" -> findInventorySlot(InventoryUtils.getBestPickaxe(true));
            case "Axe" -> findInventorySlot(InventoryUtils.getBestAxe(true));
            case "Shovel" -> findInventorySlot(InventoryUtils.getBestShovel(true));
            case "Shears" -> findInventorySlot(InventoryUtils.getBestShears());
            case "Food", "Block", "Golden Apple", "Pearl", "Projectile", "TNT" -> largestConfiguredStack(target, type);
            case "Water Bucket" -> findFirstItem(Items.WATER_BUCKET);
            case "Lava Bucket" -> findFirstItem(Items.LAVA_BUCKET);
            case "Potion" -> findFirstMatching(stack -> stack.is(Items.POTION) && InventoryUtils.isWantedPotion(stack));
            case "Splash Potion" -> findFirstMatching(stack -> stack.is(Items.SPLASH_POTION) && InventoryUtils.isWantedPotion(stack));
            case "Fishing Rod" -> findFirstItem(Items.FISHING_ROD);
            case "Totem" -> findFirstItem(Items.TOTEM_OF_UNDYING);
            case "Shield" -> findFirstItem(Items.SHIELD);
            case "Fire Charge" -> findFirstItem(Items.FIRE_CHARGE);
            case "Flint And Steel" -> findInventorySlot(InventoryUtils.getBestFlintAndSteel());
            case "God Axe" -> findInventorySlot(InventoryUtils.getGodAxe());
            case "Knockback Ball" -> findInventorySlot(InventoryUtils.getKnockbackBall());
            default -> -1;
        };
    }

    private int largestConfiguredStack(int target, String type) {
        int targetSlot = targetInventorySlot(target), bestSlot = -1, count = 0;
        for (int slot = 0; slot < minecraft.player.getInventory().getContainerSize(); slot++) {
            if (sourceReserved(slot, target, type)) continue;
            ItemStack stack = minecraft.player.getInventory().getItem(slot);
            if (!matchesLargestType(stack, type)) continue;
            if (stack.getCount() > count || stack.getCount() == count && slot == targetSlot) {
                count = stack.getCount(); bestSlot = slot;
            }
        }
        return bestSlot;
    }

    private boolean matchesLargestType(ItemStack stack, String type) {
        if (stack.isEmpty()) return false;
        return switch (type) {
            case "Food" -> InventoryUtils.isConsumable(stack) && !stack.is(Items.POTION)
                    && !stack.is(Items.SPLASH_POTION) && !stack.is(Items.GOLDEN_APPLE)
                    && !stack.is(Items.ENCHANTED_GOLDEN_APPLE);
            case "Block" -> stack.getItem() instanceof BlockItem && !stack.is(Items.TNT);
            case "Golden Apple" -> stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE);
            case "Pearl" -> stack.is(Items.ENDER_PEARL);
            case "Projectile" -> stack.is(Items.EGG) || stack.is(Items.SNOWBALL);
            case "TNT" -> stack.is(Items.TNT);
            default -> false;
        };
    }

    private boolean sameSortedStack(String type, ItemStack target, ItemStack source) {
        if (source.isEmpty()) return true;
        return switch (type) {
            case "Food", "Block", "Golden Apple", "Pearl", "Projectile", "TNT" ->
                    !target.isEmpty() && ItemStack.isSameItemSameComponents(target, source)
                            && target.getCount() == source.getCount();
            case "Sword" -> sameRank(target, source, InventoryUtils::weaponScore);
            case "Bow" -> sameRank(target, source, InventoryUtils::bowScore);
            case "Crossbow" -> sameRank(target, source, InventoryUtils::crossbowScore);
            case "Pickaxe" -> sameRank(target, source, InventoryUtils::pickaxeScore);
            case "Axe" -> sameRank(target, source, InventoryUtils::axeScore);
            case "Shovel" -> sameRank(target, source, InventoryUtils::shovelScore);
            case "Shears" -> sameRank(target, source, InventoryUtils::shearsScore);
            default -> ItemStack.isSameItemSameComponents(target, source);
        };
    }

    private boolean sourceReserved(int slot, int target, String type) {
        for (int other = 0; other < slotTypes.length; other++)
            if (other != target && slot == targetInventorySlot(other) && slotTypes[other].is(type)) return true;
        return false;
    }

    private boolean sameRank(ItemStack stack, ItemStack best, ToDoubleFunction<ItemStack> scorer) {
        return stack != null && best != null && !stack.isEmpty() && !best.isEmpty()
                && Double.compare(scorer.applyAsDouble(stack), scorer.applyAsDouble(best)) == 0
                && InventoryUtils.durability(stack) == InventoryUtils.durability(best);
    }

    private boolean hasDuplicate(Item item, int ignoredSlot) {
        for (int slot = 0; slot < minecraft.player.getInventory().getContainerSize(); slot++)
            if (slot != ignoredSlot && minecraft.player.getInventory().getItem(slot).is(item)) return true;
        return false;
    }

    private int findInventorySlot(ItemStack target) {
        if (target == null || target.isEmpty()) return -1;
        for (int slot = 0; slot < minecraft.player.getInventory().getContainerSize(); slot++)
            if (minecraft.player.getInventory().getItem(slot) == target) return slot;
        return -1;
    }

    private int findFirstItem(Item item) { return findFirstMatching(stack -> stack.is(item)); }

    private int findFirstMatching(Predicate<ItemStack> predicate) {
        for (int slot = 0; slot < minecraft.player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = minecraft.player.getInventory().getItem(slot);
            if (!stack.isEmpty() && predicate.test(stack)) return slot;
        }
        return -1;
    }

    private int targetInventorySlot(int target) {
        return target < 9 ? target : InventoryUtils.OFFHAND_SLOT_INVENTORY;
    }

    private int swapButton(int target) {
        return target < 9 ? target : InventoryUtils.OFFHAND_SLOT_INVENTORY;
    }

    private int toContainerSlot(int inventorySlot) {
        return switch (inventorySlot) {
            case InventoryUtils.HELMET_SLOT_INVENTORY -> InventoryUtils.HELMET_SLOT_MENU;
            case InventoryUtils.CHEST_SLOT_INVENTORY -> InventoryUtils.CHEST_SLOT_MENU;
            case InventoryUtils.LEGS_SLOT_INVENTORY -> InventoryUtils.LEGS_SLOT_MENU;
            case InventoryUtils.FEET_SLOT_INVENTORY -> InventoryUtils.FEET_SLOT_MENU;
            case InventoryUtils.OFFHAND_SLOT_INVENTORY -> InventoryUtils.OFFHAND_SLOT_MENU;
            default -> inventorySlot <= 8 ? inventorySlot + 36 : inventorySlot;
        };
    }

    private boolean isEquipmentSlot(int slot) {
        return slot >= InventoryUtils.FEET_SLOT_INVENTORY && slot <= InventoryUtils.OFFHAND_SLOT_INVENTORY;
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

    private boolean isContainerMenu(AbstractContainerMenu menu) {
        return menu instanceof ChestMenu || menu instanceof FurnaceMenu || menu instanceof BlastFurnaceMenu
                || menu instanceof SmokerMenu || menu instanceof BrewingStandMenu;
    }

    private void click(AbstractContainerMenu menu, int slot, int button, ClickType type) {
        if (menu == null || mc.player == null || minecraft.gameMode == null) return;
        packetClick = clickMode.is("Packet");
        boolean isSprinting = ((LocalPlayerAccessor)mc.player).isWasSprinting();
        if (isSprinting) return;
        try {
            minecraft.gameMode.handleInventoryMouseClick(menu.containerId, slot, button, type, mc.player);
            PacketUtils.sendPacketNoEvent(new ServerboundContainerClosePacket(menu.containerId));
        }
        finally { packetClick = false; }
    }

    private void openSpoofedInventory() {
        if (openInventory.getValue() || minecraft.screen != null || serverInventoryOpened) return;
        minecraft.player.sendOpenInventory();
        serverInventoryOpened = true;
    }

    private void closeSpoofedInventory() {
        if (!serverInventoryOpened) return;
        if (minecraft.getConnection() != null) minecraft.getConnection().send(new ServerboundContainerClosePacket(0));
        serverInventoryOpened = false;
    }

    private void stopPlayerSprint() {
        if (minecraft.player != null) minecraft.player.setSprinting(false);
        if (minecraft.options != null) minecraft.options.keySprint.setDown(false);
    }

    private boolean elapsed(long start, NumberSetting delay) {
        return delay.getValue().longValue() == 0 || now() - start >= delay.getValue().longValue();
    }

    private long now() { return System.currentTimeMillis(); }

    private void resetState() {
        slots.clear();
        lastSort = lastThrow = lastCraft = lastArmor = 0L;
        randomized = false;
        serverInventoryOpened = false;
        stopSprint = false;
        packetClick = false;
    }
}
