package cc.nimbus.features.rotation;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class RotationMath {
    private RotationMath() {
    }

    public static Rotation toRotation(Vec3 origin, Vec3 point) {
        Vec3 delta = point.subtract(origin);
        double horizontal = Math.hypot(delta.x, delta.z);
        return new Rotation(
                (float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0),
                (float) -Math.toDegrees(Math.atan2(delta.y, horizontal))
        ).normalized();
    }

    public static Vec3 direction(Rotation rotation) {
        float pitch = rotation.pitch() * Mth.DEG_TO_RAD;
        float yaw = -rotation.yaw() * Mth.DEG_TO_RAD;
        float yawCos = Mth.cos(yaw);
        float yawSin = Mth.sin(yaw);
        float pitchCos = Mth.cos(pitch);
        float pitchSin = Mth.sin(pitch);
        return new Vec3(yawSin * pitchCos, -pitchSin, yawCos * pitchCos);
    }

    public static Rotation sensitivityPatch(Rotation rotation, Rotation previous) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options == null) return rotation.clamped();
        double sensitivity = minecraft.options.sensitivity().get();
        double base = sensitivity * 0.6 + 0.2;
        double gcd = base * base * base * 8.0 * 0.15;
        if (gcd <= 0.0) return rotation.clamped();
        float yaw = previous.yaw() + (float) (Math.round(Mth.wrapDegrees(rotation.yaw() - previous.yaw()) / gcd) * gcd);
        float pitch = previous.pitch() + (float) (Math.round((rotation.pitch() - previous.pitch()) / gcd) * gcd);
        return new Rotation(yaw, Mth.clamp(pitch, -90.0f, 90.0f));
    }

    public static double distanceToBox(Entity from, Entity target) {
        return from.getEyePosition().distanceTo(nearestPoint(from.getEyePosition(), target.getBoundingBox().inflate(target.getPickRadius())));
    }

    public static AimPoint interactivePoint(Entity from, Entity target, double throughWallsRange) {
        Vec3 eyes = from.getEyePosition();
        AABB box = target.getBoundingBox().inflate(target.getPickRadius());
        Vec3 nearest = nearestPoint(eyes, box);
        double nearestDistance = eyes.distanceTo(nearest);
        if (nearestDistance <= throughWallsRange || visible(from, eyes, nearest)) {
            return new AimPoint(nearest, nearestDistance, nearestDistance <= throughWallsRange);
        }

        Vec3 best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (double x = 0.15; x <= 0.85; x += 0.1) {
            for (double y = 0.15; y <= 0.95; y += 0.1) {
                for (double z = 0.15; z <= 0.85; z += 0.1) {
                    Vec3 sample = new Vec3(
                            Mth.lerp(x, box.minX, box.maxX),
                            Mth.lerp(y, box.minY, box.maxY),
                            Mth.lerp(z, box.minZ, box.maxZ)
                    );
                    double distance = eyes.distanceTo(sample);
                    if (distance < bestDistance && visible(from, eyes, sample)) {
                        best = sample;
                        bestDistance = distance;
                    }
                }
            }
        }
        return new AimPoint(best, bestDistance, false);
    }

    public static boolean visible(Entity source, Vec3 start, Vec3 end) {
        HitResult result = source.level().clip(new ClipContext(
                start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, source));
        return result.getType() == HitResult.Type.MISS;
    }

    private static Vec3 nearestPoint(Vec3 point, AABB box) {
        return new Vec3(
                Mth.clamp(point.x, box.minX, box.maxX),
                Mth.clamp(point.y, box.minY, box.maxY),
                Mth.clamp(point.z, box.minZ, box.maxZ)
        );
    }

    public record AimPoint(Vec3 point, double distance, boolean throughWall) {
        public boolean exists() {
            return point != null && Double.isFinite(distance);
        }
    }
}