package cc.nimbus.utils.client.input;

import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import java.util.OptionalInt;

public final class Keyboard {
    private Keyboard() {}

    public static String getKeyName(int key) {
        if (key == 0) return "NONE";
        String glfwName = GLFW.glfwGetKeyName(key, 0);
        if (glfwName != null && !glfwName.isBlank()) return glfwName.toUpperCase(Locale.ROOT);
        if (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F25) return "F" + (key - GLFW.GLFW_KEY_F1 + 1);
        return switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> "ESCAPE";
            case GLFW.GLFW_KEY_INSERT -> "INSERT";
            case GLFW.GLFW_KEY_DELETE -> "DELETE";
            case GLFW.GLFW_KEY_ENTER -> "RETURN";
            case GLFW.GLFW_KEY_SPACE -> "SPACE";
            case GLFW.GLFW_KEY_TAB -> "TAB";
            case GLFW.GLFW_KEY_BACKSPACE -> "BACK";
            case GLFW.GLFW_KEY_LEFT -> "LEFT";
            case GLFW.GLFW_KEY_RIGHT -> "RIGHT";
            case GLFW.GLFW_KEY_UP -> "UP";
            case GLFW.GLFW_KEY_DOWN -> "DOWN";
            case GLFW.GLFW_KEY_HOME -> "HOME";
            case GLFW.GLFW_KEY_END -> "END";
            case GLFW.GLFW_KEY_PAGE_UP -> "PAGEUP";
            case GLFW.GLFW_KEY_PAGE_DOWN -> "PAGEDOWN";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "LSHIFT";
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "RSHIFT";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "LCTRL";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "RCTRL";
            case GLFW.GLFW_KEY_LEFT_ALT -> "LALT";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "RALT";
            default -> "KEY " + key;
        };
    }

    public static int getKeyIndex(String name) {
        return findKeyIndex(name).orElse(0);
    }

    public static OptionalInt findKeyIndex(String name) {
        if (name == null || name.isBlank()) return OptionalInt.empty();
        if ("NONE".equalsIgnoreCase(name) || "UNBOUND".equalsIgnoreCase(name)
                || "CLEAR".equalsIgnoreCase(name)) return OptionalInt.of(0);
        String normalized = normalizeAlias(name);
        for (int key = GLFW.GLFW_KEY_SPACE; key <= GLFW.GLFW_KEY_LAST; key++) {
            if (getKeyName(key).equalsIgnoreCase(normalized)) return OptionalInt.of(key);
        }
        try {
            int key = Integer.parseInt(normalized.replaceFirst("(?i)^KEY\\s+", ""));
            return key >= 0 && key <= GLFW.GLFW_KEY_LAST ? OptionalInt.of(key) : OptionalInt.empty();
        } catch (NumberFormatException ignored) {
            return OptionalInt.empty();
        }
    }

    private static String normalizeAlias(String name) {
        return switch (name.trim().toUpperCase(Locale.ROOT)) {
            case "ESC" -> "ESCAPE";
            case "ENTER" -> "RETURN";
            case "DEL" -> "DELETE";
            case "PGUP", "PAGE_UP" -> "PAGEUP";
            case "PGDN", "PAGE_DOWN" -> "PAGEDOWN";
            case "CTRL", "CONTROL" -> "LCTRL";
            case "SHIFT" -> "LSHIFT";
            case "ALT" -> "LALT";
            default -> name.trim();
        };
    }
}
