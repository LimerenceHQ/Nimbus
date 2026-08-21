package cc.nimbus.features.module.modules.player;

import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.game.EventTick;
import cc.nimbus.event.impl.game.EventWorldLoad;
import cc.nimbus.event.impl.input.EventMouseButton;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.submodule.ModeValue;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

@ModuleInfo(
        name = "MidPearl",
        displayName = "Mid Pearl",
        description = "Throws a hotbar ender pearl with a configurable mouse button.",
        category = ModuleCategory.Player
)
public final class MidPearl extends BasicModule {
    public final ModeValue keyMode = new ModeValue(
            "Key Mode", new String[]{"Middle", "Mouse3", "Mouse4"}, "Middle");

    private final Minecraft minecraft = Minecraft.getInstance();
    private int originalSlot = -1;
    private int pearlSlot = -1;
    private int activeButton = -1;
    private boolean holdingPearl;
    private boolean awaitingRelease;
    private boolean pressPending;
    private boolean releasePending;
    private int restoreDelayTicks;

    @Override
    protected void onEnable() {
        reset(false);
    }

    @Override
    protected void onDisable() {
        reset(true);
    }

    @EventTarget
    private void onWorldLoad(EventWorldLoad event) {
        reset(true);
    }

    @EventTarget
    private void onMouseButton(EventMouseButton event) {
        int configuredButton = configuredButton();
        if (event.getAction() == GLFW.GLFW_PRESS
                && event.getButton() == configuredButton
                && minecraft.screen == null) {
            activeButton = event.getButton();
            awaitingRelease = true;
            pressPending = true;
            return;
        }
        if (event.getAction() == GLFW.GLFW_RELEASE
                && awaitingRelease
                && event.getButton() == activeButton) {
            awaitingRelease = false;
            releasePending = true;
        }
    }

    @EventTarget
    private void onTick(EventTick event) {
        if (event.getPhase() != EventTick.Phase.PRE) return;
        if (minecraft.player == null || minecraft.level == null || minecraft.gameMode == null) {
            reset(false);
            return;
        }
        if (restoreDelayTicks > 0 && --restoreDelayTicks == 0) {
            reset(true);
        }
        if (pressPending) {
            pressPending = false;
            handlePress();
        }
        if (releasePending) {
            releasePending = false;
            handleRelease();
        }
    }

    public ItemStack getSpoofStack() {
        if (!holdingPearl || originalSlot < 0 || minecraft.player == null) return null;
        ItemStack original = minecraft.player.getInventory().getItem(originalSlot);
        return original.isEmpty() ? null : original;
    }

    private void handlePress() {
        if (holdingPearl) return;
        int foundSlot = findPearlSlot();
        if (foundSlot < 0) return;

        originalSlot = minecraft.player.getInventory().selected;
        pearlSlot = foundSlot;
        holdingPearl = true;
        minecraft.player.getInventory().selected = (pearlSlot);
    }

    private void handleRelease() {
        if (!holdingPearl || originalSlot < 0 || pearlSlot < 0) {
            reset(false);
            return;
        }

        try {
            if (minecraft.player.getInventory().getItem(pearlSlot).is(Items.ENDER_PEARL)) {
                minecraft.player.getInventory().selected = (pearlSlot);
                minecraft.gameMode.useItem(minecraft.player, InteractionHand.MAIN_HAND);
                minecraft.player.swing(InteractionHand.MAIN_HAND);
            }
        } finally {
            // Keep the pearl slot through the current right-click action. Restoring it in the
            // same tick creates USE_ITEM -> HELD_ITEM_CHANGE, which Grim treats as PacketOrderE.
            restoreDelayTicks = 2;
        }
    }

    private int findPearlSlot() {
        for (int slot = 0; slot < 9; slot++) {
            if (minecraft.player.getInventory().getItem(slot).is(Items.ENDER_PEARL)) return slot;
        }
        return -1;
    }

    private int configuredButton() {
        return switch (keyMode.getValue()) {
            case "Mouse3" -> GLFW.GLFW_MOUSE_BUTTON_4;
            case "Mouse4" -> GLFW.GLFW_MOUSE_BUTTON_5;
            default -> GLFW.GLFW_MOUSE_BUTTON_MIDDLE;
        };
    }

    private void reset(boolean restoreSlot) {
        if (restoreSlot && originalSlot >= 0 && minecraft.player != null) {
            minecraft.player.getInventory().selected = (originalSlot);
        }
        originalSlot = -1;
        pearlSlot = -1;
        activeButton = -1;
        holdingPearl = false;
        awaitingRelease = false;
        pressPending = false;
        releasePending = false;
        restoreDelayTicks = 0;
    }
}
