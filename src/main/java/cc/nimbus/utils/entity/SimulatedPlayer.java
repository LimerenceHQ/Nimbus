package cc.nimbus.utils.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class SimulatedPlayer {
    private static final Map<LivingEntity, CacheEntry> CACHE = new WeakHashMap<>();

    private SimulatedPlayer() {
    }

    public static synchronized List<AABB> predictBoxes(LivingEntity entity, int maxTicks) {
        if (entity == null) return List.of();
        int ticks = Math.max(0, maxTicks);
        CacheEntry cached = CACHE.get(entity);
        if (cached != null && cached.matches(entity, ticks)) return cached.limit(ticks);

        List<AABB> boxes = simulate(entity, ticks);
        CACHE.put(entity, CacheEntry.capture(entity, ticks, boxes));
        return boxes;
    }

    private static List<AABB> simulate(LivingEntity entity, int maxTicks) {
        List<AABB> boxes = new ArrayList<>(maxTicks + 1);
        AABB box = entity.getBoundingBox();
        Vec3 tracked = entity.position().subtract(entity.xo, entity.yo, entity.zo);
        Vec3 velocity = tracked.lengthSqr() > 1.0E-7D ? tracked : entity.getDeltaMovement();
        boolean onGround = entity.onGround();

        for (int tick = 0; tick <= maxTicks; tick++) {
            boxes.add(box);
            if (tick == maxTicks) break;

            Vec3 clipped = Entity.collideBoundingBox(entity, velocity, box, entity.level(), List.of());
            box = box.move(clipped);

            boolean xCollision = Math.abs(clipped.x - velocity.x) > 1.0E-7D;
            boolean yCollision = Math.abs(clipped.y - velocity.y) > 1.0E-7D;
            boolean zCollision = Math.abs(clipped.z - velocity.z) > 1.0E-7D;
            onGround = yCollision && velocity.y < 0.0D;

            double x = xCollision ? 0.0D : (onGround ? tracked.x : velocity.x * 0.91D);
            double z = zCollision ? 0.0D : (onGround ? tracked.z : velocity.z * 0.91D);
            double y = yCollision ? 0.0D : velocity.y;
            if (!entity.isNoGravity()) y -= entity.isInWater() ? 0.02D : 0.08D;
            double verticalDrag = entity.isInWater() ? 0.8D : 0.98D;
            velocity = new Vec3(x, y * verticalDrag, z);
        }
        return List.copyOf(boxes);
    }

    private record CacheEntry(int maxTicks, int tickCount, Vec3 position, Vec3 velocity,
                              AABB box, List<AABB> boxes) {
        private static CacheEntry capture(LivingEntity entity, int maxTicks, List<AABB> boxes) {
            return new CacheEntry(maxTicks, entity.tickCount, entity.position(),
                    entity.getDeltaMovement(), entity.getBoundingBox(), boxes);
        }

        private boolean matches(LivingEntity entity, int requestedTicks) {
            return requestedTicks <= maxTicks
                    && entity.tickCount == tickCount
                    && entity.position().distanceToSqr(position) < 1.0E-12D
                    && entity.getDeltaMovement().distanceToSqr(velocity) < 1.0E-12D
                    && sameBox(entity.getBoundingBox(), box);
        }

        private List<AABB> limit(int requestedTicks) {
            int end = Math.min(boxes.size(), requestedTicks + 1);
            return end == boxes.size() ? boxes : boxes.subList(0, end);
        }

        private static boolean sameBox(AABB first, AABB second) {
            return Math.abs(first.minX - second.minX) < 1.0E-12D
                    && Math.abs(first.minY - second.minY) < 1.0E-12D
                    && Math.abs(first.minZ - second.minZ) < 1.0E-12D
                    && Math.abs(first.maxX - second.maxX) < 1.0E-12D
                    && Math.abs(first.maxY - second.maxY) < 1.0E-12D
                    && Math.abs(first.maxZ - second.maxZ) < 1.0E-12D;
        }
    }
}
