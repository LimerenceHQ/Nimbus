package cc.nimbus.utils.entity;

import cc.nimbus.core.Core;
import cc.nimbus.features.rotation.Rotation;
import cc.nimbus.features.rotation.RotationMath;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class ProjectilePhysicsUtils {
    public static final double DEFAULT_DRAG = 0.99D;
    public static final double DEFAULT_WATER_DRAG = 0.8D;
    public static final double ARROW_GRAVITY = 0.05D;
    public static final double THROWABLE_GRAVITY = 0.03D;
    public static final double POTION_GRAVITY = 0.05D;
    public static final double EXPERIENCE_BOTTLE_GRAVITY = 0.07D;
    public static final double TRIDENT_GRAVITY = 0.05D;
    public static final double FISHING_ROD_GRAVITY = 0.03D;
    public static final double ARROW_WATER_DRAG = 0.6D;
    public static final double TRIDENT_WATER_DRAG = 0.99D;
    public static final double FISHING_ROD_DRAG = 0.92D;
    public static final double LAUNCH_Y_OFFSET = -0.1D;
    public static final double ARROW_SPEED = 3.0D;
    public static final double CROSSBOW_ARROW_SPEED = 3.15D;
    public static final double FIREWORK_ROCKET_SPEED = 1.6D;
    public static final double THROWABLE_SPEED = 1.5D;
    public static final double POTION_SPEED = 0.5D;
    public static final double EXPERIENCE_BOTTLE_SPEED = 0.7D;
    public static final double TRIDENT_SPEED = 2.5D;
    public static final double FISHING_ROD_SPEED = 1.1D;
    public static final float POTION_PITCH_OFFSET = -20.0F;
    public static final float DEFAULT_DIVERGENCE = 1.0F;

    public static final ProjectileProfile ARROW = new ProjectileProfile("arrow", ARROW_SPEED, ARROW_GRAVITY, DEFAULT_DRAG, ARROW_WATER_DRAG, LAUNCH_Y_OFFSET, 0.0F, DEFAULT_DIVERGENCE);
    public static final ProjectileProfile CROSSBOW_ARROW = new ProjectileProfile("crossbow_arrow", CROSSBOW_ARROW_SPEED, ARROW_GRAVITY, DEFAULT_DRAG, ARROW_WATER_DRAG, LAUNCH_Y_OFFSET, 0.0F, DEFAULT_DIVERGENCE);
    public static final ProjectileProfile FIREWORK_ROCKET = new ProjectileProfile("firework_rocket", FIREWORK_ROCKET_SPEED, 0.0D, DEFAULT_DRAG, DEFAULT_DRAG, -0.15D, 0.0F, DEFAULT_DIVERGENCE);
    public static final ProjectileProfile THROWABLE = new ProjectileProfile("throwable", THROWABLE_SPEED, THROWABLE_GRAVITY, DEFAULT_DRAG, DEFAULT_WATER_DRAG, LAUNCH_Y_OFFSET, 0.0F, DEFAULT_DIVERGENCE);
    public static final ProjectileProfile SNOWBALL = new ProjectileProfile("snowball", THROWABLE_SPEED, THROWABLE_GRAVITY, DEFAULT_DRAG, DEFAULT_WATER_DRAG, LAUNCH_Y_OFFSET, 0.0F, DEFAULT_DIVERGENCE);
    public static final ProjectileProfile EGG = new ProjectileProfile("egg", THROWABLE_SPEED, THROWABLE_GRAVITY, DEFAULT_DRAG, DEFAULT_WATER_DRAG, LAUNCH_Y_OFFSET, 0.0F, DEFAULT_DIVERGENCE);
    public static final ProjectileProfile ENDER_PEARL = new ProjectileProfile("ender_pearl", THROWABLE_SPEED, THROWABLE_GRAVITY, DEFAULT_DRAG, DEFAULT_WATER_DRAG, LAUNCH_Y_OFFSET, 0.0F, DEFAULT_DIVERGENCE);
    public static final ProjectileProfile POTION = new ProjectileProfile("potion", POTION_SPEED, POTION_GRAVITY, DEFAULT_DRAG, DEFAULT_WATER_DRAG, LAUNCH_Y_OFFSET, POTION_PITCH_OFFSET, DEFAULT_DIVERGENCE);
    public static final ProjectileProfile LINGERING_POTION = POTION;
    public static final ProjectileProfile SPLASH_POTION = POTION;
    public static final ProjectileProfile EXPERIENCE_BOTTLE = new ProjectileProfile("experience_bottle", EXPERIENCE_BOTTLE_SPEED, EXPERIENCE_BOTTLE_GRAVITY, DEFAULT_DRAG, DEFAULT_WATER_DRAG, LAUNCH_Y_OFFSET, POTION_PITCH_OFFSET, DEFAULT_DIVERGENCE);
    public static final ProjectileProfile TRIDENT = new ProjectileProfile("trident", TRIDENT_SPEED, TRIDENT_GRAVITY, DEFAULT_DRAG, TRIDENT_WATER_DRAG, LAUNCH_Y_OFFSET, 0.0F, DEFAULT_DIVERGENCE);
    public static final ProjectileProfile FISHING_ROD = new ProjectileProfile("fishing_rod", FISHING_ROD_SPEED, FISHING_ROD_GRAVITY, FISHING_ROD_DRAG, FISHING_ROD_DRAG, 0.0D, 0.0F, DEFAULT_DIVERGENCE);

    private ProjectilePhysicsUtils() {
    }

    public static ProjectileProfile profileForItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        if (stack.is(Items.BOW)) return ARROW;
        if (stack.is(Items.CROSSBOW)) return CROSSBOW_ARROW;
        if (stack.is(Items.FIREWORK_ROCKET)) return FIREWORK_ROCKET;
        if (stack.is(Items.SNOWBALL)) return SNOWBALL;
        if (stack.is(Items.EGG)) return EGG;
        if (stack.is(Items.ENDER_PEARL)) return ENDER_PEARL;
        if (stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION)) return POTION;
        if (stack.is(Items.EXPERIENCE_BOTTLE)) return EXPERIENCE_BOTTLE;
        if (stack.is(Items.TRIDENT)) return TRIDENT;
        if (stack.is(Items.FISHING_ROD)) return FISHING_ROD;
        return null;
    }

    public static Vec3 getLaunchOrigin(Entity entity, ProjectileProfile profile) {
        if (entity == null || profile == null) {
            return Vec3.ZERO;
        }

        return entity.getEyePosition().add(0.0D, profile.launchYOffset(), 0.0D);
    }

    public static Vec3 inheritedVelocity(Entity entity) {
        if (entity == null) {
            return Vec3.ZERO;
        }

        Vec3 velocity = entity.getDeltaMovement();
        return new Vec3(velocity.x, entity.onGround() ? 0.0D : velocity.y, velocity.z);
    }

    public static Vec3 getLaunchDirection(Rotation rotation, ProjectileProfile profile) {
        if (rotation == null || profile == null) {
            return Vec3.ZERO;
        }

        float yaw = rotation.yaw() * ((float) Math.PI / 180.0F);
        float pitch = rotation.pitch() * ((float) Math.PI / 180.0F);
        float pitchOffset = (rotation.pitch() + profile.pitchOffset()) * ((float) Math.PI / 180.0F);

        double x = -Mth.sin(yaw) * Mth.cos(pitch);
        double y = -Mth.sin(pitchOffset);
        double z = Mth.cos(yaw) * Mth.cos(pitch);
        Vec3 direction = new Vec3(x, y, z);
        return direction.lengthSqr() < 1.0E-7D ? Vec3.ZERO : direction.normalize();
    }

    public static Vec3 getLaunchVelocity(Rotation rotation, ProjectileProfile profile) {
        return getLaunchDirection(rotation, profile).scale(profile.speed());
    }

    public static Vec3 getLaunchVelocity(Entity entity, Rotation rotation, ProjectileProfile profile) {
        return getLaunchVelocity(rotation, profile).add(inheritedVelocity(entity));
    }

    public static Vec3 getLaunchVelocity(Entity entity, Rotation rotation, ProjectileProfile profile, double speed) {
        return getLaunchVelocity(rotation, profile, speed).add(inheritedVelocity(entity));
    }

    public static Vec3 getLaunchVelocity(Rotation rotation, ProjectileProfile profile, double speed) {
        double resolvedSpeed = speed > 0.0D ? speed : profile.speed();
        return getLaunchDirection(rotation, profile).scale(resolvedSpeed);
    }

    public static double effectiveSpeed(Vec3 origin, Vec3 target, Vec3 inheritedVelocity, double baseSpeed, double baseMultiplier, double minSpeed, double maxBonus) {
        Vec3 toTarget = target.subtract(origin);
        double scaledBaseSpeed = baseSpeed * baseMultiplier;
        if (toTarget.lengthSqr() < 1.0E-6D) {
            return Math.max(minSpeed, scaledBaseSpeed);
        }

        double inheritedAlongPath = inheritedVelocity.dot(toTarget.normalize());
        return clamp(scaledBaseSpeed + inheritedAlongPath, minSpeed, baseSpeed + maxBonus);
    }

    public static Vec3 compensateForInheritedVelocity(Vec3 point, Vec3 inheritedVelocity, double drag, int impactTick) {
        if (inheritedVelocity.lengthSqr() < 1.0E-6D || impactTick <= 0) {
            return point;
        }

        return point.subtract(inheritedVelocity.scale(dragDisplacementScale(drag, impactTick)));
    }

    public static double dragDisplacementScale(double drag, int impactTick) {
        if (impactTick <= 0) {
            return 0.0D;
        }

        if (Math.abs(drag - 1.0D) < 1.0E-6D) {
            return impactTick;
        }

        return (1.0D - Math.pow(drag, impactTick)) / (1.0D - drag);
    }

    public static double ballisticRoot(double speed, double gravity, double horizontal, double diffY) {
        double speedSq = speed * speed;
        return speedSq * speedSq - gravity * (gravity * horizontal * horizontal + 2.0D * diffY * speedSq);
    }

    public static List<Rotation> buildBallisticRotations(ProjectileProfile profile, Vec3 origin, Vec3 point, double speed, Vec3 inheritedVelocity, int impactTick, float[] pitchOffsets) {
        List<Rotation> rotations = new ArrayList<>();
        addBallisticRotations(rotations, profile, origin, point, speed, pitchOffsets);

        Vec3 compensatedPoint = compensateForInheritedVelocity(point, inheritedVelocity, profile.drag(), impactTick);
        if (compensatedPoint.distanceToSqr(point) > 1.0E-4D) {
            addBallisticRotations(rotations, profile, origin, compensatedPoint, speed, pitchOffsets);
        }

        return rotations;
    }

    public static void addBallisticRotations(List<Rotation> rotations, ProjectileProfile profile, Vec3 origin, Vec3 point, double speed, float[] pitchOffsets) {
        if (rotations == null || profile == null || origin == null || point == null) {
            return;
        }

        if (profile.pitchOffset() != 0.0F) {
            addRotationCandidate(rotations, RotationMath.toRotation(origin, point), 0.25F, 0.25F);
            return;
        }

        double diffX = point.x - origin.x;
        double diffY = point.y - origin.y;
        double diffZ = point.z - origin.z;
        double horizontal = Math.hypot(diffX, diffZ);
        float yaw = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0F);

        if (horizontal < 1.0E-4D) {
            addPitchSweep(rotations, yaw, diffY > 0.0D ? -90.0F : 90.0F, pitchOffsets);
            return;
        }

        double speedSq = speed * speed;
        double root = ballisticRoot(speed, profile.gravity(), horizontal, diffY);
        if (root >= 0.0D) {
            double sqrt = Math.sqrt(root);
            addPitchSweep(rotations, yaw, (float) -Math.toDegrees(Math.atan((speedSq - sqrt) / (profile.gravity() * horizontal))), pitchOffsets);
            addPitchSweep(rotations, yaw, (float) -Math.toDegrees(Math.atan((speedSq + sqrt) / (profile.gravity() * horizontal))), pitchOffsets);
        }

        Rotation direct = RotationMath.toRotation(origin, point);
        addPitchSweep(rotations, direct.yaw(), direct.pitch(), pitchOffsets);
    }

    public static boolean addRotationCandidate(List<Rotation> rotations, Rotation rotation) {
        return addRotationCandidate(rotations, rotation, 0.25F, 0.25F);
    }

    public static boolean addRotationCandidate(List<Rotation> rotations, Rotation rotation, float yawEpsilon, float pitchEpsilon) {
        if (rotations == null || rotation == null) {
            return false;
        }

        for (Rotation existing : rotations) {
            if (Math.abs(Mth.wrapDegrees(existing.yaw() - rotation.yaw())) < yawEpsilon
                    && Math.abs(existing.pitch() - rotation.pitch()) < pitchEpsilon) {
                return false;
            }
        }

        rotations.add(rotation);
        return true;
    }

    public static void addPitchSweep(List<Rotation> rotations, float yaw, float pitch, float[] pitchOffsets) {
        if (rotations == null) {
            return;
        }

        if (pitchOffsets == null || pitchOffsets.length == 0) {
            rotations.add(new Rotation(yaw, Mth.clamp(pitch, -90.0F, 90.0F)).normalized());
            return;
        }

        for (float offset : pitchOffsets) {
            float candidatePitch = Mth.clamp(pitch + offset, -90.0F, 90.0F);
            rotations.add(new Rotation(yaw, candidatePitch).normalized());
        }
    }

    public static SimulationResult simulateTrajectory(Level level, Entity shooter, ProjectileProfile profile, Rotation rotation, double speed, List<AABB> targetBoxes, int maxTicks, double hitboxExpand, double hitCenterWeight, double hitMarginWeight, double hitRotationWeight, double missDistanceWeight, double missRotationWeight, double blockedPenalty) {
        if (level == null || shooter == null || profile == null || rotation == null || targetBoxes == null || targetBoxes.isEmpty()) {
            return new SimulationResult(false, Double.MAX_VALUE, Double.MAX_VALUE, false, null);
        }

        double resolvedSpeed = speed > 0.0D ? speed : profile.speed();
        Vec3 pos = getLaunchOrigin(shooter, profile);
        Vec3 velocity = getLaunchVelocity(rotation, profile, resolvedSpeed).add(inheritedVelocity(shooter));
        double closestDistance = Double.MAX_VALUE;
        double bestScore = Double.MAX_VALUE;
        double rotationDelta = currentRotation().distanceTo(rotation).length();

        for (int tick = 0; tick < maxTicks; tick++) {
            Vec3 next = pos.add(velocity);
            AABB realTargetBox = targetBoxes.get(Math.min(tick + 1, targetBoxes.size() - 1));
            AABB targetBox = realTargetBox.inflate(hitboxExpand);
            BlockHitResult blockHit = level.clip(new ClipContext(
                    pos,
                    next,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    shooter
            ));
            double blockDistanceSq = blockHit.getType() == HitResult.Type.MISS
                    ? Double.POSITIVE_INFINITY
                    : pos.distanceToSqr(blockHit.getLocation());

            IntersectionResult intersection = getIntersection(pos, next, targetBox);
            if (intersection.hit()) {
                Vec3 entryPoint = pos.lerp(next, intersection.entry());
                double targetEntryDistanceSq = pos.distanceToSqr(entryPoint);
                if (blockDistanceSq <= targetEntryDistanceSq) {
                    return new SimulationResult(false, closestDistance, bestScore + blockedPenalty, true, null);
                }

                if (blockHit.getType() != HitResult.Type.MISS) {
                    var realIntersection = realTargetBox.clip(pos, next);
                    if (realIntersection.isEmpty() || blockDistanceSq <= pos.distanceToSqr(realIntersection.get())) {
                        return new SimulationResult(false, closestDistance, bestScore + blockedPenalty, true, null);
                    }
                }

                Vec3 hitPoint = pos.lerp(next, intersection.mid());
                double centerDistance = hitPoint.distanceTo(realTargetBox.getCenter());
                double margin = getHitMargin(hitPoint, realTargetBox);
                double score = centerDistance * hitCenterWeight - margin * hitMarginWeight + rotationDelta * hitRotationWeight;
                return new SimulationResult(true, 0.0D, score, false, realTargetBox);
            }

            double distance = distanceToBox(next, targetBox);
            if (distance < closestDistance) {
                closestDistance = distance;
                bestScore = distance * missDistanceWeight + rotationDelta * missRotationWeight;
            }

            if (blockHit.getType() != HitResult.Type.MISS) {
                return new SimulationResult(false, closestDistance, bestScore + blockedPenalty, true, null);
            }

            pos = next;
            velocity = profile.stepVelocity(velocity, level.getFluidState(BlockPos.containing(pos)).is(net.minecraft.tags.FluidTags.WATER), shooter.isNoGravity());
        }

        return new SimulationResult(false, closestDistance, bestScore, false, null);
    }

    public static Trajectory traceTrajectory(Level level, Entity shooter, ProjectileProfile profile, Rotation rotation, double speed, int maxTicks) {
        if (level == null || shooter == null || profile == null || rotation == null) {
            return new Trajectory(List.of());
        }

        double resolvedSpeed = speed > 0.0D ? speed : profile.speed();
        Vec3 pos = getLaunchOrigin(shooter, profile);
        Vec3 velocity = getLaunchVelocity(rotation, profile, resolvedSpeed).add(inheritedVelocity(shooter));
        List<TrajectoryStep> steps = new ArrayList<>(Math.max(1, maxTicks + 1));

        steps.add(new TrajectoryStep(0, pos, velocity));
        for (int tick = 1; tick <= maxTicks; tick++) {
            pos = pos.add(velocity);
            velocity = profile.stepVelocity(velocity, level.getFluidState(BlockPos.containing(pos)).is(net.minecraft.tags.FluidTags.WATER), shooter.isNoGravity());
            steps.add(new TrajectoryStep(tick, pos, velocity));
        }

        return new Trajectory(steps);
    }

    public static int estimateImpactTick(Vec3 origin, Vec3 target, ProjectileProfile profile, Vec3 inheritedVelocity, double baseMultiplier, double minSpeed, double maxBonus, int maxTick) {
        double effectiveSpeed = effectiveSpeed(origin, target, inheritedVelocity, profile.speed(), baseMultiplier, minSpeed, maxBonus);
        double distance = origin.distanceTo(target);
        for (int tick = 1; tick <= maxTick; tick++) {
            double travel = effectiveSpeed * dragDisplacementScale(profile.drag(), tick);
            if (travel >= distance) {
                return tick;
            }
        }

        return Math.max(1, maxTick);
    }

    public static double distanceToBox(Vec3 point, AABB box) {
        double x = Math.max(0.0D, Math.max(box.minX - point.x, point.x - box.maxX));
        double y = Math.max(0.0D, Math.max(box.minY - point.y, point.y - box.maxY));
        double z = Math.max(0.0D, Math.max(box.minZ - point.z, point.z - box.maxZ));
        return Math.sqrt(x * x + y * y + z * z);
    }

    public static double getHitMargin(Vec3 point, AABB box) {
        double xMargin = Math.min(point.x - box.minX, box.maxX - point.x);
        double yMargin = Math.min(point.y - box.minY, box.maxY - point.y);
        double zMargin = Math.min(point.z - box.minZ, box.maxZ - point.z);
        return Math.max(0.0D, Math.min(xMargin, Math.min(yMargin, zMargin)));
    }

    public static IntersectionResult getIntersection(Vec3 start, Vec3 end, AABB box) {
        double tMin = 0.0D;
        double tMax = 1.0D;
        double[] startValues = {start.x, start.y, start.z};
        double[] directionValues = {end.x - start.x, end.y - start.y, end.z - start.z};
        double[] minValues = {box.minX, box.minY, box.minZ};
        double[] maxValues = {box.maxX, box.maxY, box.maxZ};

        for (int axis = 0; axis < 3; axis++) {
            double direction = directionValues[axis];
            if (Math.abs(direction) < 1.0E-8D) {
                if (startValues[axis] < minValues[axis] || startValues[axis] > maxValues[axis]) {
                    return IntersectionResult.MISS;
                }
                continue;
            }

            double inverse = 1.0D / direction;
            double t1 = (minValues[axis] - startValues[axis]) * inverse;
            double t2 = (maxValues[axis] - startValues[axis]) * inverse;
            if (t1 > t2) {
                double tmp = t1;
                t1 = t2;
                t2 = tmp;
            }

            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) {
                return IntersectionResult.MISS;
            }
        }

        return new IntersectionResult(true, tMin, tMax, (tMin + tMax) * 0.5D);
    }

    private static Rotation currentRotation() {
        if (Core.INSTANCE.isInitialized()) return Core.INSTANCE.getRotationManager().getEffectiveRotation();
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player == null
                ? new Rotation(0.0f, 0.0f)
                : new Rotation(minecraft.player.getYRot(), minecraft.player.getXRot());
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public record ProjectileProfile(String name, double speed, double gravity, double drag, double waterDrag, double launchYOffset, float pitchOffset, float divergence) {
        public Vec3 velocity(Rotation rotation) {
            return velocity(rotation, speed);
        }

        public Vec3 velocity(Rotation rotation, double speed) {
            return ProjectilePhysicsUtils.getLaunchDirection(rotation, this).scale(speed);
        }

        public Vec3 stepVelocity(Vec3 velocity) {
            return stepVelocity(velocity, false, false);
        }

        public Vec3 stepVelocity(Vec3 velocity, boolean touchingWater, boolean noGravity) {
            Vec3 dragged = velocity.scale(touchingWater ? waterDrag : drag);
            return noGravity ? dragged : dragged.add(0.0D, -gravity, 0.0D);
        }

        public double ballisticRoot(double speed, double horizontal, double diffY) {
            return ProjectilePhysicsUtils.ballisticRoot(speed, gravity, horizontal, diffY);
        }

        public double dragDisplacementScale(int impactTick) {
            return ProjectilePhysicsUtils.dragDisplacementScale(drag, impactTick);
        }
    }

    public record TrajectoryStep(int tick, Vec3 position, Vec3 velocity) {
    }

    public record Trajectory(List<TrajectoryStep> steps) {
        public Vec3 positionAt(int tick) {
            if (steps.isEmpty()) {
                return Vec3.ZERO;
            }

            return steps.get(Math.min(tick, steps.size() - 1)).position();
        }

        public Vec3 velocityAt(int tick) {
            if (steps.isEmpty()) {
                return Vec3.ZERO;
            }

            return steps.get(Math.min(tick, steps.size() - 1)).velocity();
        }
    }

    public record SimulationResult(boolean hit, double closestDistance, double score, boolean blocked, AABB predictedBox) {
    }

    public record IntersectionResult(boolean hit, double entry, double exit, double mid) {
        private static final IntersectionResult MISS = new IntersectionResult(false, 0.0D, 0.0D, 0.0D);
    }
}
