package cc.nimbus.utils.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

public final class MessageUtils {
    private static final char FORMAT_PREFIX = '§';
    private static final String PREFIX = "§b[Nimbus] §7";

    private MessageUtils() {}

    public static void addMessage(String message) { add(format(PREFIX + valueOrEmpty(message))); }
    public static void addMessageWithoutPerfix(String message) { add(format(valueOrEmpty(message))); }
    public static void addMessageWithoutPrefix(String message) { addMessageWithoutPerfix(message); }

    private static void add(Component message) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.schedule(() -> {
            if (minecraft.gui != null && minecraft.gui.hud != null) {
                minecraft.gui.hud.getChat().addClientSystemMessage(message);
            }
        });
    }

    static Component format(String value) {
        MutableComponent result = Component.empty();
        StringBuilder segment = new StringBuilder();
        Style style = Style.EMPTY;

        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current != FORMAT_PREFIX || index + 1 >= value.length()) {
                segment.append(current);
                continue;
            }

            char code = value.charAt(index + 1);
            if (code == FORMAT_PREFIX) {
                segment.append(FORMAT_PREFIX);
                index++;
                continue;
            }

            ChatFormatting formatting = ChatFormatting.getByCode(code);
            if (formatting == null) {
                segment.append(current);
                continue;
            }

            append(result, segment, style);
            style = formatting == ChatFormatting.RESET
                    ? Style.EMPTY
                    : style.applyLegacyFormat(formatting);
            index++;
        }

        append(result, segment, style);
        return result;
    }

    private static void append(MutableComponent result, StringBuilder segment, Style style) {
        if (segment.isEmpty()) return;
        result.append(Component.literal(segment.toString()).setStyle(style));
        segment.setLength(0);
    }

    private static String valueOrEmpty(String value) { return value == null ? "" : value; }
}
