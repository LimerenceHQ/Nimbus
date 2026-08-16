package cc.nimbus.features.rotation;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

public final class RotationRaycast {
    private RotationRaycast() {
    }

    public static HitResult raycast(Entity source, Rotation rotation, double range,
                                    boolean throughWalls, Predicate<Entity> predicate) {
        Vec3 start = source.getEyePosition();
        Vec3 direction = RotationMath.direction(rotation);
        Vec3 end = start.add(direction.scale(range));
        double maxDistanceSquared = range * range;

        BlockHitResult blockHit = source.level().clip(new ClipContext(
                start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, source));
        if (!throughWalls && blockHit.getType() != HitResult.Type.MISS) {
            maxDistanceSquared = start.distanceToSqr(blockHit.getLocation());
        }

        AABB search = source.getBoundingBox().expandTowards(direction.scale(range)).inflate(1.0);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                source, start, end, search,
                entity -> entity != source && entity.isPickable() && !entity.isSpectator() && predicate.test(entity),
                maxDistanceSquared
        );
        if (entityHit != null) return entityHit;
        return blockHit;
    }
}
