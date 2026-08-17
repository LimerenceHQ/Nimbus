package cc.nimbus.features.module.modules.player;

import cc.nimbus.mixin.accessor.client.MinecraftAccessor;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.game.EventTick;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.BooleanSetting;
import cc.nimbus.features.module.api.setting.impl.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.BedItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.HitResult;

@ModuleInfo(name = "FastPlace", description = "Reduces the client block placement delay.", category = ModuleCategory.Player)
public final class FastPlace extends BasicModule {
    public final NumberSetting delay = new NumberSetting("Delay", 0, 0, 3, 1);
    public final BooleanSetting noBed = new BooleanSetting("No Bed", true);
    public final BooleanSetting disableOnWeb = new BooleanSetting("Disable On Web", true);
    private final Minecraft minecraft = Minecraft.getInstance();
    private int originalDelay;

    @Override protected void onEnable() { originalDelay = ((MinecraftAccessor) minecraft).nimbus$getRightClickDelay(); }
    @Override protected void onDisable() { ((MinecraftAccessor) minecraft).nimbus$setRightClickDelay(originalDelay); }

    @EventTarget private void onTick(EventTick event) {
        if (event.getPhase() != EventTick.Phase.PRE || minecraft.player == null || minecraft.level == null) return;
        if (disableOnWeb.getValue() && (minecraft.player.getMainHandItem().is(Items.COBWEB)
                || minecraft.player.getOffhandItem().is(Items.COBWEB))) return;
        boolean valid = valid(minecraft.player.getMainHandItem()) || valid(minecraft.player.getOffhandItem());
        if (valid && minecraft.hitResult != null && minecraft.hitResult.getType() == HitResult.Type.BLOCK) {
            ((MinecraftAccessor) minecraft).nimbus$setRightClickDelay(delay.getValue().intValue());
        }
    }

    private boolean valid(net.minecraft.world.item.ItemStack stack) {
        return stack.getItem() instanceof BlockItem && (!noBed.getValue() || !(stack.getItem() instanceof BedItem));
    }
}
