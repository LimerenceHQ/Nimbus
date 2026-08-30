package cc.nimbus.features.module.modules.player;

import cc.nimbus.core.Core;
import cc.nimbus.event.EventType;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.component.Priority;
import cc.nimbus.event.impl.game.EventTick;
import cc.nimbus.event.impl.player.EventMotion;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.BooleanSetting;
import cc.nimbus.features.module.api.setting.impl.NumberSetting;
import cc.nimbus.features.rotation.Rotation;
import cc.nimbus.managers.GameStateManager;
import cc.nimbus.utils.client.BlockUtils;
import cc.nimbus.utils.client.TimerUtils;
import cc.nimbus.utils.entity.player.ChunkUtils;
import cc.nimbus.utils.entity.player.InventoryUtils;
import cc.nimbus.utils.entity.player.MovementUtils;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
@ModuleInfo(name = "ContainerStealer", category = ModuleCategory.Player)
public class ContainerStealer extends BasicModule {
    private static final TimerUtils timer = new TimerUtils();
    private static final TimerUtils timer2 = new TimerUtils();
    private static final TimerUtils startTimer = new TimerUtils();
    private static final TimerUtils stopTimer = new TimerUtils();

    private final BooleanSetting swap = new BooleanSetting( "Instant", false);
    private final NumberSetting startDelay = new NumberSetting("Start Delay", 350, 0, 2000, 1);
    private final NumberSetting stopDelay = new NumberSetting("Stop Delay", 350, 0, 2000, 1);
    private final NumberSetting delay = new NumberSetting("Delay", 350, 0, 2000, 1);
    private final NumberSetting delay1 = new NumberSetting("Multi Stack Delay",350, 0, 2000, 1);
    private final BooleanSetting pickEnderChest = new BooleanSetting("Ender Chest", false);
    private Screen lastTickScreen;

    public static boolean isWorking() {
        return !timer.hasTimeElapsed(3);
    }

    @Override
    protected void onEnable() {

    }

    @Override
    protected void onDisable() {

    }

    @EventTarget(priority = Priority.High)
    private void onMotion(EventTick e) {
        if (mc.player == null || mc.gameMode == null) return;
        this.setSuffix(delay.getValue());
        if (e.isPre()) {
            Screen currentScreen = mc.screen;
            if (currentScreen instanceof ContainerScreen container) {
                ChestMenu menu = container.getMenu();
                if (currentScreen != this.lastTickScreen) {
                    timer.reset();
                } else {
                    String chestTitle = container.getTitle().getString();
                    String chest = Component.translatable("container.chest").getString();
                    String largeChest = Component.translatable("container.chestDouble").getString();
                    String enderChest = Component.translatable("container.enderchest").getString();
                    if (chestTitle.equals(chest) || chestTitle.equals(largeChest) || chestTitle.equals("Chest") || this.pickEnderChest.getValue() && chestTitle.equals(enderChest)) {
                        if (this.isChestEmpty(menu) && timer.hasTimeElapsed(this.delay.getValue().longValue())) {
                            if (!stopTimer.hasTimeElapsed(stopDelay.getValue().longValue(),true)) return;
                            mc.player.closeContainer();
                        } else {
                            List<Integer> slots = IntStream.range(0, menu.getRowCount() * 9).boxed().collect(Collectors.toList());
                            Collections.shuffle(slots);
                            if (!startTimer.hasTimeElapsed(startDelay.getValue().longValue(),true)) return;
                            for(Integer pSlotId : slots) {
                                ItemStack stack = menu.getSlot(pSlotId).getItem();
                                if (isItemUseful(stack) && this.isBestItemInChest(menu, stack) && timer.hasTimeElapsed(this.delay.getValue().longValue())) {
                                    if (this.swap.getValue()) {
                                        int slot = getFirstEmptySlot();
                                        if (slot != -1 && slot + 18 < 54) {
                                            if (stack.getMaxStackSize() <= 1) {
                                                if (slot < 9) {
                                                    GameStateManager.handleInventoryMouseClick(menu.containerId, pSlotId, slot, ClickType.SWAP, mc.player);
                                                } else {
                                                    GameStateManager.handleInventoryMouseClick(menu.containerId, slot + 18, 8, ClickType.SWAP, mc.player);
                                                    GameStateManager.handleInventoryMouseClick(menu.containerId, pSlotId, 8, ClickType.SWAP, mc.player);
                                                }
                                            } else if (timer2.hasTimeElapsed(this.delay1.getValue().longValue())) {
                                                GameStateManager.handleInventoryMouseClick(menu.containerId, pSlotId, 0, ClickType.QUICK_MOVE, mc.player);
                                                timer2.reset();
                                            }
                                        } else {
                                            mc.player.closeContainer();
                                        }
                                    } else {
                                        GameStateManager.handleInventoryMouseClick(menu.containerId, pSlotId, 0, ClickType.QUICK_MOVE, mc.player);
                                    }

                                    timer.reset();
                                    break;
                                }
                            }
                        }
                    }
                }
            }

            this.lastTickScreen = currentScreen;
        }
    }

