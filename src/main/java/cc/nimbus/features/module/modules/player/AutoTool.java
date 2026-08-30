package cc.nimbus.features.module.modules.player;

import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.game.EventTick;
import cc.nimbus.event.impl.game.EventWorldLoad;
import cc.nimbus.event.impl.player.EventAttack;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.BooleanSetting;
import cc.nimbus.features.module.api.setting.impl.NumberSetting;
import cc.nimbus.utils.entity.player.InventoryUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.phys.BlockHitResult;

@ModuleInfo(name = "AutoTool", description = "Selects the most effective tool while mining.", category = ModuleCategory.Player)
public final class AutoTool extends BasicModule {
    public final BooleanSetting autoWeapon = new BooleanSetting("Auto Weapon", false);
    public final BooleanSetting checkGoldenAxe = new BooleanSetting("Check Golden Axe", true);
    public final BooleanSetting spoofItem = new BooleanSetting("Spoof Item", true);
    public final NumberSetting returnDelay = new NumberSetting("Return Delay", 2, 0, 10, 1);

    private final Minecraft minecraft = Minecraft.getInstance();
    private int originalSlot = -1;
    private int restoreTicks;

    @Override protected void onEnable() { reset(); }
    @Override protected void onDisable() { restore(); reset(); }

    @EventTarget private void onWorldLoad(EventWorldLoad event) { restore(); reset(); }

    @EventTarget private void onTick(EventTick event) {
        if (event.getPhase() != EventTick.Phase.PRE || minecraft.player == null || minecraft.level == null
                || minecraft.gameMode == null) return;
        if (!minecraft.gameMode.isDestroying() || !(minecraft.hitResult instanceof BlockHitResult hit)) {
            if (originalSlot != -1) {
                if (++restoreTicks >= returnDelay.getValue().intValue()) { restore(); reset(); }
            }
            return;
        }
        if (originalSlot == -1) originalSlot = minecraft.player.getInventory().selected;
        restoreTicks = 0;
        int selected = findBestTool(hit.getBlockPos());
        if (selected != -1) {
            minecraft.player.getInventory().selected = (selected);
        }
    }

    @EventTarget private void onAttack(EventAttack event) {
        if (event.getStage() == EventAttack.Stage.PRE && autoWeapon.getValue()) equipBestWeapon();
    }

    public void equipBestWeapon() {
        if (minecraft.player == null) return;
        int selected = minecraft.player.getInventory().selected;
        double score = Double.NEGATIVE_INFINITY;
        double bestScore = -1.0D;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = minecraft.player.getInventory().getItem(i);
            if (!stack.is(ItemTags.SWORDS)) continue;
            if (checkGoldenAxe.getValue() && stack.getItem() instanceof AxeItem
                    && stack.getHoverName().getString().contains("一刀999")) continue;
            if (stack.getItem() instanceof SwordItem) {
                score = InventoryUtils.getSwordDamage(stack);
            } else if (stack.getItem() instanceof AxeItem) {
                score = InventoryUtils.getAxeDamage(stack);
            } else {
                continue;
            }

            if (score > bestScore) {
                bestScore = score;
                score = i;
            }
        }
        if (score > Double.NEGATIVE_INFINITY) minecraft.player.getInventory().selected = (selected);
    }

    public ItemStack getSpoofStack() {
        if (!spoofItem.getValue() || originalSlot < 0 || minecraft.player == null) return null;
        ItemStack stack = minecraft.player.getInventory().getItem(originalSlot);
        return stack.isEmpty() ? null : stack;
    }

    private int findBestTool(BlockPos pos) {
        double best = 1.0;
        int slot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = minecraft.player.getInventory().getItem(i);
            if (checkGoldenAxe.getValue() && stack.getItem() instanceof AxeItem
                    && stack.getHoverName().getString().contains("一刀999")) continue;
            float speed = stack.getDestroySpeed(minecraft.level.getBlockState(pos));
            if (speed > best) { best = speed; slot = i; }
        }
        return slot;
    }

    private void restore() {
        if (minecraft.player != null && originalSlot >= 0) minecraft.player.getInventory().selected = (originalSlot);
    }
    private void reset() { originalSlot = -1; restoreTicks = 0; }
}
