package cc.nimbus.ui.proxy;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** A normal keyboard button that distinguishes a short mouse click from a deliberate hold. */
public final class ProxyHoldButton extends Button {
    private static final long HOLD_NANOS = 450_000_000L;

    private final Runnable shortAction;
    private final Runnable longAction;
    private long pressedAt;
    private boolean longTriggered;

    public ProxyHoldButton(int x, int y, int width, int height, Component message,
                           Runnable shortAction, Runnable longAction) {
        super(x, y, width, height, message, ignored -> {}, DEFAULT_NARRATION);
        this.shortAction = shortAction;
        this.longAction = longAction;
    }

    @Override
    public void onPress() {
        shortAction.run();
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        pressedAt = System.nanoTime();
        longTriggered = false;
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        if (pressedAt == 0L) return;
        if (!longTriggered) shortAction.run();
        pressedAt = 0L;
        longTriggered = false;
    }

    public void tickHold() {
        if (pressedAt == 0L || longTriggered || System.nanoTime() - pressedAt < HOLD_NANOS) return;
        longTriggered = true;
        longAction.run();
    }
}