    private boolean isBestItemInChest(ChestMenu menu, ItemStack stack) {
        if (!InventoryUtils.isGodItem(stack) && !InventoryUtils.isSharpnessAxe(stack)) {
            for(int i = 0; i < menu.getRowCount() * 9; ++i) {
                ItemStack checkStack = menu.getSlot(i).getItem();
                if (stack.getItem() instanceof ArmorItem && checkStack.getItem() instanceof ArmorItem) {
                    ArmorItem item = (ArmorItem)stack.getItem();
                    ArmorItem checkItem = (ArmorItem)checkStack.getItem();
                    if (item.getEquipmentSlot() == checkItem.getEquipmentSlot() && InventoryUtils.getProtection(checkStack) > InventoryUtils.getProtection(stack)) {
                        return false;
                    }
                } else if (stack.getItem() instanceof SwordItem && checkStack.getItem() instanceof SwordItem) {
                    if (InventoryUtils.getSwordDamage(checkStack) > InventoryUtils.getSwordDamage(stack)) {
                        return false;
                    }
                } else if (stack.getItem() instanceof PickaxeItem && checkStack.getItem() instanceof PickaxeItem) {
                    if (InventoryUtils.getToolScore(checkStack) > InventoryUtils.getToolScore(stack)) {
                        return false;
                    }
                } else if (stack.getItem() instanceof AxeItem && checkStack.getItem() instanceof AxeItem) {
                    if (InventoryUtils.getToolScore(checkStack) > InventoryUtils.getToolScore(stack)) {
                        return false;
                    }
                } else if (stack.getItem() instanceof ShovelItem && checkStack.getItem() instanceof ShovelItem && InventoryUtils.getToolScore(checkStack) > InventoryUtils.getToolScore(stack)) {
                    return false;
                }
            }

            return true;
        } else {
            return true;
        }
    }

    private boolean isChestEmpty(ChestMenu menu) {
        for(int i = 0; i < menu.getRowCount() * 9; ++i) {
            ItemStack item = menu.getSlot(i).getItem();
            if (!item.isEmpty() && isItemUseful(item) && this.isBestItemInChest(menu, item)) {
                return false;
            }
        }

        return true;
    }

    private int getFirstEmptySlot() {
        Inventory inventory = mc.player.getInventory();

        for(int i = 0; i < inventory.items.size(); ++i) {
            if (i != 8 && inventory.getItem(i).isEmpty()) {
                return i;
            }
        }

        return -1;
    }

