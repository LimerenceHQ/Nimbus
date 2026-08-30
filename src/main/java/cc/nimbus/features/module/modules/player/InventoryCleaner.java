package cc.nimbus.features.module.modules.player;

import cc.nimbus.core.Core;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.game.EventRotation;
import cc.nimbus.event.impl.game.EventTick;
import cc.nimbus.event.impl.game.EventTickUpdate;
import cc.nimbus.event.impl.network.PacketEvent;
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
import cc.nimbus.managers.GameStateManager;
import cc.nimbus.mixin.accessor.player.LocalPlayerAccessor;
import cc.nimbus.utils.client.TimerUtils;
import cc.nimbus.utils.entity.player.InventoryUtils;
import cc.nimbus.utils.entity.player.MovementUtils;
import cc.nimbus.utils.network.PacketUtils;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static cc.nimbus.utils.entity.player.InventoryUtils.isValidStack;


@ModuleInfo(
        name = "InventoryCleaner",
        alias = {"invmanager", "invcleaner"},
        description = "Silently equips, sorts and cleans the player inventory.",
        category = ModuleCategory.Player
)
public class InventoryCleaner extends BasicModule {
    public static final TimerUtils timer = new TimerUtils();
    public final NumberSetting delay = new NumberSetting("Delay (Ticks)", 350F, 0F, 2000F, 1.0F);
    public final ModeSetting offhandItems = new ModeSetting("Offhand Items", new Modes("None", "Golden Apple", "Projectile", "Fishing Rod", "Block"));
    public final BooleanSetting autoArmor = new BooleanSetting("Auto Armor", true);
    public final BooleanSetting disOnKillaura = new BooleanSetting("Dis Only KillAura", false);
    public final BooleanSetting fastdrop = new BooleanSetting("Fast Drop", true);
    public final BooleanSetting inventoryOnly = new BooleanSetting("Inventory Only", false);
    public final BooleanSetting noMove = new BooleanSetting("NoMove Only", false);
    public final BooleanSetting switchSword = new BooleanSetting("Switch Sword", true);
    public final NumberSetting swordSlot = new NumberSetting("Sword Slot", 1.0F, 1.0F, 9.0F, 1.0F);
    public final BooleanSetting switchBlock = new BooleanSetting("Switch Block", true);
    public final NumberSetting blockSlot = new NumberSetting("Block Slot", 2.0F, 1.0F, 9.0F, 1.0F);
    public final NumberSetting maxBlockSize = new NumberSetting("Max Block Size", 256.0F, 64.0F, 512.0F, 64.0F);
    public final BooleanSetting switchPickaxe = new BooleanSetting("Switch Pickaxe", true);
    public final NumberSetting pickaxeSlot = new NumberSetting("Pickaxe Slot", 3.0F, 1.0F, 9.0F, 1.0F);
    public final BooleanSetting switchAxe = new BooleanSetting("Switch Axe", true);
    public final NumberSetting axeSlot = new NumberSetting("Axe Slot", 4.0F, 1.0F, 9.0F, 1.0F);
    public final BooleanSetting switchBow = new BooleanSetting("Switch Bow or Crossbow", true);
    public final NumberSetting bowSlot = new NumberSetting("Bow Slot", 5.0F, 1.0F, 9.0F, 1.0F);
    public final ModeSetting preferBow = new ModeSetting("Bow Priority", new Modes("Crossbow", "Power Bow", "Punch Bow"));
    public final NumberSetting maxArrowSize = new NumberSetting("Max Arrow Size", 256.0F, 64.0F, 512.0F, 64.0F);
    public final BooleanSetting switchWaterBucket = new BooleanSetting("Switch Water Bucket", true);
    public final NumberSetting waterBucketSlot = new NumberSetting("Water Bucket Slot", 6.0F, 1.0F, 9.0F, 1.0F);
    public final BooleanSetting switchEnderPearl = new BooleanSetting("Switch Ender Pearl", true);
    public final NumberSetting enderPearlSlot = new NumberSetting("Ender Pearl Slot", 7.0F, 1.0F, 9.0F, 1.0F);
    public final BooleanSetting switchFireball = new BooleanSetting("Switch Fireball", true);
    public final NumberSetting fireballSlot = new NumberSetting("Fireball Slot", 8.0F, 1.0F, 9.0F, 1.0F);
    public final BooleanSetting switchGoldenApple = new BooleanSetting("Switch Golden Apple", true);
    public final NumberSetting goldenAppleSlot = new NumberSetting("Golden Apple Slot", 9.0F, 1.0F, 9.0F, 1.0F);
    public final BooleanSetting throwItems = new BooleanSetting("Throw Items", true);
    public final NumberSetting waterBucketCount = new NumberSetting("Keep Water Buckets", 1.0F, 0.0F, 5.0F, 1.0F);
    public final NumberSetting lavaBucketCount = new NumberSetting("Keep Lava Buckets", 1.0F, 0.0F, 5.0F, 1.0F);
    public final BooleanSetting keepProjectile = new BooleanSetting("Keep Eggs & Snowballs", true);
    public final BooleanSetting switchProjectile = new BooleanSetting("Switch Eggs & Snowballs", false);
    public final NumberSetting projectileSlot = new NumberSetting("Eggs & Snowballs Slot", 9.0F, 1.0F, 9.0F, 1.0F);
    public final NumberSetting maxProjectileSize = new NumberSetting("Max Eggs & Snowballs Size", 64.0F, 16.0F, 256.0F, 16.0F);
    public final BooleanSetting switchRod = new BooleanSetting("Switch Rod", false);
    public final NumberSetting rodSlot = new NumberSetting("Rod Slot", 9.0F, 1.0F, 9.0F, 1.0F);

