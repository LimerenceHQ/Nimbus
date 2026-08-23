package cc.nimbus.ui.notification;

public enum NotificationType {
    SUCCESS("Success", 0xFF96C077),
    INFO("Info", 0xFFFFFFFF),
    WARNING("Warning", 0xFFFFD54F),
    ERROR("Error", 0xFFE06C75);

    private final String name;
    private final int color;

    NotificationType(String name, int color) {
        this.name = name;
        this.color = color;
    }

    public String getName() {
        return name;
    }

    public int getColor() {
        return color;
    }
}
