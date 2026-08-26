package cc.nimbus.features.module.modules.player;

import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.game.EventScreenChange;
import cc.nimbus.event.impl.game.EventTick;
import cc.nimbus.event.impl.game.EventWorldLoad;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.BooleanSetting;
import cc.nimbus.features.module.api.setting.impl.MultiSelectSetting;
import cc.nimbus.features.module.api.setting.impl.NumberSetting;
import cc.nimbus.features.module.api.setting.impl.modes.Modes;
import cc.nimbus.utils.client.TimerUtils;
import cc.nimbus.utils.entity.player.InventoryUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.*;
import net.minecraft.world.inventory.*;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@ModuleInfo(name = "ContainerStealer", description = "Collects useful items from supported containers.", category = ModuleCategory.Player)
public final class ContainerStealer extends BasicModule {
    public final MultiSelectSetting containers = new MultiSelectSetting("Containers",
            new Modes("Chest", "Furnace", "Blast Furnace", "Smoker", "Brewing Stand"),
            java.util.Set.of());
    public final NumberSetting openDelay = new NumberSetting("Open Delay", 50, 0, 1000, 10);
    public final NumberSetting stealDelay = new NumberSetting("Steal Delay", 0, 0, 1000, 10);
    public final NumberSetting stackDelay = new NumberSetting("Steal Delay Stack", 0, 0, 1000, 10);
    public final NumberSetting closeDelay = new NumberSetting("Close Delay", 50, 0, 1000, 10);
    public final BooleanSetting swap = new BooleanSetting("Swap", false);
    public final BooleanSetting noGui = new BooleanSetting("No Gui Container", false);
    public final BooleanSetting randomSlot = new BooleanSetting("Random Slot", false);
    public final BooleanSetting filterTrash = new BooleanSetting("Filter Trash", false);

    private final Minecraft minecraft = Minecraft.getInstance();
    private boolean active;
    private final List<Integer> order = new ArrayList<>();
    private final TimerUtils openDelayTimer = new TimerUtils();
    private final TimerUtils stealDelayTimer = new TimerUtils();
    private final TimerUtils stackDelayTimer = new TimerUtils();
    private final TimerUtils closeDelayTimer = new TimerUtils();

    @Override protected void onEnable() { reset(); }
    @Override protected void onDisable() { reset(); }
    @EventTarget
    private void onWorldLoad(EventWorldLoad event) { reset(); }

    @EventTarget private void onScreen(EventScreenChange event) {
        if (event.getScreen() != null && noGui.getValue() && supportedScreen(event.getScreen())) event.cancelEvent();
    }

    @EventTarget private void onTick(EventTick event) {
        if (event.getPhase() != EventTick.Phase.PRE || minecraft.player == null || minecraft.gameMode == null) return;
        AbstractContainerMenu menu = minecraft.player.containerMenu;
        int containerSlots = supportedSlots(menu);
        if (containerSlots <= 0) { reset(); return; }
        if (!active) { active = true; stealDelayTimer.reset(); stackDelayTimer.reset(); closeDelayTimer.reset(); rebuild(containerSlots); return; }
        if (!openDelayTimer.hasTimeElapsed(openDelay.getValue().longValue(), true)) return;
        if (order.size() != containerSlots) rebuild(containerSlots);
        int selected = -1;
        for (int index : order) {
            ItemStack stack = menu.getSlot(index).getItem();
            if (!useful(stack) || !bestInContainer(menu, index, stack)) continue;
            selected = index; break;
        }
        if (selected < 0) {
            if (closeDelayTimer.hasTimeElapsed(closeDelay.getValue().longValue(), true)) minecraft.player.closeContainer();
            return;
        }
        ItemStack stack = menu.getSlot(selected).getItem();
        if (!stealDelayTimer.hasTimeElapsed(stackDelay.getValue().longValue(), true)) return;
        if (swap.getValue() && stack.getMaxStackSize() > 1) {
            if (!stackDelayTimer.hasTimeElapsed(stealDelay.getValue().longValue(), true)) return;
        }
        if (swap.getValue() && stack.getMaxStackSize() <= 1) {
            int empty = firstEmptyInventorySlot();
            if (empty >= 0 && empty < 9) click(menu, selected, empty, ClickType.SWAP);
            else click(menu, selected, 0, ClickType.QUICK_MOVE);
        } else click(menu, selected, 0, ClickType.QUICK_MOVE);
        closeDelayTimer.reset();
    }