    int noMoveTicks = 0;
    private static final Queue<Packet<?>> packetQueue = new LinkedList<>();
    private boolean clickOffHand = false;
    boolean firstDrop = true;
    public static boolean inventoryOpen = false;
    public static boolean active = false;
    private static boolean stopSprint;
    @Override
    protected void onEnable() {

    }

    @Override
    protected void onDisable() {
        firstDrop = true;
        packetQueue.clear();
        inventoryOpen = false;
        this.clickOffHand = false;
    }


    @EventTarget
    private void onPacketSend(PacketEvent e) {
        if (mc.player == null || mc.level == null) return;
        if (fastdrop.getValue() && inventoryOpen) {
            if (e.getPacket() instanceof ServerboundContainerClickPacket containerClickPacket) {
                if (containerClickPacket.getClickType() == ClickType.THROW) {
                    packetQueue.add(containerClickPacket);
                    e.cancelEvent();
                }
            }
        }
        if (!noMove.getValue()) return;
        if (e.getPacket() instanceof ServerboundContainerClosePacket) {
            inventoryOpen = false;
        }

        if (inventoryOpen && !this.inventoryOnly.getValue()) {
            if (e.getPacket() instanceof ServerboundMovePlayerPacket) {
                if (MovementUtils.isMoving()) {
                    mc.getConnection().send(new ServerboundContainerClosePacket(mc.player.inventoryMenu.containerId));
                }
            } else if (e.getPacket() instanceof ServerboundUseItemOnPacket || e.getPacket() instanceof ServerboundUseItemPacket || e.getPacket() instanceof ServerboundInteractPacket || e.getPacket() instanceof ServerboundPlayerActionPacket) {
                mc.getConnection().send(new ServerboundContainerClosePacket(mc.player.inventoryMenu.containerId));
            }
        }
    };

    @EventTarget
    private void onTick(EventTick e) {
        if (mc.player == null || mc.level == null) return;
        this.setSuffix(delay.getValue());
        if (e.isPost()) {
            active = false;
            stopSprint = false;
        }
    }

