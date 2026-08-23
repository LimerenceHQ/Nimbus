package cc.nimbus.managers;

import cc.nimbus.core.interfaces.InstanceManager;
import cc.nimbus.event.EventBus;
import cc.nimbus.ui.notification.NotificationRenderer;
import cc.nimbus.ui.notification.NotificationType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public final class NotificationManager implements InstanceManager {
    private static final int MAX_NOTIFICATIONS = 64;

    private final List<Notification> notifications = new ArrayList<>();
    private final AtomicLong nextId = new AtomicLong();
    private final NotificationRenderer renderer;
    private volatile boolean running;

    public NotificationManager(ModuleManager modules, RenderManager renderManager, HudManager hudManager) {
        renderer = new NotificationRenderer(this,
                Objects.requireNonNull(hudManager, "hudManager"),
                Objects.requireNonNull(modules, "modules"),
                Objects.requireNonNull(renderManager, "renderManager"));
    }

    @Override
    public synchronized void init() {
        clear();
        renderer.clear();
        EventBus.register(renderer);
        running = true;
    }

    @Override
    public synchronized void shutdown() {
        running = false;
        EventBus.unregister(renderer);
        clear();
        renderer.clear();
    }

    public synchronized void postClient(String title, String message, long durationMs, NotificationType type) {
        if (!running) return;
        add(Notification.client(nextId.incrementAndGet(), title, message, durationMs, type));
    }

    public synchronized void postGame(String message, long durationMs) {
        postGame(message, durationMs, null);
    }

    public synchronized void postGame(String message, long durationMs, NotificationType type) {
        if (!running) return;
        add(Notification.game(nextId.incrementAndGet(), message, durationMs, type));
    }

    public synchronized List<Notification> snapshot() {
        return List.copyOf(notifications);
    }

    public synchronized void remove(long id) {
        notifications.removeIf(notification -> notification.id() == id);
    }

    public synchronized void clear() {
        notifications.clear();
    }

    private void add(Notification notification) {
        Objects.requireNonNull(notification, "notification");
        if (notifications.size() >= MAX_NOTIFICATIONS) notifications.removeFirst();
        notifications.add(notification);
    }

    public record Notification(
            long id,
            Kind kind,
            String title,
            String message,
            long durationMs,
            NotificationType type,
            long createdAtMs
    ) {
        public Notification {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(message, "message");
            if (message.isEmpty()) throw new IllegalArgumentException("message cannot be empty");
            if (durationMs <= 0) throw new IllegalArgumentException("durationMs must be positive");
            if (kind == Kind.CLIENT) {
                Objects.requireNonNull(title, "title");
                Objects.requireNonNull(type, "type");
                if (title.isEmpty()) throw new IllegalArgumentException("title cannot be empty");
            }
        }

        public static Notification client(long id, String title, String message,
                                          long durationMs, NotificationType type) {
            return new Notification(id, Kind.CLIENT, title, message, durationMs, type,
                    System.currentTimeMillis());
        }

        public static Notification game(long id, String message, long durationMs) {
            return game(id, message, durationMs, null);
        }

        public static Notification game(long id, String message, long durationMs, NotificationType type) {
            return new Notification(id, Kind.GAME, null, message, durationMs, type,
                    System.currentTimeMillis());
        }
    }

    public enum Kind {
        CLIENT,
        GAME
    }
}
