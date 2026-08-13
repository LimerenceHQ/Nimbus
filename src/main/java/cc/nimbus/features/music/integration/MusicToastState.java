package cc.nimbus.features.music.integration;

public final class MusicToastState {
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
        text = value == null || value.isBlank() ? null : value;
    }

    public static void clear() {
        text = null;
    }
}
