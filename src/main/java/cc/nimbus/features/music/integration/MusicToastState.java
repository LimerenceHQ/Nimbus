package cc.nimbus.features.music.integration;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

public final class MusicToastState {
    private static final SystemToast.SystemToastIds TOAST_ID = SystemToast.SystemToastIds.TUTORIAL_HINT;
    private static final Component TOAST_TITLE = Component.literal("Nimbus Music");
    private static volatile String text;

    private MusicToastState() {
    }

    public static String text() {
        return text;
    }

    public static boolean active() {
        return text != null && !text.isBlank();
    }

    public static void set(String value) {
        if (value == null || value.isBlank()) {
            clear();
            return;
        }
        text = value;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            SystemToast.addOrUpdate(minecraft.getToasts(), TOAST_ID,
                    TOAST_TITLE, Component.literal(value));
        }
    }

    public static void clear() {
        text = null;
        Minecraft minecraft = Minecraft.getInstance();
    }
}
