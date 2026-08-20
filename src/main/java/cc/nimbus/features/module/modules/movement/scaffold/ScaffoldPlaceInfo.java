package cc.nimbus.features.module.modules.movement.scaffold;

import cc.nimbus.features.rotation.Rotation;
import cc.nimbus.features.rotation.RotationMath;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public record ScaffoldPlaceInfo(BlockPos blockPos, Direction direction) {
    private static final double[] FACE_OFFSETS = {
            0.03125, 0.09375, 0.15625, 0.21875, 0.28125, 0.34375, 0.40625, 0.46875,
            0.53125, 0.59375, 0.65625, 0.71875, 0.78125, 0.84375, 0.90625, 0.96875
    };

    public RotationHit rotationAndHit(RotatePoint point, Rotation reference) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return null;

        return switch (point) {
            case NORMAL -> {
                Vec3 hit = faceCenter();
                yield new RotationHit(RotationMath.toRotation(minecraft.player.getEyePosition(), hit), hit);
            }
            case CORNER -> {
                if (reference == null) yield null;
                Vec3 hit = nearestCorner(reference);
                yield new RotationHit(RotationMath.toRotation(minecraft.player.getEyePosition(), hit), hit);
            }
            case NEAREST -> reference == null ? null : nearestVisiblePoint(reference);
        };
    }

    public Vec3 faceCenter() {
        Vec3 normal = direction.getUnitVec3();
        return Vec3.atCenterOf(blockPos).add(normal.scale(0.5));
    }

    private Vec3 nearestCorner(Rotation reference) {
        Minecraft minecraft = Minecraft.getInstance();
        double inset = 0.15;
        float yaw = (reference.yaw() % 360.0f + 360.0f) % 360.0f;
        Vec3 first;
        Vec3 second;

        if (yaw >= 315.0f || yaw < 45.0f) {
            first = new Vec3(blockPos.getX() + 1.0 - inset, blockPos.getY() + 0.2, blockPos.getZ() + inset);
            second = new Vec3(blockPos.getX() + 1.0 - inset, blockPos.getY() + 0.2, blockPos.getZ() + 1.0 - inset);
        } else if (yaw < 135.0f) {
            first = new Vec3(blockPos.getX() + inset, blockPos.getY() + 0.2, blockPos.getZ() + 1.0 - inset);
            second = new Vec3(blockPos.getX() + 1.0 - inset, blockPos.getY() + 0.2, blockPos.getZ() + 1.0 - inset);
        } else if (yaw < 225.0f) {
            first = new Vec3(blockPos.getX() + inset, blockPos.getY() + 0.2, blockPos.getZ() + inset);
            second = new Vec3(blockPos.getX() + inset, blockPos.getY() + 0.2, blockPos.getZ() + 1.0 - inset);
        } else {
            first = new Vec3(blockPos.getX() + inset, blockPos.getY() + 0.2, blockPos.getZ() + inset);
            second = new Vec3(blockPos.getX() + 1.0 - inset, blockPos.getY() + 0.2, blockPos.getZ() + inset);
        }

        Vec3 eyes = minecraft.player.getEyePosition();
        return eyes.distanceToSqr(first) < eyes.distanceToSqr(second) ? first : second;
    }

    private RotationHit nearestVisiblePoint(Rotation reference) {
        Minecraft minecraft = Minecraft.getInstance();
        Vec3 eyes = minecraft.player.getEyePosition();
        Rotation bestRotation = null;
        Vec3 bestHit = null;
        double bestDifference = Double.POSITIVE_INFINITY;

        double[] xs = direction.getAxis() == Direction.Axis.X
                ? new double[]{direction == Direction.EAST ? 1.0 : 0.0} : FACE_OFFSETS;
        double[] ys = direction.getAxis() == Direction.Axis.Y
                ? new double[]{direction == Direction.UP ? 1.0 : 0.0} : FACE_OFFSETS;
        double[] zs = direction.getAxis() == Direction.Axis.Z
                ? new double[]{direction == Direction.SOUTH ? 1.0 : 0.0} : FACE_OFFSETS;

        for (double x : xs) {
            for (double y : ys) {
                for (double z : zs) {
                    Vec3 point = Vec3.atLowerCornerOf(blockPos).add(x, y, z);
                    Rotation rotation = RotationMath.toRotation(eyes, point);
                    BlockHitResult hit = rayCast(rotation, 4.5);
                    if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(blockPos)) continue;

                    double difference = reference.distanceTo(rotation);
                    if (difference < bestDifference) {
                        bestDifference = difference;
                        bestRotation = rotation;
                        bestHit = point;
                    }
                }
            }
        }
        return bestRotation == null ? new RotationHit(reference, null) : new RotationHit(bestRotation, bestHit);
    }

    private static BlockHitResult rayCast(Rotation rotation, double range) {
        Minecraft minecraft = Minecraft.getInstance();
        Vec3 start = minecraft.player.getEyePosition();
        Vec3 end = start.add(RotationMath.direction(rotation).scale(range));
        return minecraft.level.clip(new ClipContext(
                start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, minecraft.player));
    }

    public enum RotatePoint {
        NORMAL,
        CORNER,
        NEAREST
    }

    public record RotationHit(Rotation rotation, Vec3 hitVec) {
    }
}
