package cc.nimbus.ui.notification;

import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.component.Priority;
import cc.nimbus.event.impl.render.EventRender2D;
import cc.nimbus.core.Core;
import cc.nimbus.features.client.ClientSettings;
import cc.nimbus.features.hud.HudViewport;
import cc.nimbus.features.module.modules.hud.Notification;
import cc.nimbus.managers.HudManager;
import cc.nimbus.managers.NotificationManager;
import cc.nimbus.managers.ModuleManager;
import cc.nimbus.managers.RenderManager;
import cc.nimbus.utils.render.font.NimbusFonts;
import cc.nimbus.utils.render.skija.api.SkijaFrame;
import cc.nimbus.utils.render.skija.api.SkijaRenderer;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.types.RRect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class NotificationRenderer {
    private static final String CLIENT_TITLE_FONT = NimbusFonts.DISPLAY;
    private static final String CLIENT_TIME_FONT = NimbusFonts.CONTENT;
    private static final String CLIENT_MESSAGE_FONT = NimbusFonts.BODY_MEDIUM;
    private static final String GAME_FONT = NimbusFonts.BODY_STRONG;
    private static final float CLIENT_TITLE_SIZE = 12f;
    private static final float CLIENT_TIME_SIZE = 9f;
    private static final float CLIENT_MESSAGE_SIZE = 11f;
    private static final float GAME_FONT_SIZE = 14f;
    private static final float CLIENT_PROGRESS_HEIGHT = 3f;
    private static final float GAME_PROGRESS_HEIGHT = 2f;
    private static final float DEFAULT_RENDER_SCALE = 1.5f;

    private final NotificationManager manager;
    private final HudManager hudManager;
    private final ModuleManager modules;
    private final RenderManager renderManager;
    private final Map<Long, ClientState> clientStates = new HashMap<>();
    private final Map<Long, GameState> gameStates = new HashMap<>();

    public NotificationRenderer(NotificationManager manager, HudManager hudManager,
                                ModuleManager modules, RenderManager renderManager) {
        this.manager = manager;
        this.hudManager = hudManager;
        this.modules = modules;
        this.renderManager = renderManager;
    }

    public synchronized void clear() {
        clientStates.clear();
        gameStates.clear();
    }

    @EventTarget(priority = Priority.Lowest)
    private void onRender2D(EventRender2D event) {
        if (event.isPost()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null
                || !(minecraft.screen == null || minecraft.screen instanceof ChatScreen)) return;

        Notification module = modules.getModule(Notification.class);
        if (module == null || !module.isEnabled()) return;

        List<NotificationManager.Notification> notifications = manager.snapshot();
        Set<Long> activeIds = new HashSet<>();
        notifications.forEach(notification -> activeIds.add(notification.id()));
        clientStates.keySet().removeIf(id -> !activeIds.contains(id));
        gameStates.keySet().removeIf(id -> !activeIds.contains(id));

        SkijaRenderer skija = renderManager.skija();
        HudViewport viewport = hudManager.viewport(skija.width(), skija.height());
        float renderScale = viewport.scale() * DEFAULT_RENDER_SCALE;
        float logicalWidth = viewport.width() / renderScale;
        float logicalHeight = viewport.height() / renderScale;
        if (logicalWidth <= 0f || logicalHeight <= 0f) return;

        ClientRenderSnapshot client = updateClient(notifications, module.client.getValue(),
                logicalWidth, skija);
        ClientSettings clientSettings = Core.INSTANCE.getConfigManager().clientSettings();
        GameRenderSnapshot game = updateGame(notifications, module.game.getValue(),
                logicalWidth, logicalHeight, skija, clientSettings, System.currentTimeMillis());
        removeFinished(notifications);
        if (client.entries().isEmpty() && game.entries().isEmpty()) return;

        addBlurRegions(skija, client.entries(), game.entries(), renderScale);
        skija.hud().submit(frame -> {
            Canvas canvas = frame.canvas();
            int save = canvas.save();
            try {
                canvas.scale(renderScale, renderScale);
                drawClient(frame, client.entries());
                drawGame(frame, game.entries());
            } finally {
                canvas.restoreToCount(save);
            }
        });
    }

    private static void addBlurRegions(SkijaRenderer skija, List<ClientEntry> clients,
                                       List<GameEntry> games, float scale) {
        for (ClientEntry entry : clients) {
            float x = (entry.logicalWidth() - entry.width()) * .5f;
            float y = entry.posY() - entry.height();
            float animationScale = entry.scale();
            if (animationScale <= 0f) continue;
            float centerX = x + entry.width() * .5f;
            float centerY = y + entry.height() * .5f;
            float scaledX = centerX + (x - centerX) * animationScale;
            float scaledY = centerY + (y - centerY) * animationScale;
            skija.postProcess().add(RRect.makeXYWH(
                    scaledX * scale, scaledY * scale,
                    entry.width() * animationScale * scale,
                    entry.height() * animationScale * scale, 3f * animationScale * scale));
        }
        for (GameEntry entry : games) {
            float x = entry.logicalWidth() - entry.width() + entry.animationX();
            float y = entry.posY() - entry.height();
            skija.postProcess().add(RRect.makeXYWH(
                    x * scale, y * scale, entry.width() * scale,
                    entry.height() * scale,
                    3f * scale));
        }
    }

    private ClientRenderSnapshot updateClient(List<NotificationManager.Notification> notifications,
                                              boolean visible, float logicalWidth, SkijaRenderer skija) {
        List<ClientEntry> entries = new java.util.ArrayList<>();
        int startY = 48;
        for (NotificationManager.Notification notification : notifications) {
            if (notification.kind() != NotificationManager.Kind.CLIENT) continue;
            ClientState state = clientStates.computeIfAbsent(notification.id(),
                    ignored -> new ClientState(notification));
            state.measure(skija);
            state.update(startY);
            if (visible) entries.add(state.snapshot(logicalWidth));
            startY += state.height + 6;
        }
        return new ClientRenderSnapshot(List.copyOf(entries));
    }

    private GameRenderSnapshot updateGame(List<NotificationManager.Notification> notifications,
                                          boolean visible, float logicalWidth, float logicalHeight,
                                          SkijaRenderer skija, ClientSettings clientSettings,
                                          long colorTime) {
        List<GameEntry> entries = new java.util.ArrayList<>();
        double startY = logicalHeight - 24;
        int colorIndex = 0;
        for (NotificationManager.Notification notification : notifications) {
            if (notification.kind() != NotificationManager.Kind.GAME) continue;
            GameState state = gameStates.computeIfAbsent(notification.id(),
                    ignored -> new GameState(notification));
            state.measure(skija);
            state.update(startY);
            if (visible) {
                entries.add(state.snapshot(logicalWidth,
                        clientSettings.accentColor(colorIndex++, colorTime)));
            }
            startY -= state.height + 8;
        }
        return new GameRenderSnapshot(List.copyOf(entries));
    }

    private void removeFinished(List<NotificationManager.Notification> notifications) {
        for (NotificationManager.Notification notification : notifications) {
            if (notification.kind() == NotificationManager.Kind.CLIENT) {
                ClientState state = clientStates.get(notification.id());
                if (state != null && state.shouldDelete()) manager.remove(notification.id());
            } else {
                GameState state = gameStates.get(notification.id());
                if (state != null && state.shouldDelete()) manager.remove(notification.id());
            }
        }
    }

    private static void drawClient(SkijaFrame frame, List<ClientEntry> entries) {
        for (ClientEntry entry : entries) {
            float x = (entry.logicalWidth() - entry.width()) * .5f;
            float y = entry.posY() - entry.height();
            int save = frame.canvas().save();
            try {
                frame.canvas().translate(x + entry.width() * .5f, y + entry.height() * .5f);
                frame.canvas().scale(entry.scale(), entry.scale());
                frame.canvas().translate(-(x + entry.width() * .5f), -(y + entry.height() * .5f));
                frame.draw().roundedRect(x, y, entry.width(), entry.height(), 3f, 0x5C181818);
                float headerCenterY = y + 13f;
                float bodyCenterY = y + 29f;
                String time = Math.round(((entry.durationMs() - entry.elapsedMs()) / 1000D) * 10.0) / 10.0 + "s";
                float titleY = headerCenterY
                        - frame.text().visualCenterY(CLIENT_TITLE_FONT, CLIENT_TITLE_SIZE, entry.title());
                float titleBaselineOffset = frame.text().baselineFromTop(
                        CLIENT_TITLE_FONT, CLIENT_TITLE_SIZE, entry.title(), 0f);
                float timeBaselineOffset = frame.text().baselineFromTop(
                        CLIENT_TIME_FONT, CLIENT_TIME_SIZE, time, 0f);
                float timeY = titleY + titleBaselineOffset - timeBaselineOffset;
                float messageY = bodyCenterY
                        - frame.text().visualCenterY(CLIENT_MESSAGE_FONT, CLIENT_MESSAGE_SIZE, entry.message());
                frame.text().draw(CLIENT_TITLE_FONT, CLIENT_TITLE_SIZE, entry.title(),
                        x + 10f, titleY, entry.type().getColor());
                frame.text().draw(CLIENT_TIME_FONT, CLIENT_TIME_SIZE, time,
                        x + 12f + entry.titleWidth(), timeY, 0xFFC8C8C8);
                frame.text().draw(CLIENT_MESSAGE_FONT, CLIENT_MESSAGE_SIZE, entry.message(),
                        x + 10f, messageY, 0xFFFFFFFF);
                float health = clamp((entry.durationMs() - entry.elapsedMs()) / (float) entry.durationMs(), 0f, 1f);
                float progressX = x + 5f;
                float progressWidth = entry.width() - 10f;
                float progressY = y + entry.height() - CLIENT_PROGRESS_HEIGHT - 2f;
                frame.draw().roundedRect(progressX, progressY, progressWidth, CLIENT_PROGRESS_HEIGHT,
                        CLIENT_PROGRESS_HEIGHT * .5f, 0x3DFFFFFF);
                frame.draw().roundedRect(progressX, progressY, Math.max(0f, progressWidth * health),
                        CLIENT_PROGRESS_HEIGHT, CLIENT_PROGRESS_HEIGHT * .5f,
                        multiplyAlpha(entry.type().getColor(), .65f));
                frame.draw().roundedRect(x + 2, y + 2, entry.animationW(), entry.height() - 4,
                        2f, entry.type().getColor());
            } finally {
                frame.canvas().restoreToCount(save);
            }
        }
    }

    private static void drawGame(SkijaFrame frame, List<GameEntry> entries) {
        for (GameEntry entry : entries) {
            float x = entry.logicalWidth() - entry.width() + entry.animationX();
            float y = entry.posY() - entry.height();
            float progressX = x;
            float progressWidth = entry.width();
            frame.draw().roundedRect(x, y, entry.width(), entry.height(), 3, 0x5C000000);
            float progress = Math.min(1f, entry.elapsedMs() / (float) entry.durationMs());
            float progressY = y + entry.height() - GAME_PROGRESS_HEIGHT;
            frame.draw().roundedRect(progressX, progressY, progressWidth, GAME_PROGRESS_HEIGHT,
                    1f, 0x26FFFFFF);
            frame.draw().roundedRect(progressX, progressY,
                    entry.finished() ? 0f : progress * progressWidth, GAME_PROGRESS_HEIGHT,
                    1f, multiplyAlpha(entry.color(), .75f));
            drawGameText(frame, entry, x, y);
        }
    }

    private static void drawGameText(SkijaFrame frame, GameEntry entry, float x, float y) {
        String message = entry.message();
        String status = null;
        String moduleName = message;
        if (entry.type() != null) {
            String suffix = entry.type() == NotificationType.SUCCESS ? " enabled" : " disabled";
            if (message.endsWith(suffix)) {
                moduleName = message.substring(0, message.length() - suffix.length());
                status = suffix;
            }
        }

        float messageWidth = frame.text().width(GAME_FONT, GAME_FONT_SIZE, message);
        float textX = x + (entry.width() - messageWidth) * .5f;
        float textY = y + entry.height() * .5f
                - frame.text().visualCenterY(GAME_FONT, GAME_FONT_SIZE, message);
        if (status == null) {
            frame.text().draw(GAME_FONT, GAME_FONT_SIZE, message, textX, textY, 0xFFFFFFFF);
            return;
        }

        float moduleWidth = frame.text().width(GAME_FONT, GAME_FONT_SIZE, moduleName);
        frame.text().draw(GAME_FONT, GAME_FONT_SIZE, moduleName, textX, textY, 0xFFFFFFFF);
        frame.text().draw(GAME_FONT, GAME_FONT_SIZE, status,
                textX + moduleWidth, textY, entry.type().getColor());
    }

    private static float moveUD(float current, float target, float speed) {
        float movement = (target - current) * speed;
        if (movement > 0f) {
            movement = Math.max(speed, movement);
            movement = Math.min(target - current, movement);
        } else if (movement < 0f) {
            movement = Math.min(-speed, movement);
            movement = Math.max(target - current, movement);
        }
        return current + movement;
    }

    private static int debugFps() {
        return Math.max(1, Minecraft.getInstance().getFps());
    }

    private static int animationFps() {
        return Math.clamp(debugFps(), 1, 244);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int multiplyAlpha(int color, float multiplier) {
        int alpha = (color >>> 24) & 0xFF;
        int scaledAlpha = Math.round(alpha * clamp(multiplier, 0f, 1f));
        return (color & 0x00FFFFFF) | (scaledAlpha << 24);
    }

    private static final class ClientState {
        private final NotificationManager.Notification notification;
        private long timerStartMs;
        private float posY = -16f;
        private float width = 240f;
        private int height = 40;
        private float animationS = 240f;
        private float animationW = 2f;
        private float scale;
        private float titleWidth;
        private float messageWidth;

        private ClientState(NotificationManager.Notification notification) {
            this.notification = notification;
            timerStartMs = notification.createdAtMs();
        }

        private void measure(SkijaRenderer skija) {
            titleWidth = skija.text().width(CLIENT_TITLE_FONT, CLIENT_TITLE_SIZE, notification.title());
            messageWidth = skija.text().width(CLIENT_MESSAGE_FONT, CLIENT_MESSAGE_SIZE, notification.message());
            width = Math.max(180, Math.max(messageWidth + 24f, titleWidth + 44f));
            height = 44;
        }

        private void update(float targetY) {
            if (isFinished()) scale = moveUD(scale, 0f, 6f / animationFps());
            else scale = 1f;
            posY = moveUD(posY, targetY, 10f / debugFps());
            if (posY != targetY && posY < targetY) timerStartMs = System.currentTimeMillis();
            if (elapsedMs() >= notification.durationMs()) {
                animationW = moveUD(animationW, width - 4f, 10f / debugFps());
            }
            animationS = isFinished() && scale == 0f ? width : 0f;
        }

        private boolean isFinished() {
            return elapsedMs() >= notification.durationMs() && animationW >= width - 4f;
        }

        private long elapsedMs() {
            return Math.max(0L, System.currentTimeMillis() - timerStartMs);
        }

        private boolean shouldDelete() {
            return isFinished() && animationW >= width - 4f && animationS >= width && scale <= Float.MIN_VALUE;
        }

        private ClientEntry snapshot(float logicalWidth) {
            return new ClientEntry(notification.title(), notification.message(), notification.type(),
                    logicalWidth, width, height, posY, scale, animationW, titleWidth,
                    notification.durationMs(), elapsedMs());
        }
    }

    private static final class GameState {
        private final NotificationManager.Notification notification;
        private float posY = -1f;
        private float width;
        private float height = 28f;
        private float animationX;
        private boolean measured;

        private GameState(NotificationManager.Notification notification) {
            this.notification = notification;
            this.width = 18f;
            this.animationX = width;
        }

        private void measure(SkijaRenderer skija) {
            width = skija.text().width(GAME_FONT, GAME_FONT_SIZE, notification.message()) + 24f;
            height = 28f;
            if (!measured) {
                animationX = width;
                measured = true;
            }
        }

        private void update(double targetY) {
            animationX = moveUD(animationX, isFinished() ? width : 0f, 8f / animationFps());
            if (posY == -1f) posY = (float) targetY;
            else posY = moveUD(posY, (float) targetY, 8f / animationFps());
        }

        private long elapsedMs() {
            return Math.max(0L, System.currentTimeMillis() - notification.createdAtMs());
        }

        private boolean isFinished() {
            return elapsedMs() >= notification.durationMs();
        }

        private boolean shouldDelete() {
            return isFinished() && animationX >= width;
        }

        private GameEntry snapshot(float logicalWidth, int color) {
            return new GameEntry(notification.message(), notification.type(), color, logicalWidth, width, height,
                    posY, animationX, notification.durationMs(), elapsedMs(), isFinished());
        }
    }

    private record ClientEntry(String title, String message, NotificationType type,
                               float logicalWidth, float width, float height, float posY,
                               float scale, float animationW, float titleWidth,
                               long durationMs, long elapsedMs) {}

    private record GameEntry(String message, NotificationType type, int color,
                             float logicalWidth, float width, float height,
                             float posY, float animationX, long durationMs,
                             long elapsedMs, boolean finished) {}

    private record ClientRenderSnapshot(List<ClientEntry> entries) {}
    private record GameRenderSnapshot(List<GameEntry> entries) {}
}
