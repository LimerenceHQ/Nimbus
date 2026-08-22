package cc.nimbus.features.module.modules.render;

import cc.nimbus.core.Core;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.render.EventRender2D;
import cc.nimbus.features.client.ClientSettings;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.BooleanSetting;
import cc.nimbus.features.module.api.setting.impl.ColorSetting;
import cc.nimbus.features.module.api.setting.impl.NumberSetting;
import cc.nimbus.utils.render.Render3DUtils;
import cc.nimbus.utils.render.font.NimbusFonts;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.PathBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

@ModuleInfo(name = "PointerESP", description = "Shows arrows pointing to other players.", category = ModuleCategory.Render)
public final class PointerESP extends BasicModule {
    public final BooleanSetting name = new BooleanSetting("Name", true);
    public final BooleanSetting health = new BooleanSetting("Health", true);
    public final BooleanSetting blink = new BooleanSetting("Blink Animation", true);
    public final BooleanSetting outline = new BooleanSetting("Outline Rect", true);
    public final NumberSetting outlineWidth = new NumberSetting("Outline Width", 1.5, 0.5, 5, 0.1);
    public final NumberSetting radius = new NumberSetting("Radius", 100, 15, 100, 5);
    public final NumberSetting arrowSize = new NumberSetting("Arrow Size", 14, 6, 30, 1);
    public final BooleanSetting hideThroughWalls = new BooleanSetting("Hide Through Walls", false);
    public final BooleanSetting invisibles = new BooleanSetting("Show Invisibles", true);
    public final ColorSetting visibleColor = new ColorSetting("Visible Color", 0xFF00FF00);
    public final ColorSetting invisibleColor = new ColorSetting("Invisible Color", 0xFFFF0000);
    @EventTarget
    private void onRender2D(EventRender2D event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.screen != null) return;
        Render3DUtils.WorldProjection projection = Render3DUtils.worldProjection();
        if (projection == null) return;
        float partial = projection.partialTick();
        long colorTime = System.currentTimeMillis();
        ClientSettings clientSettings = Core.INSTANCE.getConfigManager().clientSettings();
        List<Entry> entries = new ArrayList<>();
        int colorIndex = 0;
        for (Player player : minecraft.level.players()) {
            if (player == minecraft.player || player.isRemoved() || !player.isAlive()
                    || player.isSpectator()) continue;
            if (player.isInvisible() && !invisibles.getValue()) continue;
            if (!Core.INSTANCE.getTargetManager().isTarget(player)) continue;
            boolean visible = minecraft.player.hasLineOfSight(player);
            if (hideThroughWalls.getValue() && !visible) continue;
            Vec3 position = new Vec3(
                    Mth.lerp(partial, player.xOld, player.getX()),
                    Mth.lerp(partial, player.yOld, player.getY()) + player.getBbHeight() * .5,
                    Mth.lerp(partial, player.zOld, player.getZ()));
            Render3DUtils.ProjectedPoint point = Render3DUtils.projectPoint(position, projection);
            if (point != null) {
                entries.add(new Entry(player, point, visible,
                        clientSettings.accentColor(colorIndex++, colorTime)));
            }
        }
        if (entries.isEmpty()) return;
        List<Entry> snapshot = List.copyOf(entries);
        Core.INSTANCE.getRenderManager().skija().hud().submit(frame -> {
            float cx = frame.width() * .5f;
            float cy = frame.height() * .5f;
            float rx = Math.max(1f, frame.width() * .5f - 20f) * radius.getValue().floatValue() / 100f;
            float ry = Math.max(1f, frame.height() * .5f - 20f) * radius.getValue().floatValue() / 100f;
            for (Entry entry : snapshot) draw(frame, entry, cx, cy, rx, ry);
        });
    }

    private void draw(cc.nimbus.utils.render.skija.api.SkijaFrame frame, Entry entry,
                      float cx, float cy, float rx, float ry) {
        float sx = (entry.point.x() + 1f) * .5f * frame.width();
        float sy = (entry.point.y() + 1f) * .5f * frame.height();
        boolean onScreen = entry.point.inFront() && entry.point.x() >= -1f && entry.point.x() <= 1f
                && entry.point.y() >= -1f && entry.point.y() <= 1f
                && entry.point.depth() >= -1f && entry.point.depth() <= 1f;
        if (onScreen) return;
        float angle = (float) Math.atan2(sy - cy, sx - cx);
        if (!entry.point.inFront()) angle += Math.PI;
        float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
        float denom = (cos * cos) / (rx * rx) + (sin * sin) / (ry * ry);
        float distance = denom <= 0f ? 0f : (float) (1f / Math.sqrt(denom));
        float x = cx + cos * distance;
        float y = cy + sin * distance;
        int alpha = blink.getValue() ? (int) (140 + 115 * (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 180.0))) : 255;
        int color = withAlpha(entry.color, entry.visible ? alpha : Math.round(alpha * .62f));
        float size = arrowSize.getValue().floatValue();
        float tipDistance = size * .58f;
        float rearDistance = size * .42f;
        float halfWidth = size * .5f;
        float bx = x - cos * rearDistance, by = y - sin * rearDistance;
        float px = -sin, py = cos;
        try (PathBuilder builder = new PathBuilder()) {
            builder.moveTo(x + cos * tipDistance, y + sin * tipDistance);
            builder.lineTo(bx + px * halfWidth, by + py * halfWidth);
            builder.lineTo(bx - px * halfWidth, by - py * halfWidth);
            builder.closePath();
            try (Path path = builder.detach(); Paint paint = fill(color)) {
                frame.canvas().drawPath(path, paint);
                if (outline.getValue()) {
                    try (Paint stroke = fill(withAlpha(0xFF000000, alpha)).setStroke(true)
                            .setStrokeWidth(outlineWidth.getValue().floatValue())) {
                        frame.canvas().drawPath(path, stroke);
                    }
                }
            }
        }
        if (name.getValue()) {
            float nameSize = Math.max(9f, size * .85f);
            float nameHeight = frame.text().height(NimbusFonts.BODY_STRONG, nameSize, entry.player.getName().getString());
            frame.text().center(NimbusFonts.BODY_STRONG, nameSize,
                    entry.player.getName().getString(), x, y - size * .5f - nameHeight - 6f,
                    withAlpha(0xFFFFFFFF, alpha));
        }
        if (health.getValue()) {
            float pct = Mth.clamp(entry.player.getHealth() / Math.max(1f, entry.player.getMaxHealth()), 0f, 1f);
            float width = size + 4f;
            frame.draw().rect(x - width * .5f, y + size * .5f + 3f, width, 2f, withAlpha(0xFF000000, (int) (alpha * .59f)));
            frame.draw().rect(x - (width - 1f) * .5f, y + size * .5f + 3.5f,
                    (width - 1f) * pct, 1f,
                    withAlpha(entry.visible ? visibleColor.getValue() : invisibleColor.getValue(), alpha));
        }
    }

    private static Paint fill(int color) { return new Paint().setAntiAlias(true).setColor(color); }
    private static int withAlpha(int color, int alpha) { return (Math.clamp(alpha, 0, 255) << 24) | (color & 0x00FFFFFF); }
    private record Entry(Player player, Render3DUtils.ProjectedPoint point, boolean visible, int color) {}
}
