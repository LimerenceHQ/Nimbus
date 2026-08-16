package cc.nimbus.managers;

import cc.nimbus.core.interfaces.InstanceManager;
import cc.nimbus.event.EventBus;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.component.Priority;
import cc.nimbus.event.impl.game.EventScreenChange;
import cc.nimbus.event.impl.input.EventMouseButton;
import cc.nimbus.event.impl.input.EventMouseScroll;
import cc.nimbus.event.impl.render.EventRender2D;
import cc.nimbus.features.client.ClientSettings;
import cc.nimbus.features.hud.Draggable;
import cc.nimbus.features.hud.HudRect;
import cc.nimbus.features.hud.HudViewport;
import cc.nimbus.features.module.api.BasicModule;
import com.mojang.blaze3d.platform.Window;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.types.RRect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class HudManager implements InstanceManager {
    private static final float DRAG_FOLLOW_RATE = 24f;
    private static final float HOVER_IN_RATE = 15f;
    private static final float HOVER_OUT_RATE = 11f;
    private static final int HOVER_COLOR = 0xFFFFFFFF;
    private static final int DRAG_COLOR = 0xFFFFD166;

    private final ModuleManager moduleManager;
    private final RenderManager renderManager;
    private final ClientSettings clientSettings;
    private final Map<Draggable, EditorState> states = new IdentityHashMap<>();
    private Draggable dragging;
    private float dragOffsetX;
    private float dragOffsetY;
    private long lastFrameNanos;

    public HudManager(ModuleManager moduleManager, RenderManager renderManager, ClientSettings clientSettings) {
        this.moduleManager = Objects.requireNonNull(moduleManager, "moduleManager");
        this.renderManager = Objects.requireNonNull(renderManager, "renderManager");
        this.clientSettings = Objects.requireNonNull(clientSettings, "clientSettings");
    }

    public HudViewport viewport(float width, float height) {
        return HudViewport.fit(width, height, clientSettings.hudScale.getValue().floatValue());
    }

    @Override
    public void init() {
        EventBus.register(this);
    }

    @Override
    public void shutdown() {
        finishMotion();
        EventBus.unregister(this);
    }

    @EventTarget(priority = Priority.Highest)
    private void onMouseButton(EventMouseButton event) {
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT || !isEditing()) return;
        if (event.getAction() == GLFW.GLFW_PRESS) {
            ScreenSpace screen = screenSpace();
            Point mouse = mousePosition(screen);
            Item hit = findHit(mouse.x(), mouse.y(), screen.viewport());
            if (hit == null) return;
            dragging = hit.draggable();
            dragOffsetX = mouse.x() - hit.rect().x();
            dragOffsetY = mouse.y() - hit.rect().y();
            EditorState state = state(dragging);
            state.targetX = hit.rect().x();
            state.targetY = hit.rect().y();
            state.moving = true;
            event.cancelEvent();
        } else if (event.getAction() == GLFW.GLFW_RELEASE && dragging != null) {
            dragging = null;
            event.cancelEvent();
        }
    }

    @EventTarget(priority = Priority.Highest)
    private void onMouseScroll(EventMouseScroll event) {
        if (!isEditing() || !controlDown() || Math.abs(event.getY()) < 0.0001) return;
        ScreenSpace screen = screenSpace();
        Point mouse = mousePosition(screen);
        Item hit = findHit(mouse.x(), mouse.y(), screen.viewport());
        if (hit == null) return;

        HudRect oldRect = hit.rect();
        double oldScale = hit.draggable().getScale();
        float anchorX = (mouse.x() - oldRect.x()) / oldRect.width();
        float anchorY = (mouse.y() - oldRect.y()) / oldRect.height();
        hit.draggable().setScale(oldScale + event.getY() * Draggable.SCALE_STEP);
        if (Double.compare(oldScale, hit.draggable().getScale()) != 0) {
            HudRect newRect = hit.draggable().getRect(screen.viewport());
            float left = mouse.x() - anchorX * newRect.width();
            float top = mouse.y() - anchorY * newRect.height();
            hit.draggable().moveTo(left, top, screen.viewport());
            EditorState state = state(hit.draggable());
            HudRect moved = hit.draggable().getRect(screen.viewport());
            state.targetX = moved.x();
            state.targetY = moved.y();
            if (dragging == hit.draggable()) {
                dragOffsetX = mouse.x() - moved.x();
                dragOffsetY = mouse.y() - moved.y();
            }
        }
        event.cancelEvent();
    }

    @EventTarget(priority = Priority.Highest)
    private void onScreenChange(EventScreenChange event) {
        if (!(event.getScreen() instanceof ChatScreen)) finishMotion();
    }

    @EventTarget(priority = Priority.Lowest)
    private void onRender2D(EventRender2D event) {
        if (!isEditing()) {
            finishMotion();
            return;
        }

        float deltaSeconds = frameDeltaSeconds();
        ScreenSpace screen = screenSpace();
        Point mouse = mousePosition(screen);
        List<Item> items = collect(screen.viewport());
        Set<Draggable> active = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        items.forEach(item -> active.add(item.draggable()));
        states.keySet().removeIf(draggable -> !active.contains(draggable));
        if (dragging != null && !active.contains(dragging)) dragging = null;

        for (Item item : items) {
            EditorState state = state(item.draggable());
            boolean hovered = item.draggable() == dragging || item.rect().contains(mouse.x(), mouse.y());
            float rate = hovered ? HOVER_IN_RATE : HOVER_OUT_RATE;
            state.hover += (hovered ? 1f - state.hover : -state.hover) * easing(rate, deltaSeconds);
            if (Math.abs(state.hover) < 0.001f) state.hover = 0f;
        }

        if (dragging != null) {
            HudRect rect = dragging.getRect(screen.viewport());
            EditorState state = state(dragging);
            state.targetX = clamp(mouse.x() - dragOffsetX, 0f, Math.max(0f, screen.width() - rect.width()));
            state.targetY = clamp(mouse.y() - dragOffsetY, 0f, Math.max(0f, screen.height() - rect.height()));
            state.moving = true;
        }

        float follow = easing(DRAG_FOLLOW_RATE, deltaSeconds);
        for (var entry : states.entrySet()) {
            EditorState state = entry.getValue();
            if (!state.moving) continue;
            Draggable draggable = entry.getKey();
            HudRect rect = draggable.getRect(screen.viewport());
            float nextX = lerp(rect.x(), state.targetX, follow);
            float nextY = lerp(rect.y(), state.targetY, follow);
            if (Math.abs(nextX - state.targetX) < 0.05f && Math.abs(nextY - state.targetY) < 0.05f) {
                nextX = state.targetX;
                nextY = state.targetY;
                if (draggable != dragging) state.moving = false;
            }
            draggable.moveTo(nextX, nextY, screen.viewport());
        }

        items = collect(screen.viewport());
        List<Overlay> overlays = new ArrayList<>();
        for (Item item : items) {
            float hover = state(item.draggable()).hover;
            if (hover > 0.001f) overlays.add(new Overlay(item.rect(), hover, item.draggable() == dragging));
        }
        if (overlays.isEmpty()) return;

        float pixelScale = screen.pixelScale();
        List<Overlay> frameOverlays = List.copyOf(overlays);
        renderManager.skija().hud().submit(frame -> {
            try (Paint soft = new Paint().setAntiAlias(true).setMode(PaintMode.STROKE);
                 Paint crisp = new Paint().setAntiAlias(true).setMode(PaintMode.STROKE);
                 Paint fill = new Paint().setAntiAlias(true)) {
                for (Overlay overlay : frameOverlays) {
                    float hover = overlay.hover();
                    float padding = (3f + (1f - hover) * 3f) * pixelScale;
                    float radius = 5f * pixelScale;
                    HudRect rect = overlay.rect();
                    RRect outline = RRect.makeXYWH(
                            rect.x() - padding,
                            rect.y() - padding,
                            rect.width() + padding * 2f,
                            rect.height() + padding * 2f,
                            radius
                    );
                    int color = overlay.dragging() ? DRAG_COLOR : HOVER_COLOR;
                    fill.setColor(withAlpha(color, 0.045f * hover));
                    soft.setColor(withAlpha(color, 0.20f * hover)).setStrokeWidth(3f * pixelScale);
                    crisp.setColor(withAlpha(color, 0.82f * hover)).setStrokeWidth(pixelScale);
                    frame.canvas().drawRRect(outline, fill);
                    frame.canvas().drawRRect(outline, soft);
                    frame.canvas().drawRRect(outline, crisp);
                }
            }
        });
    }

    private Item findHit(float x, float y, HudViewport viewport) {
        return collect(viewport).stream()
                .filter(item -> item.rect().contains(x, y))
                .min(Comparator.comparingDouble(item -> item.rect().area()))
                .orElse(null);
    }

    private List<Item> collect(HudViewport viewport) {
        List<Item> result = new ArrayList<>();
        for (BasicModule module : moduleManager.getModules()) {
            if (!module.isEnabled()) continue;
            for (Draggable draggable : module.getDraggables()) {
                HudRect rect = draggable.getRect(viewport);
                if (rect.width() > 0f && rect.height() > 0f) result.add(new Item(draggable, rect));
            }
        }
        return result;
    }

    private EditorState state(Draggable draggable) {
        return states.computeIfAbsent(draggable, ignored -> new EditorState());
    }

    private float frameDeltaSeconds() {
        long now = System.nanoTime();
        float delta = lastFrameNanos == 0L ? 1f / 60f : Math.min(0.05f, (now - lastFrameNanos) / 1_000_000_000f);
        lastFrameNanos = now;
        return delta;
    }

    private void finishMotion() {
        if (!states.isEmpty()) {
            ScreenSpace screen = screenSpace();
            for (var entry : states.entrySet()) {
                EditorState state = entry.getValue();
                if (state.moving) {
                    entry.getKey().moveTo(state.targetX, state.targetY, screen.viewport());
                }
            }
        }
        dragging = null;
        states.clear();
        lastFrameNanos = 0L;
    }

    private static boolean isEditing() {
        return Minecraft.getInstance().screen instanceof ChatScreen;
    }

    private static boolean controlDown() {
        long window = Minecraft.getInstance().getWindow().handle();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    private ScreenSpace screenSpace() {
        Window window = Minecraft.getInstance().getWindow();
        float width = window.getWidth();
        float height = window.getHeight();
        float scaleX = width / Math.max(1f, window.getGuiScaledWidth());
        float scaleY = height / Math.max(1f, window.getGuiScaledHeight());
        return new ScreenSpace(viewport(width, height), Math.max(scaleX, scaleY));
    }

    private static Point mousePosition(ScreenSpace screen) {
        Minecraft minecraft = Minecraft.getInstance();
        Window window = minecraft.getWindow();
        float x = (float) (minecraft.mouseHandler.getScaledXPos(window)
                * screen.width() / Math.max(1f, window.getGuiScaledWidth()));
        float y = (float) (minecraft.mouseHandler.getScaledYPos(window)
                * screen.height() / Math.max(1f, window.getGuiScaledHeight()));
        return new Point(x, y);
    }

    private static float easing(float rate, float deltaSeconds) {
        return 1f - (float) Math.exp(-rate * deltaSeconds);
    }

    private static float lerp(float from, float to, float amount) {
        return from + (to - from) * amount;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int withAlpha(int color, float alpha) {
        int value = Math.clamp(Math.round(alpha * 255f), 0, 255);
        return (color & 0x00FFFFFF) | (value << 24);
    }

    private static final class EditorState {
        private float targetX;
        private float targetY;
        private float hover;
        private boolean moving;
    }

    private record Point(float x, float y) {}
    private record ScreenSpace(HudViewport viewport, float pixelScale) {
        private float width() { return viewport.width(); }
        private float height() { return viewport.height(); }
    }
    private record Item(Draggable draggable, HudRect rect) {}
    private record Overlay(HudRect rect, float hover, boolean dragging) {}
}