    @EventTarget
    private void onTick(EventRotation e) {
        if (mc.player == null || mc.level == null) return;
        this.setSuffix(delay.getValue());
        label350:
        {
            if (InventoryUtils.shouldDisableFeatures()) {
                return;
            }
            if (mc.player.isUsingItem()) return;

            if (Core.INSTANCE.getModuleManager().getModule(KillAura.class).isEnabled() && Core.INSTANCE.getModuleManager().getModule(KillAura.class).attackTarget != null && disOnKillaura.getValue()) {
                return;
            }
            if (Core.INSTANCE.getModuleManager().getModule(Scaffold.class).isEnabled()) {
                stopSprint = false;
                active = false;
                break label350;
            }
            if (MovementUtils.isMoving() && noMove.getValue()) {
                this.noMoveTicks = 0;
            } else {
                ++this.noMoveTicks;
            }
            if (ContainerStealer.isWorking()) {
                break label350;
            }

            if (this.inventoryOnly.getValue()) {
                if (!(mc.screen instanceof InventoryScreen)) {
                    break label350;
                }
            } else if (this.noMoveTicks <= 1) {
                break label350;
            }

            if (mc.screen instanceof AbstractContainerScreen) {
                AbstractContainerScreen container = (AbstractContainerScreen) mc.screen;
                if (container.getMenu().containerId != mc.player.inventoryMenu.containerId) {
                    return;
                }
            }

            if (inventoryOpen) {

                // 物品整理逻辑开始
                if (this.autoArmor.getValue()) {
                    for (int i = 0; i < mc.player.getInventory().armor.size(); ++i) {
                        ItemStack stack = (ItemStack) mc.player.getInventory().armor.get(i);
                        if (stack.getItem() instanceof ArmorItem) {
                            ArmorItem item = (ArmorItem) stack.getItem();
                            if (!stack.isEmpty() && InventoryUtils.getBestArmorScore(item.getEquipmentSlot()) > InventoryUtils.getProtection(stack)) {
                                clickSlotA(mc.player.inventoryMenu.containerId, 4 + (4 - i), 1, ClickType.THROW, mc.player);
                                inventoryOpen = true;
                            }
                        }
                    }

                    for (int i = 0; i < mc.player.getInventory().items.size(); ++i) {
                        ItemStack stack = (ItemStack) mc.player.getInventory().items.get(i);
                        if (!stack.isEmpty() && stack.getItem() instanceof ArmorItem) {
                            ArmorItem item = (ArmorItem) stack.getItem();
                            float currentItemScore = InventoryUtils.getProtection(stack);
                            boolean isBestItem = InventoryUtils.getBestArmorScore(item.getEquipmentSlot()) == currentItemScore;
                            boolean isBetterItem = InventoryUtils.getCurrentArmorScore(item.getEquipmentSlot()) < currentItemScore;
                            if (isBestItem && isBetterItem) {
                                if (i < 9) {
                                    clickSlot(mc.player.inventoryMenu.containerId, i + 36, 0, ClickType.QUICK_MOVE, mc.player);
                                } else {
                                    clickSlot(mc.player.inventoryMenu.containerId, i, 0, ClickType.QUICK_MOVE, mc.player);
                                }

                                inventoryOpen = true;
                            }
                        }
                    }
                }

                if (this.clickOffHand) {
                    clickSlot(mc.player.inventoryMenu.containerId, 45, 0, ClickType.PICKUP, mc.player);
                    inventoryOpen = true;
                    this.clickOffHand = false;
                }
                if (this.offhandItems.is("Golden Apple")) {
                    ItemStack offHand = (ItemStack) mc.player.getInventory().offhand.get(0);
                    int slot = InventoryUtils.getItemSlot(Items.GOLDEN_APPLE);
                    if (slot != -1) {
                        if (offHand.getItem() == Items.GOLDEN_APPLE) {
                            ItemStack goldenAppleStack = (ItemStack) mc.player.getInventory().items.get(slot);
                            if (offHand.getCount() + goldenAppleStack.getCount() <= 64) {
                                if (slot < 9) {
                                    clickSlot(mc.player.inventoryMenu.containerId, slot + 36, 0, ClickType.PICKUP, mc.player);
                                } else {
                                    clickSlot(mc.player.inventoryMenu.containerId, slot, 0, ClickType.PICKUP, mc.player);
                                }

                                inventoryOpen = true;
                                this.clickOffHand = true;
                            }
                        } else {
                            this.swapOffHand(slot);
                        }
                    }
                } else if (this.offhandItems.is("Projectile")) {
                    ItemStack offHand = (ItemStack) mc.player.getInventory().offhand.get(0);
                    ItemStack bestProjectile = InventoryUtils.getBestProjectile();
                    if (bestProjectile != null) {
                        int slot = InventoryUtils.getItemStackSlot(bestProjectile);
                        boolean shouldSwap = false;
                        if (offHand.getItem() != Items.EGG && offHand.getItem() != Items.SNOWBALL) {
                            shouldSwap = true;
                        } else if (offHand.getCount() < bestProjectile.getCount()) {
                            shouldSwap = true;
                        }

                        if (shouldSwap && slot != -1) {
                            this.swapOffHand(slot);
                        }
                    }
                } else if (this.offhandItems.is("Fishing Rod")) {
                    ItemStack offHand = (ItemStack) mc.player.getInventory().offhand.get(0);
                    int slot = InventoryUtils.getItemSlot(Items.FISHING_ROD);
                    if (slot != -1 && offHand.getItem() != Items.FISHING_ROD) {
                        this.swapOffHand(slot);
                    }
                } else if (this.offhandItems.is("Block")) {
                    ItemStack offHand = (ItemStack) mc.player.getInventory().offhand.get(0);
                    ItemStack bestBlock = InventoryUtils.getBestBlock();
                    if (bestBlock != null) {
                        int slot = InventoryUtils.getItemStackSlot(bestBlock);
                        boolean shouldSwap = false;
                        if (isValidStack(offHand)) {
                            if (offHand.getCount() < bestBlock.getCount()) {
                                shouldSwap = true;
                            }
                        } else {
                            shouldSwap = true;
                        }

                        if (shouldSwap && slot != -1) {
                            this.swapOffHand(slot);
                        }
                    }
                }

                if (this.switchGoldenApple.getValue() && !this.offhandItems.is("Golden Apple")) {
                    this.swapItem((int) (this.goldenAppleSlot.getValue().floatValue() - 1.0F), Items.GOLDEN_APPLE);
                }

                if (this.switchBlock.getValue()) {
                    if (!Core.INSTANCE.getModuleManager().getModule(Scaffold.class).isEnabled()) {
                        int blockSlot = (int) (this.blockSlot.getValue().floatValue() - 1.0F);
                        ItemStack currentBlock = (ItemStack) mc.player.getInventory().items.get(blockSlot);
                        ItemStack bestBlock = InventoryUtils.getBestBlock();
                        if (bestBlock != null && (bestBlock.getCount() > currentBlock.getCount() || isValidStack(currentBlock)) && !this.offhandItems.is("Block")) {
                            this.swapItem(blockSlot, bestBlock);
                        }

                        if ((float) InventoryUtils.getBlockCountInInventory() > this.maxBlockSize.getValue().floatValue()) {
                            ItemStack worstBlock = InventoryUtils.getWorstBlock();
                            this.throwItem(worstBlock);
                        }
                    }
                }

                if (this.switchSword.getValue()) {
                    int slot = (int) (this.swordSlot.getValue().floatValue() - 1.0F);
                    ItemStack currentSword = (ItemStack) mc.player.getInventory().items.get(slot);
                    ItemStack bestSword = InventoryUtils.getBestSword();
                    ItemStack bestShapeAxe = InventoryUtils.getBestShapeAxe();
                    if (InventoryUtils.getAxeDamage(bestShapeAxe) > InventoryUtils.getSwordDamage(bestSword)) {
                        bestSword = bestShapeAxe;
                    }

                    if (bestSword != null) {
                        float currentDamage = currentSword.getItem() instanceof SwordItem ? InventoryUtils.getSwordDamage(currentSword) : InventoryUtils.getAxeDamage(currentSword);
                        float bestWeaponDamage = bestSword.getItem() instanceof SwordItem ? InventoryUtils.getSwordDamage(bestSword) : InventoryUtils.getAxeDamage(bestSword);
                        if (bestWeaponDamage > currentDamage) {
                            this.swapItem(slot, bestSword);
                        }
                    }
                }

                if (this.switchPickaxe.getValue()) {
                    int slot = (int) (this.pickaxeSlot.getValue().floatValue() - 1.0F);
                    ItemStack bestPickaxe = InventoryUtils.getBestPickaxe();
                    ItemStack currentPickaxe = (ItemStack) mc.player.getInventory().items.get(slot);
                    if (bestPickaxe != null && bestPickaxe.getItem() instanceof PickaxeItem && (InventoryUtils.getToolScore(bestPickaxe) > InventoryUtils.getToolScore(currentPickaxe) || !(currentPickaxe.getItem() instanceof PickaxeItem))) {
                        this.swapItem(slot, bestPickaxe);
                    }
                }

                if (this.switchAxe.getValue()) {
                    int slot = (int) (this.axeSlot.getValue().floatValue() - 1.0F);
                    ItemStack bestAxe = InventoryUtils.getBestAxe();
                    ItemStack currentAxe = (ItemStack) mc.player.getInventory().items.get(slot);
                    if (bestAxe != null && bestAxe.getItem() instanceof AxeItem && (InventoryUtils.getToolScore(bestAxe) > InventoryUtils.getToolScore(currentAxe) || !(currentAxe.getItem() instanceof AxeItem))) {
                        this.swapItem(slot, bestAxe);
                    }
                }

                if (this.switchRod.getValue() && !this.offhandItems.is("Fishing Rod")) {
                    int slot = (int) (this.rodSlot.getValue().floatValue() - 1.0F);
                    ItemStack bestRod = InventoryUtils.getFishingRod();
                    ItemStack currentRod = (ItemStack) mc.player.getInventory().items.get(slot);
                    if (!(currentRod.getItem() instanceof FishingRodItem)) {
                        this.swapItem(slot, bestRod);
                    }
                }

                if (this.switchBow.getValue()) {
                    int slot = (int) (this.bowSlot.getValue().floatValue() - 1.0F);
                    ItemStack currentBow = (ItemStack) mc.player.getInventory().items.get(slot);
                    ItemStack bestBow;
                    float bestBowScore;
                    float currentBowScore;
                    if (this.preferBow.is("Crossbow")) {
                        bestBow = InventoryUtils.getBestCrossbow();
                        bestBowScore = InventoryUtils.getCrossbowScore(bestBow);
                        currentBowScore = InventoryUtils.getCrossbowScore(currentBow);
                    } else if (this.preferBow.is("Power Bow")) {
                        bestBow = InventoryUtils.getBestPowerBow();
                        bestBowScore = InventoryUtils.getPowerBowScore(bestBow);
                        currentBowScore = InventoryUtils.getPowerBowScore(currentBow);
                    } else {
                        bestBow = InventoryUtils.getBestPunchBow();
                        bestBowScore = InventoryUtils.getPunchBowScore(bestBow);
                        currentBowScore = InventoryUtils.getPunchBowScore(currentBow);
                    }

                    if (bestBow == null) {
                        bestBow = InventoryUtils.getBestCrossbow();
                        bestBowScore = InventoryUtils.getCrossbowScore(bestBow);
                        currentBowScore = InventoryUtils.getCrossbowScore(currentBow);
                    }

                    if (bestBow == null) {
                        bestBow = InventoryUtils.getBestPowerBow();
                        bestBowScore = InventoryUtils.getPowerBowScore(bestBow);
                        currentBowScore = InventoryUtils.getPowerBowScore(currentBow);
                    }

                    if (bestBow == null) {
                        bestBow = InventoryUtils.getBestPunchBow();
                        bestBowScore = InventoryUtils.getPunchBowScore(bestBow);
                        currentBowScore = InventoryUtils.getPunchBowScore(currentBow);
                    }

                    if (bestBow != null && bestBowScore > currentBowScore) {
                        this.swapItem(slot, bestBow);
                    }

                    if ((float) InventoryUtils.getItemCount(Items.ARROW) > this.maxArrowSize.getValue().floatValue()) {
                        ItemStack worstArrow = InventoryUtils.getWorstArrow();
                        this.throwItem(worstArrow);
                    }
                }

                if (this.switchEnderPearl.getValue()) {
                    this.swapItem((int) (this.enderPearlSlot.getValue().floatValue() - 1.0F), Items.ENDER_PEARL);
                }

                if (this.switchWaterBucket.getValue()) {
                    this.swapItem((int) (this.waterBucketSlot.getValue().floatValue() - 1.0F), Items.WATER_BUCKET);
                }

                if (this.switchFireball.getValue()) {
                    this.swapItem((int) (this.fireballSlot.getValue().floatValue() - 1.0F), Items.FIRE_CHARGE);
                }

                if (this.keepProjectile.getValue()) {
                    if ((float) (InventoryUtils.getItemCount(Items.EGG) + InventoryUtils.getItemCount(Items.SNOWBALL)) > this.maxProjectileSize.getValue().floatValue()) {
                        ItemStack worstProjectile = InventoryUtils.getWorstProjectile();
                        this.throwItem(worstProjectile);
                    }

                    if (this.switchProjectile.getValue() && !this.offhandItems.is("Projectile")) {
                        int projectileSlot = (int) (this.projectileSlot.getValue().floatValue() - 1.0F);
                        if (InventoryUtils.getItemCount(Items.EGG) > 0) {
                            this.swapItem(projectileSlot, Items.EGG);
                        } else if (InventoryUtils.getItemCount(Items.SNOWBALL) > 0) {
                            this.swapItem(projectileSlot, Items.SNOWBALL);
                        }
                    }
                }

                if (this.throwItems.getValue()) {
                    List slots = (List) IntStream.range(0, mc.player.getInventory().items.size()).boxed().collect(Collectors.toList());
                    Collections.shuffle(slots);

                    for (Object slot : slots) {
                        ItemStack stack = (ItemStack) mc.player.getInventory().items.get((Integer) slot);
                        if (!stack.isEmpty() && !this.isItemUseful(stack)) {
                            this.throwItem(stack);
                        }
                    }
                }
            }

            boolean needsOrganizing = this.needsInventoryOrganizing();

            if (needsOrganizing && !inventoryOpen) {
                inventoryOpen = true;
            }

            if (!needsOrganizing && inventoryOpen) {

                if (isInventoryOrganizingComplete()) {
                    if (fastdrop.getValue() && !packetQueue.isEmpty()) {
                        stopSprint = true;
                    }
                    boolean isSprinting = ((LocalPlayerAccessor)mc.player).isWasSprinting();
                    while (!packetQueue.isEmpty() && fastdrop.getValue() && !isSprinting) {
                        Packet<?> packet = packetQueue.poll();
                        PacketUtils.sendPacketNoEvent(packet);
                    }
                    if (packetQueue.isEmpty()) {
                        mc.getConnection().send(new ServerboundContainerClosePacket(mc.player.inventoryMenu.containerId));

                        inventoryOpen = false;

                        break label350;
                    }

                }
            }
            return;
        }

        this.clickOffHand = false;

    };

