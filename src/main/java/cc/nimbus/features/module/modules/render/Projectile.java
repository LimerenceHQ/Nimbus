package cc.nimbus.features.module.modules.render;

import cc.nimbus.core.Core;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.render.EventRender3D;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.utils.render.Render3DUtils;
import cc.nimbus.utils.render.skija.api.SkijaFrame;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintStrokeCap;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.PathBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

@ModuleInfo(name = "Projectile", description = "Predicts the path of held projectiles.", category = ModuleCategory.Render)
public final class Projectile extends BasicModule {
    private static final int[][] BOX_EDGES = {
            {0, 1}, {1, 3}, {3, 2}, {2, 0},
            {4, 5}, {5, 7}, {7, 6}, {6, 4},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}
    };
    private static final int[][] BOX_FACES = {
            {0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1},
            {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3}
    };

    private int targetEntityId = -1;

    @EventTarget
    private void onRender3D(EventRender3D event) {
        targetEntityId = -1;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.screen != null) return;

        boolean usingItem = minecraft.player.isUsingItem();
        ItemStack stack = usingItem && supported(minecraft.player.getUseItem())
                ? minecraft.player.getUseItem() : minecraft.player.getMainHandItem();
        if (!supported(stack)) stack = minecraft.player.getOffhandItem();
        if (!supported(stack)) return;

        boolean bow = stack.is(Items.BOW);
        boolean crossbow = stack.is(Items.CROSSBOW);
        boolean trident = stack.is(Items.TRIDENT);
        boolean potion = stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION);
        int useTicks = minecraft.player.getTicksUsingItem();
        double speed;
        if (bow) {
            float charge = usingItem && minecraft.player.getUseItem() == stack ? useTicks / 20f : 1f;
            charge = (charge * charge + charge * 2f) / 3f;
            speed = Math.min(charge, 1f) * 3f;
        } else if (crossbow) {
            if (!CrossbowItem.isCharged(stack)) return;
            speed = 3.15;
        } else if (trident) {
            speed = 2.5;
        } else {
            speed = potion ? 0.5 : 1.5;
        }

        float yaw = minecraft.player.getYRot();
        float pitch = minecraft.player.getXRot();
        float radians = Mth.DEG_TO_RAD;
        Vec3 direction = new Vec3(
                -Mth.sin(yaw * radians) * Mth.cos(pitch * radians),
                -Mth.sin((pitch + (potion ? -20f : 0f)) * radians),
                Mth.cos(yaw * radians) * Mth.cos(pitch * radians)
        ).normalize();
        Vec3 position = minecraft.player.getEyePosition(event.getPartialTick()).add(
                -Mth.cos(yaw * radians) * 0.16f,
                -0.1f,
                -Mth.sin(yaw * radians) * 0.16f
        );
        Vec3 inherited = minecraft.player.getDeltaMovement();
        Vec3 movement = direction.scale(speed).add(
                inherited.x, minecraft.player.onGround() ? 0d : inherited.y, inherited.z
        );
        double gravity = potion ? 0.05 : (bow || crossbow || trident ? 0.05 : 0.03);
        List<Vec3> points = new ArrayList<>(201);
        points.add(position);
        HitResult landingHit = null;

        for (int tick = 0; tick < 200; tick++) {
            Vec3 next = position.add(movement);
            HitResult blockHit = minecraft.level.clip(new ClipContext(
                    position, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, minecraft.player
            ));
            AABB sweep = new AABB(
                    Math.min(position.x, next.x), Math.min(position.y, next.y), Math.min(position.z, next.z),
                    Math.max(position.x, next.x), Math.max(position.y, next.y), Math.max(position.z, next.z)
            ).inflate(0.3);
            EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                    minecraft.level, minecraft.player, position, next, sweep,
                    entity -> entity != minecraft.player && entity.isPickable() && !entity.isSpectator(), 0f
            );
            HitResult hit = blockHit.getType() == HitResult.Type.MISS ? entityHit : blockHit;
            if (entityHit != null && (hit == null
                    || position.distanceToSqr(entityHit.getLocation()) < position.distanceToSqr(hit.getLocation()))) {
                hit = entityHit;
            }
            if (hit != null && hit.getType() != HitResult.Type.MISS) {
                position = hit.getLocation();
                points.add(position);
                landingHit = hit;
                break;
            }

            position = next;
            points.add(position);
            movement = movement.scale(0.99).add(0d, -gravity, 0d);
        }
        if (points.size() < 2) return;

        int color = 0xE6000000
                | (Core.INSTANCE.getConfigManager().clientSettings().accentColor() & 0x00FFFFFF);
        Render3DUtils.WorldProjection projection = Render3DUtils.worldProjection();
        if (projection == null) return;

        List<ProjectedPath> paths = projectPaths(points, projection);
        ProjectedBox landingBox = null;
        Render3DUtils.ProjectedPoint impactPoint = null;
        if (landingHit instanceof BlockHitResult blockHit) {
            var blockPos = blockHit.getBlockPos();
            VoxelShape shape = minecraft.level.getBlockState(blockPos)
                    .getCollisionShape(minecraft.level, blockPos);
            AABB blockBox = shape.isEmpty()
                    ? new AABB(blockPos.getX(), blockPos.getY(), blockPos.getZ(),
                    blockPos.getX() + 1, blockPos.getY() + 1, blockPos.getZ() + 1)
                    : shape.bounds().move(blockPos);
            landingBox = projectBox(blockBox, projection);
            impactPoint = visiblePoint(blockHit.getLocation(), projection);
        } else if (landingHit instanceof EntityHitResult entityHit) {
            targetEntityId = entityHit.getEntity().getId();
        } else if (landingHit != null) {
            impactPoint = visiblePoint(landingHit.getLocation(), projection);
        }

        if (paths.isEmpty() && landingBox == null && impactPoint == null) return;
        ProjectileSnapshot snapshot = new ProjectileSnapshot(
                List.copyOf(paths), landingBox, impactPoint, color);
        Core.INSTANCE.getRenderManager().skija().hud().submit(frame -> draw(frame, snapshot));
    }

    @Override
    protected void onDisable() {
        targetEntityId = -1;
    }

    public int getTargetEntityId() {
        return targetEntityId;
    }

    private static boolean supported(ItemStack stack) {
        return stack != null && (stack.is(Items.BOW) || stack.is(Items.CROSSBOW)
                || stack.is(Items.TRIDENT) || stack.is(Items.SNOWBALL) || stack.is(Items.EGG)
                || stack.is(Items.ENDER_PEARL) || stack.is(Items.SPLASH_POTION)
                || stack.is(Items.LINGERING_POTION));
    }

    private static List<ProjectedPath> projectPaths(List<Vec3> points,
                                                    Render3DUtils.WorldProjection projection) {
        List<ProjectedPath> paths = new ArrayList<>();
        List<Render3DUtils.ProjectedPoint> current = new ArrayList<>(points.size());
        for (Vec3 point : points) {
            Render3DUtils.ProjectedPoint projected = visiblePoint(point, projection);
            if (projected == null) {
                finishPath(paths, current);
                current = new ArrayList<>();
                continue;
            }
            current.add(projected);
        }
        finishPath(paths, current);
        return paths;
    }

    private static void finishPath(List<ProjectedPath> paths,
                                   List<Render3DUtils.ProjectedPoint> points) {
        if (points.size() >= 2) paths.add(new ProjectedPath(List.copyOf(points)));
    }

    private static ProjectedBox projectBox(AABB box, Render3DUtils.WorldProjection projection) {
        Vec3[] corners = {
                new Vec3(box.minX, box.minY, box.minZ), new Vec3(box.maxX, box.minY, box.minZ),
                new Vec3(box.minX, box.maxY, box.minZ), new Vec3(box.maxX, box.maxY, box.minZ),
                new Vec3(box.minX, box.minY, box.maxZ), new Vec3(box.maxX, box.minY, box.maxZ),
                new Vec3(box.minX, box.maxY, box.maxZ), new Vec3(box.maxX, box.maxY, box.maxZ)
        };
        List<Render3DUtils.ProjectedPoint> projected = new ArrayList<>(corners.length);
        for (Vec3 corner : corners) {
            Render3DUtils.ProjectedPoint point = visiblePoint(corner, projection);
            if (point == null) return null;
            projected.add(point);
        }
        return new ProjectedBox(List.copyOf(projected));
    }

    private static Render3DUtils.ProjectedPoint visiblePoint(
            Vec3 point, Render3DUtils.WorldProjection projection) {
        Render3DUtils.ProjectedPoint projected = Render3DUtils.projectPoint(point, projection);
        return projected != null && projected.inFront() ? projected : null;
    }

    private static void draw(SkijaFrame frame, ProjectileSnapshot snapshot) {
        drawPaths(frame, snapshot.paths(), snapshot.color());
        if (snapshot.landingBox() != null) {
            drawBox(frame, snapshot.landingBox(), snapshot.color());
        }
        if (snapshot.impactPoint() != null) {
            float x = screenX(snapshot.impactPoint(), frame.width());
            float y = screenY(snapshot.impactPoint(), frame.height());
            frame.draw().outline(x - 3.5f, y - 3.5f, 7f, 7f, 2f, 1.5f, 0xF0FFFFFF);
        }
    }

    private static void drawPaths(SkijaFrame frame, List<ProjectedPath> paths, int color) {
        try (PathBuilder builder = new PathBuilder();
             Paint paint = new Paint().setAntiAlias(true).setColor(color).setStroke(true)
                     .setStrokeWidth(2f).setStrokeCap(PaintStrokeCap.ROUND)) {
            for (ProjectedPath path : paths) {
                List<Render3DUtils.ProjectedPoint> points = path.points();
                builder.moveTo(screenX(points.get(0), frame.width()),
                        screenY(points.get(0), frame.height()));
                for (int index = 1; index < points.size(); index++) {
                    builder.lineTo(screenX(points.get(index), frame.width()),
                            screenY(points.get(index), frame.height()));
                }
            }
            try (Path path = builder.detach()) {
                frame.canvas().drawPath(path, paint);
            }
        }
    }

    private static void drawBox(SkijaFrame frame, ProjectedBox box, int color) {
        List<Render3DUtils.ProjectedPoint> points = box.points();
        try (Paint paint = new Paint().setAntiAlias(true)
                .setColor(0x24000000 | (color & 0x00FFFFFF))) {
            for (int[] face : BOX_FACES) {
                try (PathBuilder builder = new PathBuilder()) {
                    builder.moveTo(screenX(points.get(face[0]), frame.width()),
                            screenY(points.get(face[0]), frame.height()));
                    for (int index = 1; index < face.length; index++) {
                        builder.lineTo(screenX(points.get(face[index]), frame.width()),
                                screenY(points.get(face[index]), frame.height()));
                    }
                    builder.closePath();
                    try (Path path = builder.detach()) {
                        frame.canvas().drawPath(path, paint);
                    }
                }
            }
        }
        try (PathBuilder builder = new PathBuilder();
             Paint paint = new Paint().setAntiAlias(true).setColor(color).setStroke(true)
                     .setStrokeWidth(2.5f).setStrokeCap(PaintStrokeCap.ROUND)) {
            for (int[] edge : BOX_EDGES) {
                builder.moveTo(screenX(points.get(edge[0]), frame.width()),
                        screenY(points.get(edge[0]), frame.height()));
                builder.lineTo(screenX(points.get(edge[1]), frame.width()),
                        screenY(points.get(edge[1]), frame.height()));
            }
            try (Path path = builder.detach()) {
                frame.canvas().drawPath(path, paint);
            }
        }
    }

    private static float screenX(Render3DUtils.ProjectedPoint point, float width) {
        return (point.x() + 1f) * .5f * width;
    }

    private static float screenY(Render3DUtils.ProjectedPoint point, float height) {
        return (point.y() + 1f) * .5f * height;
    }

    private record ProjectedPath(List<Render3DUtils.ProjectedPoint> points) { }
    private record ProjectedBox(List<Render3DUtils.ProjectedPoint> points) { }
    private record ProjectileSnapshot(List<ProjectedPath> paths, ProjectedBox landingBox,
                                      Render3DUtils.ProjectedPoint impactPoint, int color) { }
}
