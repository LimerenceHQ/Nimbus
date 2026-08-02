package cc.nimbus.ui.clickgui.tool;

import cc.nimbus.ui.clickgui.component.UiContext;
import cc.nimbus.ui.clickgui.component.UiRect;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.PreeditEvent;

public interface ToolPage {
    default void opened() {
    }

    void render(UiContext context, UiRect bounds);

    boolean mouseClicked(float x, float y, int button);

    default void mouseMoved(float x, float y, boolean leftDown) {
    }

    default void mouseReleased() {
    }

    default boolean mouseScrolled(float x, float y, double delta) {
        return false;
    }

    default boolean keyPressed(KeyEvent event) {
        return false;
    }

    default boolean charTyped(CharacterEvent event) {
        return false;
    }

    default boolean preeditUpdated(PreeditEvent event) {
        return false;
    }

    default boolean cancelTransient() {
        return false;
    }

    default void closed() {
    }
}
