package cc.nimbus.features.module.modules.render;

import cc.nimbus.core.Core;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.render.EventRender2D;
import cc.nimbus.features.client.ClientSettings;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.BooleanSetting;
import cc.nimbus.features.module.api.setting.impl.NumberSetting;
import cc.nimbus.ui.notification.NotificationType;
import cc.nimbus.utils.render.Render3DUtils;
import cc.nimbus.utils.render.font.NimbusFonts;
import cc.nimbus.utils.render.skija.api.SkijaFrame;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.PathBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

@ModuleInfo(
        name = "TNTWarn",
        displayName = "TNT Warn",
        alias = {"TNTTimer"},
        description = "Shows TNT fuse timers and explosion danger ranges.",
        category = ModuleCategory.Render
)
public final class TNTWarn extends BasicModule {
    private static final String FONT = NimbusFonts.BODY_STRONG;
    private static final int RED = 0xFFFF3B1F;
    private static final int YELLOW = 0xFFFFD400;
    private static final int GREEN = 0xFF42FF66;
    private static final int GROUND_SEGMENTS = 96;
    private static final float OUTER_THICKNESS = 0.12f;
    private static final float INNER_THICKNESS = 0.16f;
    private static final float PULSE_THRESHOLD_SECONDS = 2.0f;
    private static final float PULSE_AMPLITUDE = 0.12f;
    private static final float PULSE_SPEED = 2.0f;
    private static final double NOTIFICATION_RANGE_SQUARED = 8.0 * 8.0;

    public final NumberSetting dangerRadius = new NumberSetting("Danger Radius", 7.0, 0.5, 12, 0.1);
    public final NumberSetting lethalRadius = new NumberSetting("Lethal Radius", 4.0, 0.5, 12, 0.1);
    public final NumberSetting alpha = new NumberSetting("Alpha", 128, 8, 255, 1);
    public final BooleanSetting pulse = new BooleanSetting("Pulse Near Explosion", true);
    public final BooleanSetting timer = new BooleanSetting("Timer", true);
    private ClientLevel notificationLevel;
    private final java.util.Set<Integer> notifiedTnt = new java.util.HashSet<>();