    private boolean isInventoryOrganizingComplete() {
        if (this.throwItems.getValue()) {
            for(int i = 0; i < mc.player.getInventory().items.size(); ++i) {
                ItemStack stack = mc.player.getInventory().items.get(i);
                if (!stack.isEmpty() && !this.isItemUseful(stack)) {
                    return false;
                }
            }
        }

        if (this.autoArmor.getValue()) {
            for(int i = 0; i < mc.player.getInventory().armor.size(); ++i) {
                ItemStack stack = (ItemStack)mc.player.getInventory().armor.get(i);
                if (stack.getItem() instanceof ArmorItem) {
                    ArmorItem item = (ArmorItem)stack.getItem();
                    if (!stack.isEmpty() && InventoryUtils.getBestArmorScore(item.getEquipmentSlot()) > InventoryUtils.getProtection(stack)) {
                        return false;
                    }
                }
            }

            for(int i = 0; i < mc.player.getInventory().items.size(); ++i) {
                ItemStack stack = (ItemStack)mc.player.getInventory().items.get(i);
                if (!stack.isEmpty() && stack.getItem() instanceof ArmorItem) {
                    ArmorItem item = (ArmorItem)stack.getItem();
                    float currentItemScore = InventoryUtils.getProtection(stack);
                    boolean isBestItem = InventoryUtils.getBestArmorScore(item.getEquipmentSlot()) == currentItemScore;
                    boolean isBetterItem = InventoryUtils.getCurrentArmorScore(item.getEquipmentSlot()) < currentItemScore;
                    if (isBestItem && isBetterItem) {
                        return false;
                    }
                }
            }
        }

        if (this.clickOffHand) {
            return false;
        }

        if (this.offhandItems.is("Golden Apple")) {
            ItemStack offHand = (ItemStack)mc.player.getInventory().offhand.get(0);
            int slot = InventoryUtils.getItemSlot(Items.GOLDEN_APPLE);
            if (slot != -1) {
                if (offHand.getItem() == Items.GOLDEN_APPLE) {
                    ItemStack goldenAppleStack = (ItemStack)mc.player.getInventory().items.get(slot);
                    if (offHand.getCount() + goldenAppleStack.getCount() <= 64) {
                        return false;
                    }
                } else {
                    return false;
                }
            }
        } else if (this.offhandItems.is("Projectile")) {
            ItemStack offHand = (ItemStack)mc.player.getInventory().offhand.get(0);
            ItemStack bestProjectile = InventoryUtils.getBestProjectile();
            if (bestProjectile != null) {
                int slot = InventoryUtils.getItemStackSlot(bestProjectile);
                boolean shouldSwap = false;
                if (offHand.getItem() != Items.EGG && offHand.getItem() != Items.SNOWBALL) {
                    shouldSwap = true;
                } else if (offHand.getCount() < bestProjectile.getCount()) {
                    shouldSwap = true;
                }
                if (shouldSwap && slot != -1) {
                    return false;
                }
            }
        } else if (this.offhandItems.is("Fishing Rod")) {
            ItemStack offHand = (ItemStack)mc.player.getInventory().offhand.get(0);
            int slot = InventoryUtils.getItemSlot(Items.FISHING_ROD);
            if (slot != -1 && offHand.getItem() != Items.FISHING_ROD) {
                return false;
            }
        } else if (this.offhandItems.is("Block")) {
            ItemStack offHand = (ItemStack)mc.player.getInventory().offhand.get(0);
            ItemStack bestBlock = InventoryUtils.getBestBlock();
            if (bestBlock != null) {
                int slot = InventoryUtils.getItemStackSlot(bestBlock);
                boolean shouldSwap = false;
                if (isValidStack(offHand)) {
                    if (offHand.getCount() < bestBlock.getCount()) {
                        shouldSwap = true;
                    }
                } else {
                    shouldSwap = true;
                }
                if (shouldSwap && slot != -1) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean needsInventoryOrganizing() {
        if (this.autoArmor.getValue()) {
            for(int i = 0; i < mc.player.getInventory().armor.size(); ++i) {
                ItemStack stack = (ItemStack)mc.player.getInventory().armor.get(i);
                if (stack.getItem() instanceof ArmorItem) {
                    ArmorItem item = (ArmorItem)stack.getItem();
                    if (!stack.isEmpty() && InventoryUtils.getBestArmorScore(item.getEquipmentSlot()) > InventoryUtils.getProtection(stack)) {
                        return true;
                    }
                }
            }

            for(int i = 0; i < mc.player.getInventory().items.size(); ++i) {
                ItemStack stack = (ItemStack)mc.player.getInventory().items.get(i);
                if (!stack.isEmpty() && stack.getItem() instanceof ArmorItem) {
                    ArmorItem item = (ArmorItem)stack.getItem();
                    float currentItemScore = InventoryUtils.getProtection(stack);
                    boolean isBestItem = InventoryUtils.getBestArmorScore(item.getEquipmentSlot()) == currentItemScore;
                    boolean isBetterItem = InventoryUtils.getCurrentArmorScore(item.getEquipmentSlot()) < currentItemScore;
                    if (isBestItem && isBetterItem) {
                        return true;
                    }
                }
            }
        }

        if (this.clickOffHand) {
            return true;
        }

        if (this.offhandItems.is("Golden Apple")) {
            ItemStack offHand = (ItemStack)mc.player.getInventory().offhand.get(0);
            int slot = InventoryUtils.getItemSlot(Items.GOLDEN_APPLE);
            if (slot != -1) {
                if (offHand.getItem() == Items.GOLDEN_APPLE) {
                    ItemStack goldenAppleStack = (ItemStack)mc.player.getInventory().items.get(slot);
                    if (offHand.getCount() + goldenAppleStack.getCount() <= 64) {
                        return true;
                    }
                } else {
                    return true;
                }
            }
        } else if (this.offhandItems.is("Projectile")) {
            ItemStack offHand = (ItemStack)mc.player.getInventory().offhand.get(0);
            ItemStack bestProjectile = InventoryUtils.getBestProjectile();
            if (bestProjectile != null) {
                int slot = InventoryUtils.getItemStackSlot(bestProjectile);
                boolean shouldSwap = false;
                if (offHand.getItem() != Items.EGG && offHand.getItem() != Items.SNOWBALL) {
                    shouldSwap = true;
                } else if (offHand.getCount() < bestProjectile.getCount()) {
                    shouldSwap = true;
                }
                if (shouldSwap && slot != -1) {
                    return true;
                }
            }
        } else if (this.offhandItems.is("Fishing Rod")) {
            ItemStack offHand = (ItemStack)mc.player.getInventory().offhand.get(0);
            int slot = InventoryUtils.getItemSlot(Items.FISHING_ROD);
            if (slot != -1 && offHand.getItem() != Items.FISHING_ROD) {
                return true;
            }
        } else if (this.offhandItems.is("Block")) {
            ItemStack offHand = (ItemStack)mc.player.getInventory().offhand.get(0);
            ItemStack bestBlock = InventoryUtils.getBestBlock();
            if (bestBlock != null) {
                int slot = InventoryUtils.getItemStackSlot(bestBlock);
                boolean shouldSwap = false;
                if (isValidStack(offHand)) {
                    if (offHand.getCount() < bestBlock.getCount()) {
                        shouldSwap = true;
                    }
                } else {
                    shouldSwap = true;
                }
                if (shouldSwap && slot != -1) {
                    return true;
                }
            }
        }

        if (this.switchGoldenApple.getValue() && !this.offhandItems.is("Golden Apple")) {
            int slot = (int)(this.goldenAppleSlot.getValue().floatValue() - 1.0F);
            ItemStack currentItem = (ItemStack)mc.player.getInventory().items.get(slot);
            int goldenAppleSlot = InventoryUtils.getItemSlot(Items.GOLDEN_APPLE);
            if (goldenAppleSlot != -1 && currentItem.getItem() != Items.GOLDEN_APPLE) {
                return true;
            }
        }

        if (this.switchBlock.getValue()) {
            int blockSlot = (int)(this.blockSlot.getValue().floatValue() - 1.0F);
            ItemStack currentBlock = (ItemStack)mc.player.getInventory().items.get(blockSlot);
            ItemStack bestBlock = InventoryUtils.getBestBlock();
            if (!Core.INSTANCE.getModuleManager().getModule(Scaffold.class).isEnabled()) {
                if (bestBlock != null && (bestBlock.getCount() > currentBlock.getCount() || !isValidStack(currentBlock)) && !this.offhandItems.is("Block")) {
                    return true;
                }
                if ((float) InventoryUtils.getBlockCountInInventory() > this.maxBlockSize.getValue().floatValue()) {
                    return true;
                }
            }
        }

        if (this.switchSword.getValue()) {
            int slot = (int)(this.swordSlot.getValue().floatValue() - 1.0F);
            ItemStack currentSword = (ItemStack)mc.player.getInventory().items.get(slot);
            ItemStack bestSword = InventoryUtils.getBestSword();
            ItemStack bestShapeAxe = InventoryUtils.getBestShapeAxe();
            if (InventoryUtils.getAxeDamage(bestShapeAxe) > InventoryUtils.getSwordDamage(bestSword)) {
                bestSword = bestShapeAxe;
            }
            if (bestSword != null) {
                float currentDamage = currentSword.getItem() instanceof SwordItem ? InventoryUtils.getSwordDamage(currentSword) : InventoryUtils.getAxeDamage(currentSword);
                float bestWeaponDamage = bestSword.getItem() instanceof SwordItem ? InventoryUtils.getSwordDamage(bestSword) : InventoryUtils.getAxeDamage(bestSword);
                if (bestWeaponDamage > currentDamage) {
                    return true;
                }
            }
        }

        if (this.switchPickaxe.getValue()) {
            int slot = (int)(this.pickaxeSlot.getValue().floatValue() - 1.0F);
            ItemStack bestPickaxe = InventoryUtils.getBestPickaxe();
            ItemStack currentPickaxe = (ItemStack)mc.player.getInventory().items.get(slot);
            if (bestPickaxe != null && bestPickaxe.getItem() instanceof PickaxeItem && (InventoryUtils.getToolScore(bestPickaxe) > InventoryUtils.getToolScore(currentPickaxe) || !(currentPickaxe.getItem() instanceof PickaxeItem))) {
                return true;
            }
        }

        if (this.switchAxe.getValue()) {
            int slot = (int)(this.axeSlot.getValue().floatValue() - 1.0F);
            ItemStack bestAxe = InventoryUtils.getBestAxe();
            ItemStack currentAxe = (ItemStack)mc.player.getInventory().items.get(slot);
            if (bestAxe != null && bestAxe.getItem() instanceof AxeItem && (InventoryUtils.getToolScore(bestAxe) > InventoryUtils.getToolScore(currentAxe) || !(currentAxe.getItem() instanceof AxeItem))) {
                return true;
            }
        }

        if (this.switchBow.getValue()) {
            int slot = (int)(this.bowSlot.getValue().floatValue() - 1.0F);
            ItemStack currentBow = (ItemStack)mc.player.getInventory().items.get(slot);
            ItemStack bestBow = null;
            if (this.preferBow.is("Crossbow")) {
                bestBow = InventoryUtils.getBestCrossbow();
            } else if (this.preferBow.is("Power Bow")) {
                bestBow = InventoryUtils.getBestPowerBow();
            } else {
                bestBow = InventoryUtils.getBestPunchBow();
            }
            if (bestBow != null && (!(currentBow.getItem() instanceof BowItem) && !(currentBow.getItem() instanceof CrossbowItem))) {
                return true;
            }
            if ((float)InventoryUtils.getItemCount(Items.ARROW) > this.maxArrowSize.getValue().floatValue()) {
                return true;
            }
        }

        if (this.switchWaterBucket.getValue()) {
            int slot = (int)(this.waterBucketSlot.getValue().floatValue() - 1.0F);
            ItemStack currentWaterBucket = (ItemStack)mc.player.getInventory().items.get(slot);
            int waterBucketSlot = InventoryUtils.getItemSlot(Items.WATER_BUCKET);
            if (waterBucketSlot != -1 && currentWaterBucket.getItem() != Items.WATER_BUCKET) {
                return true;
            }
        }

        if (this.switchEnderPearl.getValue()) {
            int slot = (int)(this.enderPearlSlot.getValue().floatValue() - 1.0F);
            ItemStack currentEnderPearl = (ItemStack)mc.player.getInventory().items.get(slot);
            int enderPearlSlot = InventoryUtils.getItemSlot(Items.ENDER_PEARL);
            if (enderPearlSlot != -1 && currentEnderPearl.getItem() != Items.ENDER_PEARL) {
                return true;
            }
        }

        if (this.switchFireball.getValue()) {
            int slot = (int)(this.fireballSlot.getValue().floatValue() - 1.0F);
            ItemStack currentFireball = (ItemStack)mc.player.getInventory().items.get(slot);
            int fireballSlot = InventoryUtils.getItemSlot(Items.FIRE_CHARGE);
            if (fireballSlot != -1 && currentFireball.getItem() != Items.FIRE_CHARGE) {
                return true;
            }
        }

        if (this.switchProjectile.getValue()) {
            int slot = (int)(this.projectileSlot.getValue().floatValue() - 1.0F);
            ItemStack currentProjectile = (ItemStack)mc.player.getInventory().items.get(slot);
            ItemStack bestProjectile = InventoryUtils.getBestProjectile();
            if (bestProjectile != null && (currentProjectile.getItem() != Items.EGG && currentProjectile.getItem() != Items.SNOWBALL)) {
                return true;
            }
            int totalProjectiles = InventoryUtils.getItemCount(Items.EGG) + InventoryUtils.getItemCount(Items.SNOWBALL);
            if ((float)totalProjectiles > this.maxProjectileSize.getValue().floatValue()) {
                return true;
            }
        }

        if (this.switchRod.getValue()) {
            int slot = (int)(this.rodSlot.getValue().floatValue() - 1.0F);
            ItemStack currentRod = (ItemStack)mc.player.getInventory().items.get(slot);
            int rodSlot = InventoryUtils.getItemSlot(Items.FISHING_ROD);
            if (rodSlot != -1 && currentRod.getItem() != Items.FISHING_ROD) {
                return true;
            }
        }

        if (this.throwItems.getValue()) {
            for(int i = 0; i < mc.player.getInventory().items.size(); ++i) {
                ItemStack stack = (ItemStack)mc.player.getInventory().items.get(i);
                if (!stack.isEmpty() && !this.isItemUseful(stack)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isItemUseful(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        } else if (InventoryUtils.isGodItem(stack)) {
            return true;
        } else if (stack.getDisplayName().getString().contains("\u70b9\u51fb\u4f7f\u7528")) {
            return true;
        } else if (stack.getItem() instanceof ArmorItem) {
            ArmorItem item = (ArmorItem)stack.getItem();
            float protection = InventoryUtils.getProtection(stack);
            if (InventoryUtils.getCurrentArmorScore(item.getEquipmentSlot()) >= protection) {
                return false;
            } else {
                float bestArmor = InventoryUtils.getBestArmorScore(item.getEquipmentSlot());
                return !(protection < bestArmor);
            }
        } else if (stack.getItem() instanceof SwordItem) {
            return InventoryUtils.getBestSword() == stack;
        } else if (stack.getItem() instanceof PickaxeItem) {
            return InventoryUtils.getBestPickaxe() == stack;
        } else if (stack.getItem() instanceof AxeItem && !InventoryUtils.isSharpnessAxe(stack)) {
            return InventoryUtils.getBestAxe() == stack;
        } else if (stack.getItem() instanceof ShovelItem) {
            return InventoryUtils.getBestShovel() == stack;
        } else if (stack.getItem() instanceof CrossbowItem) {
            return InventoryUtils.getBestCrossbow() == stack;
        } else if (stack.getItem() instanceof BowItem && InventoryUtils.isPunchBow(stack)) {
            return InventoryUtils.getBestPunchBow() == stack;
        } else if (stack.getItem() instanceof BowItem && InventoryUtils.isPowerBow(stack)) {
            return InventoryUtils.getBestPowerBow() == stack;
        } else if (stack.getItem() instanceof BowItem && InventoryUtils.getItemCount(Items.BOW) > 1) {
            return false;
        } else if (stack.getItem() == Items.WATER_BUCKET && InventoryUtils.getItemCount(Items.WATER_BUCKET) > waterBucketCount.getValue().intValue()) {
            return false;
        } else if (stack.getItem() == Items.LAVA_BUCKET && InventoryUtils.getItemCount(Items.LAVA_BUCKET) > lavaBucketCount.getValue().intValue()) {
            return false;
        } else if (stack.getItem() instanceof FishingRodItem && InventoryUtils.getItemCount(Items.FISHING_ROD) > 1) {
            return false;
        } else if ((stack.getItem() == Items.SNOWBALL || stack.getItem() == Items.EGG) && !keepProjectile.getValue()) {
            return false;
        } else {
            return stack.getItem() instanceof ItemNameBlockItem ? false : InventoryUtils.isCommonItemUseful(stack);
        }
    }

    private void swapOffHand(int slot) {
        if (slot < 9) {
            clickSlot(mc.player.inventoryMenu.containerId, slot + 36, 40, ClickType.SWAP, mc.player);
        } else {
            clickSlot(mc.player.inventoryMenu.containerId, slot, 40, ClickType.SWAP, mc.player);
        }

        inventoryOpen = true;
    }

    private void throwItem(ItemStack item) {
        if (InventoryUtils.isItemValid(item)) {
            int itemSlot = InventoryUtils.getItemStackSlot(item);
            if (itemSlot != -1) {
                if (itemSlot < 9) {
                    clickSlot(mc.player.inventoryMenu.containerId, itemSlot + 36, 1, ClickType.THROW, mc.player);
                } else {
                    clickSlot(mc.player.inventoryMenu.containerId, itemSlot, 1, ClickType.THROW, mc.player);
                }

                inventoryOpen = true;
            }
        }

    }

    private void swapItem(int targetSlot, ItemStack bestItem) {
        ItemStack currentSlot = (ItemStack)mc.player.getInventory().items.get(targetSlot);
        if (InventoryUtils.isItemValid(currentSlot) && bestItem != currentSlot) {
            int bestItemSlot = InventoryUtils.getItemStackSlot(bestItem);
            if (bestItemSlot != -1) {
                if (bestItemSlot < 9) {
                    clickSlot(mc.player.inventoryMenu.containerId, bestItemSlot + 36, targetSlot, ClickType.SWAP, mc.player);
                } else {
                    clickSlot(mc.player.inventoryMenu.containerId, bestItemSlot, targetSlot, ClickType.SWAP, mc.player);
                }

                inventoryOpen = true;
            }
        }

    }

    private void swapItem(int targetSlot, Item item) {
        ItemStack currentSlot = (ItemStack)mc.player.getInventory().items.get(targetSlot);
        if (InventoryUtils.isItemValid(currentSlot)) {
            int bestItemSlot = InventoryUtils.getItemSlot(item);
            if (bestItemSlot != -1) {
                ItemStack bestItemStack = (ItemStack)mc.player.getInventory().items.get(bestItemSlot);
                if (currentSlot.getItem() != item || currentSlot.getItem() == item && currentSlot.getCount() < bestItemStack.getCount()) {
                    if (bestItemSlot < 9) {
                        clickSlot(mc.player.inventoryMenu.containerId, bestItemSlot + 36, targetSlot, ClickType.SWAP, mc.player);
                    } else {
                        clickSlot(mc.player.inventoryMenu.containerId, bestItemSlot, targetSlot, ClickType.SWAP, mc.player);
                    }

                    inventoryOpen = true;
                }
            }
        }

    }
    @EventTarget
    public void onPreUpdate(EventTickUpdate e) {
        if (stopSprint) {
            mc.options.keySprint.setDown(false);
            mc.player.setSprinting(false);
        }
    }
    @EventTarget
    public void onMoveInput(EventMoveInput e) {
        if (stopSprint) {
            mc.options.keySprint.setDown(false);
            mc.player.setSprinting(false);
        }
    }
    private void clickSlotA(int syncId, int slotId, int button, ClickType actionType, LocalPlayer player) {
        stopSprint = true;
        if (mc.player == null) return;
        if (timer.hasTimeElapsed(delay.getValue().longValue())) {
            boolean isSprinting = ((LocalPlayerAccessor)mc.player).isWasSprinting();
            if (!inventoryOnly.getValue() && isSprinting) return;
            if (isSprinting) {
                PacketUtils.sendPacket(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
                GameStateManager.handleInventoryMouseClick(syncId, slotId, button, actionType, player);
                PacketUtils.sendPacket(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_SPRINTING));
            } else {
                GameStateManager.handleInventoryMouseClick(syncId, slotId, button, actionType, player);
            }
            active = true;
            if (!inventoryOnly.getValue()) {
                isSprinting = ((LocalPlayerAccessor)mc.player).isWasSprinting();
                if (isSprinting) {
                    PacketUtils.sendPacket(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
                    PacketUtils.sendPacket(new ServerboundContainerClosePacket(mc.player.inventoryMenu.containerId));
                    PacketUtils.sendPacket(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_SPRINTING));
                }else {
                    PacketUtils.sendPacket(new ServerboundContainerClosePacket(mc.player.inventoryMenu.containerId));
                }
            }
            timer.reset();
        }

    }
    private void clickSlot(int syncId, int slotId, int button, ClickType actionType, LocalPlayer player) {
        stopSprint = true;
        if (mc.player == null) return;
        if (timer.hasTimeElapsed(delay.getValue().longValue())) {
            if (actionType == ClickType.THROW && fastdrop.getValue()) {
                mc.gameMode.handleInventoryMouseClick(syncId, slotId, button, actionType, player);
                return;
            }
            boolean isSprinting = ((LocalPlayerAccessor)mc.player).isWasSprinting();
            if (!inventoryOnly.getValue() && isSprinting) return;
            if (isSprinting) {
                PacketUtils.sendPacket(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
                mc.gameMode.handleInventoryMouseClick(syncId, slotId, button, actionType, player);
                PacketUtils.sendPacket(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_SPRINTING));
            } else {
                mc.gameMode.handleInventoryMouseClick(syncId, slotId, button, actionType, player);
            }
            active = true;
            if (!inventoryOnly.getValue()) {
                isSprinting = ((LocalPlayerAccessor)mc.player).isWasSprinting();
                if (isSprinting) {
                    PacketUtils.sendPacket(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
                    PacketUtils.sendPacket(new ServerboundContainerClosePacket(mc.player.inventoryMenu.containerId));
                    PacketUtils.sendPacket(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_SPRINTING));
                }else {
                    PacketUtils.sendPacket(new ServerboundContainerClosePacket(mc.player.inventoryMenu.containerId));
                }
            }
            timer.reset();
        }

    }
}