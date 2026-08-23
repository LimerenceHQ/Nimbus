package cc.nimbus.features.command.commands;

import cc.nimbus.core.Core;
import cc.nimbus.features.command.api.BasicCommand;
import cc.nimbus.ui.notification.NotificationType;

import java.util.List;
import java.util.Locale;

public final class NotificationTestCommand extends BasicCommand {
    public NotificationTestCommand() {
        super("NotiTest", "Tests Client and Game notifications.", "notificationtest");
    }

    @Override
    public void execute(String[] args) {
        String target = args.length == 0 ? "all" : args[0].toLowerCase(Locale.ROOT);
        switch (target) {
            case "all" -> {
                postClientNotifications();
                postGameNotifications();
            }
            case "client" -> postClientNotifications();
            case "game" -> postGameNotifications();
            default -> throw new IllegalArgumentException("Usage: " + getUsage());
        }
    }

    @Override
    public List<String> suggest(String[] args) {
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return List.of("all", "client", "game").stream()
                    .filter(value -> value.startsWith(prefix))
                    .toList();
        }
        return List.of();
    }

    @Override
    public String getUsage() {
        return ".notitest [all|client|game]";
    }

    private static void postClientNotifications() {
        var notifications = Core.INSTANCE.getNotificationManager();
        notifications.postClient("Success", "Client success notification", 4000, NotificationType.SUCCESS);
        notifications.postClient("Info", "Client info notification", 4000, NotificationType.INFO);
        notifications.postClient("Warning", "Client warning notification", 4000, NotificationType.WARNING);
        notifications.postClient("Error", "Client error notification", 4000, NotificationType.ERROR);
    }

    private static void postGameNotifications() {
        var notifications = Core.INSTANCE.getNotificationManager();
        notifications.postGame("Game notification one", 4000);
        notifications.postGame("Game notification two", 4000);
        notifications.postGame("Game notification three", 4000);
    }
}