    private boolean isItemUseful(ItemStack stack) {
        InventoryCleaner inventoryCleaner = Core.INSTANCE.getModuleManager().getModule(InventoryCleaner.class);
        if (stack.isEmpty()) {
            return false;
        } else if (!InventoryUtils.isGodItem(stack) && !InventoryUtils.isSharpnessAxe(stack)) {
            if (stack.getItem() instanceof ArmorItem) {
                ArmorItem item = (ArmorItem)stack.getItem();
                float protection = InventoryUtils.getProtection(stack);
                float bestArmor = InventoryUtils.getBestArmorScore(item.getEquipmentSlot());
                return !(protection <= bestArmor);
            } else if (stack.getItem() instanceof SwordItem) {
                float damage = InventoryUtils.getSwordDamage(stack);
                float bestDamage = InventoryUtils.getBestSwordDamage();
                return !(damage <= bestDamage);
            } else if (stack.getItem() instanceof PickaxeItem) {
                float score = InventoryUtils.getToolScore(stack);
                float bestScore = InventoryUtils.getBestPickaxeScore();
                return !(score <= bestScore);
            } else if (stack.getItem() instanceof AxeItem) {
                float score = InventoryUtils.getToolScore(stack);
                float bestScore = InventoryUtils.getBestAxeScore();
                return !(score <= bestScore);
            } else if (stack.getItem() instanceof ShovelItem) {
                float score = InventoryUtils.getToolScore(stack);
                float bestScore = InventoryUtils.getBestShovelScore();
                return !(score <= bestScore);
            } else if (stack.getItem() instanceof CrossbowItem) {
                float score = InventoryUtils.getCrossbowScore(stack);
                float bestScore = InventoryUtils.getBestCrossbowScore();
                return !(score <= bestScore);
            } else if (stack.getItem() instanceof BowItem && InventoryUtils.isPunchBow(stack)) {
                float score = InventoryUtils.getPunchBowScore(stack);
                float bestScore = InventoryUtils.getBestPunchBowScore();
                return !(score <= bestScore);
            } else if (stack.getItem() instanceof BowItem && InventoryUtils.isPowerBow(stack)) {
                float score = InventoryUtils.getPowerBowScore(stack);
                float bestScore = InventoryUtils.getBestPowerBowScore();
                return !(score <= bestScore);
            } else if (stack.getItem() == Items.COMPASS) {
                return !InventoryUtils.hasItem(stack.getItem());
            } else if (stack.getItem() == Items.WATER_BUCKET && InventoryUtils.getItemCount(Items.WATER_BUCKET) >= inventoryCleaner.waterBucketCount.getValue().intValue()) {
                return false;
            } else if (stack.getItem() == Items.LAVA_BUCKET && InventoryUtils.getItemCount(Items.LAVA_BUCKET) >= inventoryCleaner.lavaBucketCount.getValue().intValue()) {
                return false;
            } else if (stack.getItem() instanceof BlockItem && InventoryUtils.isItemValid(stack) && InventoryUtils.getBlockCountInInventory() + stack.getCount() >= inventoryCleaner.maxBlockSize.getValue().intValue()) {
                return false;
            } else if (stack.getItem() == Items.ARROW && InventoryUtils.getItemCount(Items.ARROW) + stack.getCount() >= inventoryCleaner.maxArrowSize.getValue().intValue()) {
                return false;
            } else if (stack.getItem() instanceof FishingRodItem && InventoryUtils.getItemCount(Items.FISHING_ROD) >= 1) {
                return false;
            } else if (stack.getItem() != Items.SNOWBALL && stack.getItem() != Items.EGG || InventoryUtils.getItemCount(Items.SNOWBALL) + InventoryUtils.getItemCount(Items.EGG) + stack.getCount() < inventoryCleaner.maxProjectileSize.getValue().intValue() && inventoryCleaner.keepProjectile.getValue()) {
                return stack.getItem() instanceof ItemNameBlockItem ? false : InventoryUtils.isCommonItemUseful(stack);
            } else {
                return false;
            }
        } else {
            return true;
        }
    }

    private static class ChestInfo {
        BlockPos blockPos;
        Rotation screenPos;

        public BlockPos getBlockPos() {
            return this.blockPos;
        }

        public Rotation getScreenPos() {
            return this.screenPos;
        }

        public boolean equals(Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof ChestInfo)) {
                return false;
            } else {
                ChestInfo other = (ChestInfo)o;
                if (!other.canEqual(this)) {
                    return false;
                } else {
                    Object this$blockPos = this.getBlockPos();
                    Object other$blockPos = other.getBlockPos();
                    if (this$blockPos == null) {
                        if (other$blockPos != null) {
                            return false;
                        }
                    } else if (!this$blockPos.equals(other$blockPos)) {
                        return false;
                    }

                    Object this$screenPos = this.getScreenPos();
                    Object other$screenPos = other.getScreenPos();
                    if (this$screenPos == null) {
                        if (other$screenPos != null) {
                            return false;
                        }
                    } else if (!this$screenPos.equals(other$screenPos)) {
                        return false;
                    }

                    return true;
                }
            }
        }

        protected boolean canEqual(Object other) {
            return other instanceof ChestInfo;
        }

        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Object $blockPos = this.getBlockPos();
            result = result * 59 + ($blockPos == null ? 43 : $blockPos.hashCode());
            Object $screenPos = this.getScreenPos();
            result = result * 59 + ($screenPos == null ? 43 : $screenPos.hashCode());
            return result;
        }

        public String toString() {
            String var10000 = String.valueOf(this.getBlockPos());
            return "ChestStealer.ChestInfo(blockPos=" + var10000 + ", screenPos=" + String.valueOf(this.getScreenPos()) + ")";
        }

        public ChestInfo(BlockPos blockPos, Rotation screenPos) {
            this.blockPos = blockPos;
            this.screenPos = screenPos;
        }
    }
}