    @EventTarget
    private void onRender2D(EventRender2D event) {
        if (event.isPost()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null
                || !(minecraft.screen == null || minecraft.screen instanceof net.minecraft.client.gui.screens.ChatScreen)) return;
        Render3DUtils.WorldProjection projection = Render3DUtils.worldProjection();
        if (projection == null) return;
        if (notificationLevel != minecraft.level) {
            notificationLevel = minecraft.level;
            notifiedTnt.clear();
        }
        int opacity = Math.clamp(alpha.getValue().intValue(), 0, 255);
        float outerRadius = dangerRadius.getValue().floatValue();
        float innerRadius = lethalRadius.getValue().floatValue();
        List<TimerEntry> entries = new ArrayList<>();
        List<EffectEntry> effects = new ArrayList<>();
        ClientSettings settings = Core.INSTANCE.getConfigManager().clientSettings();
        int colorIndex = 0;
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof PrimedTnt tnt)) continue;
            float fuse = Math.max(0f, tnt.getFuse());
            if (tnt.distanceToSqr(minecraft.player) <= NOTIFICATION_RANGE_SQUARED
                    && fuse > 0f && notifiedTnt.add(tnt.getId())) {
                Core.INSTANCE.getNotificationManager().postClient(
                        Core.INSTANCE.getI18nManager().get(
                                "nimbus.notification.tnt_warning.title", "TNT Warning"),
                        Core.INSTANCE.getI18nManager().get(
                                "nimbus.notification.tnt_warning.message", "TNT %.1fs", fuse / 20f),
                        Math.max(500L, Math.round(fuse * 50f)),
                        NotificationType.WARNING);
            }
            int color = withAlpha(fuseColor(Mth.clamp(1f - fuse / 80f, 0f, 1f)), opacity);
            double x = Mth.lerp(projection.partialTick(), tnt.xo, tnt.getX());
            double y = Mth.lerp(projection.partialTick(), tnt.yo, tnt.getY());
            double z = Mth.lerp(projection.partialTick(), tnt.zo, tnt.getZ());
            float radiusScale = pulseScale(minecraft, fuse);
            float outer = outerRadius * radiusScale;
            ProjectedRing innerRing = projectRing(x, y + .01, z, innerRadius,
                    INNER_THICKNESS, GROUND_SEGMENTS, projection);
            ProjectedRing outerRing = projectRing(x, y + .01, z, outer,
                    OUTER_THICKNESS, GROUND_SEGMENTS, projection);
            if (innerRing != null || outerRing != null) {
                effects.add(new EffectEntry(innerRing, outerRing, color));
            }
            if (!timer.getValue()) continue;
            Vec3 position = new Vec3(
                    Mth.lerp(projection.partialTick(), tnt.xo, tnt.getX()),
                    Mth.lerp(projection.partialTick(), tnt.yo, tnt.getY()) + tnt.getBbHeight() + .35,
                    Mth.lerp(projection.partialTick(), tnt.zo, tnt.getZ()));
            Render3DUtils.ProjectedPoint point = Render3DUtils.projectPoint(position, projection);
            if (point == null || !point.inFront()) continue;
            String text = Core.INSTANCE.getI18nManager().get(
                    "nimbus.module.tnt_warn.overlay.timer", "%.1fs",
                    Math.max(0, tnt.getFuse()) / 20f);
            entries.add(new TimerEntry(point, text, settings.accentColor(colorIndex++, System.currentTimeMillis())));
        }
        if (entries.isEmpty() && effects.isEmpty()) return;
        List<TimerEntry> timerSnapshot = List.copyOf(entries);
        List<EffectEntry> effectSnapshot = List.copyOf(effects);
        Core.INSTANCE.getRenderManager().skija().hud().submit(frame -> {
            drawEffects(frame, effectSnapshot);
            drawTimers(frame, timerSnapshot);
        });
        notifiedTnt.removeIf(id -> minecraft.level.getEntity(id) == null);
    }

    @Override
    protected void onDisable() {
        notificationLevel = null;
        notifiedTnt.clear();
    }

    private float pulseScale(Minecraft minecraft, float fuse) {
        if (!pulse.getValue()) return 1f;
        float fade = Mth.clamp(1f - fuse / 20f / PULSE_THRESHOLD_SECONDS, 0f, 1f);
        float time = (minecraft.level.getGameTime() + minecraft.getFrameTime()) / 20f;
        return 1f + Mth.sin(time * PULSE_SPEED * (float) (Math.PI * 2.0))
                * PULSE_AMPLITUDE * fade;
    }

    private static ProjectedRing projectRing(double x, double y, double z, float radius,
                                             float thickness, int segments,
                                             Render3DUtils.WorldProjection projection) {
        float[] xs = new float[segments];
        float[] ys = new float[segments];
        for (int index = 0; index < segments; index++) {
            double angle = Math.PI * 2d * index / segments;
            Render3DUtils.ProjectedPoint point = Render3DUtils.projectPoint(
                    new Vec3(x + Math.cos(angle) * radius, y, z + Math.sin(angle) * radius), projection);
            if (point == null || !point.inFront()) return null;
            xs[index] = point.x();
            ys[index] = point.y();
        }
        Render3DUtils.ProjectedPoint outer = Render3DUtils.projectPoint(new Vec3(x + radius, y, z), projection);
        Render3DUtils.ProjectedPoint inner = Render3DUtils.projectPoint(
                new Vec3(x + Math.max(0f, radius - thickness), y, z), projection);
        float strokeWidth = 1.2f;
        if (outer != null && inner != null) {
            strokeWidth = Math.max(1.2f, (float) Math.hypot(
                    (outer.x() - inner.x()) * 960f, (outer.y() - inner.y()) * 540f));
        }
        return new ProjectedRing(xs, ys, strokeWidth);
    }

    private static void drawEffects(SkijaFrame frame, List<EffectEntry> effects) {
        for (EffectEntry effect : effects) {
            drawRing(frame, effect.innerRing(), withAlpha(RED, effect.color() >>> 24));
            drawRing(frame, effect.outerRing(), effect.color());
        }
    }

    private static void drawRing(SkijaFrame frame, ProjectedRing ring, int color) {
        if (ring == null) return;
        try (PathBuilder builder = new PathBuilder();
             Paint paint = new Paint().setAntiAlias(true).setColor(color).setStroke(true)
                     .setStrokeWidth(ring.strokeWidth())) {
            builder.moveTo(screenX(ring.x()[0], frame.width()), screenY(ring.y()[0], frame.height()));
            for (int index = 1; index < ring.x().length; index++) {
                builder.lineTo(screenX(ring.x()[index], frame.width()), screenY(ring.y()[index], frame.height()));
            }
            builder.closePath();
            try (Path path = builder.detach()) { frame.canvas().drawPath(path, paint); }
        }
    }

    private static void drawTimers(SkijaFrame frame, List<TimerEntry> entries) {
        for (TimerEntry entry : entries) {
            float x = (entry.point().x() + 1f) * .5f * frame.width();
            float y = (entry.point().y() + 1f) * .5f * frame.height();
            frame.text().centerWithShadow(FONT, 11f, entry.text(), x, y - 8f,
                    0xFF000000 | (entry.color() & 0x00FFFFFF));
        }
    }

    private static float screenX(float normalized, float width) {
        return (normalized + 1f) * .5f * width;
    }

    private static float screenY(float normalized, float height) {
        return (normalized + 1f) * .5f * height;
    }

    private static int fuseColor(float progress) {
        if (progress < .5f) return lerpColor(GREEN, YELLOW, progress * 2f);
        return lerpColor(YELLOW, RED, (progress - .5f) * 2f);
    }

    private static int lerpColor(int first, int second, float amount) {
        float t = Mth.clamp(amount, 0f, 1f);
        int r = Math.round(((first >> 16) & 0xFF) + (((second >> 16) & 0xFF) - ((first >> 16) & 0xFF)) * t);
        int g = Math.round(((first >> 8) & 0xFF) + (((second >> 8) & 0xFF) - ((first >> 8) & 0xFF)) * t);
        int b = Math.round((first & 0xFF) + ((second & 0xFF) - (first & 0xFF)) * t);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.clamp(alpha, 0, 255) << 24);
    }

    private record TimerEntry(Render3DUtils.ProjectedPoint point, String text, int color) { }
    private record ProjectedRing(float[] x, float[] y, float strokeWidth) { }
    private record EffectEntry(ProjectedRing innerRing, ProjectedRing outerRing, int color) { }
}