    private void click(AbstractContainerMenu menu, int slot, int button, ClickType type) {
        if (minecraft.gameMode != null && minecraft.player != null)
            minecraft.gameMode.handleInventoryMouseClick(menu.containerId, slot, button, type, minecraft.player);
    }

    private boolean useful(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (!InventoryUtils.hasValidProfileComponent(stack)) return false;
        return !filterTrash.getValue() || !InventoryUtils.notUsefulItem(stack);
    }

    private boolean bestInContainer(AbstractContainerMenu menu, int slot, ItemStack stack) {
        double score = score(stack);
        for (int i = 0; i < supportedSlots(menu); i++) {
            if (i == slot) continue;
            ItemStack other = menu.getSlot(i).getItem();
            if (other.isEmpty() || !sameFamily(stack, other)) continue;
            if (score(other) > score) return false;
        }
        return true;
    }

    private boolean sameFamily(ItemStack a, ItemStack b) {
        if (InventoryUtils.armorSlot(a) != null && InventoryUtils.armorSlot(b) != null)
            return InventoryUtils.armorSlot(a) == InventoryUtils.armorSlot(b);
        return (a.getItem() instanceof BowItem && b.getItem() instanceof BowItem)
                || (a.getItem() instanceof CrossbowItem && b.getItem() instanceof CrossbowItem)
                || (a.is(ItemTags.SWORDS) && b.is(ItemTags.SWORDS))
                || (a.is(ItemTags.AXES) && b.is(ItemTags.AXES))
                || (a.is(ItemTags.PICKAXES) && b.is(ItemTags.PICKAXES))
                || (a.is(ItemTags.SHOVELS) && b.is(ItemTags.SHOVELS));
    }

    private double score(ItemStack stack) {
        if (InventoryUtils.armorSlot(stack) != null) return InventoryUtils.armorScore(stack);
        if (stack.getItem() instanceof BowItem) return InventoryUtils.bowScore(stack);
        if (stack.getItem() instanceof CrossbowItem) return InventoryUtils.crossbowScore(stack);
        if (stack.is(ItemTags.SWORDS)) return InventoryUtils.weaponScore(stack);
        if (stack.is(ItemTags.AXES)) return Math.max(InventoryUtils.weaponScore(stack), InventoryUtils.axeScore(stack));
        if (stack.is(ItemTags.PICKAXES)) return Math.max(InventoryUtils.weaponScore(stack), InventoryUtils.pickaxeScore(stack));
        if (stack.is(ItemTags.SHOVELS)) return Math.max(InventoryUtils.weaponScore(stack), InventoryUtils.shovelScore(stack));
        return 0.0;
    }

    private int supportedSlots(AbstractContainerMenu menu) {
        if (menu instanceof ChestMenu chest && containers.isSelected("Chest")) return chest.getRowCount() * 9;
        if (menu instanceof FurnaceMenu && containers.isSelected("Furnace")) return 3;
        if (menu instanceof BlastFurnaceMenu && containers.isSelected("Blast Furnace")) return 3;
        if (menu instanceof SmokerMenu && containers.isSelected("Smoker")) return 3;
        if (menu instanceof BrewingStandMenu && containers.isSelected("Brewing Stand")) return 5;
        return 0;
    }

    private boolean supportedScreen(Screen screen) {
        return (screen instanceof ContainerScreen && containers.isSelected("Chest"))
                || (screen instanceof FurnaceScreen && containers.isSelected("Furnace"))
                || (screen instanceof BlastFurnaceScreen && containers.isSelected("Blast Furnace"))
                || (screen instanceof SmokerScreen && containers.isSelected("Smoker"))
                || (screen instanceof BrewingStandScreen && containers.isSelected("Brewing Stand"));
    }

    private int firstEmptyInventorySlot() {
        if (minecraft.player == null) return -1;
        for (int i = 0; i < 36; i++) if (minecraft.player.getInventory().getItem(i).isEmpty()) return i;
        return -1;
    }
    private void rebuild(int size) { order.clear(); for (int i = 0; i < size; i++) order.add(i); if (randomSlot.getValue()) Collections.shuffle(order); }
    private void reset() { active = false; stackDelayTimer.reset(); stealDelayTimer.reset(); openDelayTimer.reset(); closeDelayTimer.reset(); order.clear(); }
}
