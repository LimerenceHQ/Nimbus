package cc.nimbus.features.module.modules.hud;

import cc.nimbus.core.Core;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.render.EventRender2D;
import cc.nimbus.features.client.ClientSettings;
import cc.nimbus.features.hud.Draggable;
import cc.nimbus.features.hud.HudRect;
import cc.nimbus.features.hud.HudSize;
import cc.nimbus.features.hud.HudViewport;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.modules.combat.KillAura;
import cc.nimbus.utils.render.font.NimbusFonts;
import cc.nimbus.utils.render.skija.api.SkijaFrame;
import cc.nimbus.utils.render.skija.api.SkijaRenderer;
import io.github.humbleui.skija.FilterTileMode;
import io.github.humbleui.skija.GradientStyle;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.types.Point;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;

@ModuleInfo(
        name = "TargetHUD",
        displayName = "Target HUD",
        description = "Shows the current combat target's face, name and health.",
        category = ModuleCategory.HUD
)
public final class TargetHUD extends BasicModule {
    private static final String FONT = NimbusFonts.BODY_STRONG;
    private static final float WIDTH = 236f;
    private static final float HEIGHT = 56f;
    private static final float AVATAR_X = 6f;
    private static final float AVATAR_Y = 5f;
    private static final float AVATAR_SIZE = 46f;
    private static final float CONTENT_X = 56f;
    private static final float CONTENT_WIDTH = 169f;
    private static final float BAR_Y = 42f;
    private static final float BAR_HEIGHT = 8f;
    private static final int BACKGROUND = 0x5C171A1E;
    private static final long TARGET_HOLD_MILLIS = 700L;

    private final Draggable drag = new Draggable("TargetHUD", 0.5, 0.32) {
        @Override
        public HudSize getSize() {
            return new HudSize(WIDTH, HEIGHT);
        }
    };

    private LivingEntity target;
    private long targetSeenAt;
    private long lastFrameNanos;
    private float animatedHealth = 1f;
    private float visibility;
    private boolean targetActive;

    @EventTarget
    private void onRender2D(EventRender2D event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return;
        boolean preview = minecraft.screen instanceof ChatScreen;
        if (minecraft.screen != null && !preview) return;

        LivingEntity current = combatTarget();
        if (current != null && !(current instanceof AbstractClientPlayer)) {
            target = null;
            targetSeenAt = 0L;
            targetActive = false;
            visibility = 0f;
            return;
        }
        if (current != null && current.isAlive()) {
            if (target == null) animatedHealth = 0f;
            target = current;
            targetSeenAt = System.currentTimeMillis();
            targetActive = true;
        } else if (preview) {
            if (target == null) animatedHealth = 0f;
            target = minecraft.player;
            targetSeenAt = System.currentTimeMillis();
            targetActive = true;
        } else if (target != null && System.currentTimeMillis() - targetSeenAt > TARGET_HOLD_MILLIS) {
            targetActive = false;
        }

        float delta = frameDelta();
        visibility = animate(visibility, targetActive ? 1f : 0f,
                1f - (float) Math.exp(-12f * delta));
        if (target == null || (!targetActive && visibility <= .002f)) {
            target = null;
            visibility = 0f;
            return;
        }

        float health = health(target);
        animatedHealth += (health - animatedHealth) * (1f - (float) Math.exp(-14f * delta));
        if (Math.abs(animatedHealth - health) < .001f) animatedHealth = health;

        SkijaRenderer skija = Core.INSTANCE.getRenderManager().skija();
        HudViewport viewport = Core.INSTANCE.getHudManager().viewport(skija.width(), skija.height());
        HudRect bounds = drag.getRect(viewport);

        float panelProgress = smooth(visibility);
        if (targetActive && visibility > .002f) {
            float renderScale = drag.getRenderScale(viewport);
            float panelScale = .94f + .06f * panelProgress;
            float offset = (drag.getAnchor().isRight() ? 1f : -1f)
                    * (1f - panelProgress) * 16f * renderScale;
            float centerX = bounds.centerX() + offset;
            float centerY = bounds.centerY();
            float blurWidth = WIDTH * panelScale * renderScale;
            float blurHeight = HEIGHT * panelScale * renderScale;
            skija.postProcess().add(RRect.makeXYWH(
                    centerX - blurWidth * .5f,
                    centerY - blurHeight * .5f,
                    blurWidth,
                    blurHeight,
                    9f * panelScale * renderScale));
        }
        ClientSettings settings = Core.INSTANCE.getConfigManager().clientSettings();
        long now = System.currentTimeMillis();
        int[] accent = new int[6];
        for (int index = -2; index <= 2; index++) {
            accent[index + 2] = settings.accentColor(index, now);
        }
        accent[5] = accent[0];
        float flowCycle = CONTENT_WIDTH * 2f;
        float flowPhase = (float) ((System.nanoTime() / 1_000_000d * .055d) % flowCycle);
        String name = target.getName().getString();
        String visibleName = ellipsize(skija, name, FONT, 16f, CONTENT_WIDTH);
        LivingEntity shownTarget = target;
        float shownHealth = animatedHealth;
        String percentage = Math.round(shownHealth * 100f) + "%";
        float shownVisibility = visibility;
        boolean rightAnchored = drag.getAnchor().isRight();

        skija.hud().submit(frame -> drag.render(frame, viewport, local -> draw(
                local, shownTarget, visibleName, percentage, shownHealth, accent, flowPhase,
                shownVisibility, rightAnchored)));
    }

    @Override
    protected void onDisable() {
        target = null;
        targetSeenAt = 0L;
        lastFrameNanos = 0L;
        animatedHealth = 1f;
        visibility = 0f;
        targetActive = false;
    }

