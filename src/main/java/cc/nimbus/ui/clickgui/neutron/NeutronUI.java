package cc.nimbus.ui.clickgui.neutron;

import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.ui.clickgui.neutron.panel.NeutronWindowPanel;
import cc.nimbus.utils.client.input.Keyboard;
import io.github.humbleui.skija.Canvas;
import org.lwjgl.glfw.GLFW;

public class NeutronUI {

    private final NeutronState state = new NeutronState();
    private final NeutronActions actions = new NeutronActions(state);
    private final NeutronWindowPanel window = new NeutronWindowPanel(state, actions);

    private boolean lastLeftDown;
    private boolean lastRightDown;

    public void render(String title, Canvas canvas, int width, int height, float mouseX, float mouseY,
                       boolean leftDown, boolean rightDown, boolean leftClicked, boolean rightClicked, double scrolledDelta) {
        boolean leftPressed = leftDown && !lastLeftDown;
        lastLeftDown = leftDown;
        state.rightPressed = rightDown && !lastRightDown;

        state.rightDown = rightDown;
        lastRightDown = rightDown;
        window.setBounds(0f, 0f, width, height);
        window.render(canvas, mouseX, mouseY, leftDown, rightDown, leftPressed, scrolledDelta);
    }

    public boolean handleKeyInput(int key, int scanCode, int modifiers) {
        BasicModule binding = state.bindingModule;
        if (binding != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                binding.changeKey = false;
                state.bindingModule = null;
                actions.showToast("已取消绑定：" + binding.getDisplayName());
                return true;
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                binding.setKey(0);
                binding.changeKey = false;
                state.bindingModule = null;
                actions.showToast("已清空绑定：" + binding.getDisplayName());
                return true;
            }
            if (key > 0) {
                binding.setKey(key);
                binding.changeKey = false;
                state.bindingModule = null;
                actions.showToast("已绑定：" + binding.getDisplayName() + " -> " + Keyboard.getKeyName(key));
                return true;
            }
            return true;
        }
        if (state.textFocused) {
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                actions.backspaceText();
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                actions.cancelText();
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                actions.commitText();
                return true;
            }
        }
        if (state.numberFocused) {
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                actions.backspaceNumber();
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                actions.cancelNumber();
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                actions.commitNumber();
                return true;
            }
        }
        if (state.searchFocused) {
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                actions.backspaceSearch();
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                actions.setSearchFocused(false);
                return false;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                return true;
            }
        }
        if (window.handleKeyInput(key, scanCode, modifiers)) {
            return true;
        }
        return false;
    }

    public boolean handleCharInput(char codePoint, int modifiers) {
        if (state.textFocused) {
            if (Character.isISOControl(codePoint)) return false;
            actions.appendTextChar(codePoint);
            return true;
        }
        if (state.numberFocused) {
            if (Character.isISOControl(codePoint)) return false;
            actions.appendNumberChar(codePoint);
            return true;
        }
        if (state.searchFocused) {
            if (Character.isISOControl(codePoint)) return false;
            actions.appendSearchChar(codePoint);
            return true;
        }
        if (window.handleCharInput(codePoint, modifiers)) {
            return true;
        }
        return false;
    }

    public boolean middleClick(float mx, float my) {
        return window.middleClick(mx, my);
    }
}