    private static void draw(SkijaFrame frame, LivingEntity entity, String name, String percentage,
                             float health, int[] accent, float flowPhase,
                             float visibility, boolean rightAnchored) {
        float progress = smooth(visibility);
        float scale = .94f + .06f * progress;
        float offset = (rightAnchored ? 1f : -1f) * (1f - progress) * 16f;
        float avatarX = AVATAR_X;
        float contentX = CONTENT_X;
        int save = frame.canvas().save();
        try {
            frame.canvas().translate(WIDTH * .5f + offset, HEIGHT * .5f);
            frame.canvas().scale(scale, scale);
            frame.canvas().translate(-WIDTH * .5f, -HEIGHT * .5f);
            frame.draw().roundedRect(0f, 0f, WIDTH, HEIGHT, 9f, multiplyAlpha(BACKGROUND, progress));

            int avatarSave = frame.canvas().save();
            try {
                frame.draw().clipSmoothRoundedRect(avatarX, AVATAR_Y, AVATAR_SIZE, AVATAR_SIZE, 8f);
                Image image = frame.images().minecraftTexture(frame.images().entityTexture(entity));
                if (image != null && !image.isClosed()) {
                    try (Paint paint = new Paint().setAntiAlias(true).setAlpha(Math.round(255f * progress))) {
                        Rect source = Rect.makeXYWH(8f, 8f, 8f, 8f);
                        frame.canvas().drawImageRect(image, source,
                                Rect.makeXYWH(avatarX, AVATAR_Y, AVATAR_SIZE, AVATAR_SIZE),
                                SamplingMode.DEFAULT, paint, true);
                        frame.canvas().drawImageRect(image, Rect.makeXYWH(40f, 8f, 8f, 8f),
                                Rect.makeXYWH(avatarX, AVATAR_Y, AVATAR_SIZE, AVATAR_SIZE),
                                SamplingMode.DEFAULT, paint, true);
                    }
                }
            } finally {
                frame.canvas().restoreToCount(avatarSave);
            }

            float fillWidth = CONTENT_WIDTH * Math.clamp(health, 0f, 1f);
            float visibleFillWidth = fillWidth > .01f ? Math.max(BAR_HEIGHT, fillWidth) : 0f;
            float percentageSize = 8f;
            float percentageWidth = frame.text().width(NimbusFonts.BODY_MEDIUM, percentageSize, percentage);
            float percentageCenterX = Math.clamp(
                    contentX + visibleFillWidth,
                    contentX + percentageWidth * .5f,
                    contentX + CONTENT_WIDTH - percentageWidth * .5f);

            frame.text().draw(FONT, 16f, name, contentX, 7f,
                    multiplyAlpha(0xFFF2F4F5, progress));
            frame.text().center(NimbusFonts.BODY_MEDIUM, percentageSize, percentage,
                    percentageCenterX, 29f, multiplyAlpha(0xFFD8DEE2, progress));
            frame.draw().roundedRect(contentX, BAR_Y, CONTENT_WIDTH, BAR_HEIGHT, 4f,
                    multiplyAlpha(0xE3000000, progress));
            if (fillWidth > .01f) {
                float capOpacity = Math.min(1f, fillWidth / BAR_HEIGHT);
                float[] positions = {0f, .2f, .4f, .6f, .8f, 1f};
                GradientStyle repeat = GradientStyle.DEFAULT.withTileMode(FilterTileMode.REPEAT);
                try (Paint fill = new Paint().setAntiAlias(true)
                        .setAlpha(Math.round(255f * progress * capOpacity))
                        .setShader(frame.effects().linearGradient(
                                new Point(flowPhase, 0f), new Point(flowPhase + CONTENT_WIDTH * 2f, 0f),
                                accent, positions, repeat))) {
                    frame.draw().roundedRect(contentX, BAR_Y, visibleFillWidth, BAR_HEIGHT,
                            BAR_HEIGHT * .5f, fill);
                }
            }
        } finally {
            frame.canvas().restoreToCount(save);
        }
    }

    private static float animate(float value, float target, float speed) {
        float result = value + (target - value) * Math.clamp(speed, 0f, 1f);
        return Math.abs(result - target) < .002f ? target : result;
    }

    private static float smooth(float value) {
        float clamped = Math.clamp(value, 0f, 1f);
        return clamped * clamped * (3f - 2f * clamped);
    }

    private static int multiplyAlpha(int color, float opacity) {
        int alpha = Math.round((color >>> 24) * Math.clamp(opacity, 0f, 1f));
        return color & 0x00FFFFFF | alpha << 24;
    }

    private LivingEntity combatTarget() {
        KillAura aura = Core.INSTANCE.getModuleManager().getModule(KillAura.class);
        return aura != null && aura.isEnabled() ? aura.attackTarget : null;
    }

    private float frameDelta() {
        long now = System.nanoTime();
        float delta = lastFrameNanos == 0L ? 1f / 60f
                : Math.min(.05f, (now - lastFrameNanos) / 1_000_000_000f);
        lastFrameNanos = now;
        return delta;
    }

    private static float health(LivingEntity entity) {
        float max = Math.max(.01f, entity.getMaxHealth());
        return Math.clamp(entity.getHealth() / max, 0f, 1f);
    }

    private static String ellipsize(SkijaRenderer skija, String value, String font, float size, float width) {
        if (skija.text().width(font, size, value) <= width) return value;
        String result = value;
        while (result.length() > 1 && skija.text().width(font, size, result + "…") > width) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "…";
    }
}
