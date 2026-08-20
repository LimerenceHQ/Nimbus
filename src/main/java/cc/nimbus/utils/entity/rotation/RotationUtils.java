package cc.nimbus.utils.entity.rotation;

import cc.nimbus.core.Core;
import cc.nimbus.features.rotation.Rotation;
import cc.nimbus.features.rotation.RotationRaycast;
import cc.nimbus.utils.client.IMinecraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class RotationUtils implements IMinecraft {
    public record EntityData(Vec3 point, double distance) {}

    public static Rotation getServerRotation() {
        return Core.INSTANCE.getRotationManager().getServerRotation();
    }

    private static List<Vec3> getOptimalPointOnFace(AABB box, Direction face, Vec3 eyePos, float lastYaw) {
        List<Vec3> points = new ArrayList<>();
        double edgeInset = 0.1;

        if (face.getAxis() == Direction.Axis.Y) {
            double planeY = (face == Direction.UP) ? box.maxY : box.minY;
            double clampedX = Mth.clamp(eyePos.x, box.minX + edgeInset, box.maxX - edgeInset);
            double clampedZ = Mth.clamp(eyePos.z, box.minZ + edgeInset, box.maxZ - edgeInset);
            points.add(new Vec3(clampedX, planeY, clampedZ));
            double centerX = (box.minX + box.maxX) * 0.5;
            double centerZ = (box.minZ + box.maxZ) * 0.5;
            points.add(new Vec3(centerX, planeY, centerZ));

        } else {
            double centerX = (face.getAxis() == Direction.Axis.X) ? (face == Direction.EAST ? box.maxX : box.minX) : (box.minX + box.maxX) * 0.5;
            double centerY = (box.minY + box.maxY) * 0.5;
            double centerZ = (face.getAxis() == Direction.Axis.Z) ? (face == Direction.SOUTH ? box.maxZ : box.minZ) : (box.minZ + box.maxZ) * 0.5;
            points.add(new Vec3(centerX, centerY, centerZ));
            double yawRad = Math.toRadians(lastYaw);
            double dirX = -Math.sin(yawRad);
            double dirZ = Math.cos(yawRad);

            double bestX = centerX;
            double bestZ = centerZ;

            if (face.getAxis() == Direction.Axis.Z) {
                double planeZ = (face == Direction.SOUTH) ? box.maxZ : box.minZ;
                if (Math.abs(dirZ) > 1e-4) {
                    double t = (planeZ - eyePos.z) / dirZ;
                    if (t > 0) {
                        double intersectX = eyePos.x + t * dirX;
                        bestX = Mth.clamp(intersectX, box.minX + edgeInset, box.maxX - edgeInset);
                        bestZ = planeZ;
                    }
                }
            } else if (face.getAxis() == Direction.Axis.X) {
                double planeX = (face == Direction.EAST) ? box.maxX : box.minX;
                if (Math.abs(dirX) > 1e-4) {
                    double t = (planeX - eyePos.x) / dirX;
                    if (t > 0) {
                        double intersectZ = eyePos.z + t * dirZ;
                        bestZ = Mth.clamp(intersectZ, box.minZ + edgeInset, box.maxZ - edgeInset);
                        bestX = planeX;
                    }
                }
            }
            for (double y = box.minY + 0.1; y < box.maxY; y += 0.4) {
                points.add(new Vec3(bestX, y, bestZ));
            }
            points.add(new Vec3(bestX, box.maxY - 0.1, bestZ));
            points.add(new Vec3(bestX, box.minY + 0.1, bestZ));
        }

        return points;
    }

    public static Rotation calculate(Vec3 to) {
        Minecraft client = Minecraft.getInstance();
        Player player = client.player;

        if (player == null) return new Rotation(0, 0);

        Vec3 from = new Vec3(player.getX(), player.getEyeY(), player.getZ());
        return calculate(from, to);
    }
    public static Rotation calculate(Vec3 from, Vec3 to) {
        Vec3 diff = to.subtract(from);
        double horizontalDistance = Math.hypot(diff.x, diff.z);
        float yaw = (float) (Mth.atan2(diff.z, diff.x) * (180F / Math.PI)) - 90.0F;
        float pitch = (float) (-(Mth.atan2(diff.y, horizontalDistance) * (180F / Math.PI)));
        return new Rotation(yaw, pitch);
    }

    public static Rotation getClosestToBlockFace(BlockPos pos, Direction face, float lastYaw, float lastPitch) {
        if (mc.player == null || mc.level == null) return null;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return new Rotation(lastYaw, 80.5f);
        Rotation currentRot = new Rotation(lastYaw, lastPitch);
        if (RotationRaycast.didHitBlockFace(currentRot, pos, face, true)) {
            return currentRot;
        }

        BlockState state = player.level().getBlockState(pos);
        VoxelShape shape = state.getShape(player.level(), pos);
        if (shape.isEmpty()) shape = Shapes.block();

        List<AABB> boxes = shape.toAabbs();

        Rotation bestRot = null;
        double minDiff = Double.MAX_VALUE;

        Vec3 eyePos = player.getEyePosition();

        for (AABB box : boxes) {
            AABB worldBox = box.move(pos);
            Vec3 optimalPoint = null;
            List<Vec3> hitPoints = new ArrayList<>();
            List<Vec3> optimalPoints = getOptimalPointOnFace(worldBox, face, eyePos, lastYaw);
            double minYawDiff = Double.MAX_VALUE;
            for (Vec3 point : optimalPoints) {
                Rotation rot = calculate(eyePos, point);
                if (Math.abs(rot.pitch()) > 89.9f) {
                    rot.setYaw(lastYaw);
                }
                if (RotationRaycast.didHitBlockFace(rot, pos, face, true)) {
                    double diff = Math.abs(normalizeYaw(rot.yaw() - lastYaw));
                    if (diff < minYawDiff) {
                        minYawDiff = diff;
                        bestRot = rot;
                        optimalPoint = point;
                    }
                }
            }
            if (optimalPoint != null) {
                hitPoints.add(optimalPoint);
            }
            Vec3 projectedPoint = getProjectedPointOnFace(worldBox, face, eyePos, lastYaw, 0.05);
            if (tryHit(projectedPoint, pos, face, eyePos)) {
                hitPoints.add(projectedPoint);
            }
            if (!hitPoints.isEmpty()) {
                for (Vec3 hitPoint : hitPoints) {
                    Rotation rot = calculate(eyePos, hitPoint);
                    if (Math.abs(rot.pitch()) > 89.9f) {
                        rot.setYaw(lastYaw);
                    }

                    double yawDiff = Math.abs(normalizeYaw(rot.yaw() - lastYaw));

                    if (yawDiff < minDiff) {
                        minDiff = yawDiff;
                        bestRot = rot;
                    }
                }
            }
        }

        return bestRot != null ? bestRot : new Rotation(lastYaw, 80.5f);
    }

    private static Vec3 getProjectedPointOnFace(AABB box, Direction face, Vec3 eye, float lastYaw, double epsilon) {
        double cx = (face.getAxis() == Direction.Axis.X)
                ? (face == Direction.EAST ? box.maxX : box.minX)
                : (box.minX + box.maxX) * 0.5;
        double cy = (face.getAxis() == Direction.Axis.Y)
                ? (face == Direction.UP ? box.maxY : box.minY)
                : (box.minY + box.maxY) * 0.5;
        double cz = (face.getAxis() == Direction.Axis.Z)
                ? (face == Direction.SOUTH ? box.maxZ : box.minZ)
                : (box.minZ + box.maxZ) * 0.5;

        Vec3 center = new Vec3(cx, cy, cz);
        Vec3 u, v;
        switch (face.getAxis()) {
            case X -> { u = new Vec3(0, 1, 0); v = new Vec3(0, 0, 1); }
            case Y -> { u = new Vec3(1, 0, 0); v = new Vec3(0, 0, 1); }
            default -> { u = new Vec3(1, 0, 0); v = new Vec3(0, 1, 0); } // Z Axis
        }

        double halfU = (u.x != 0 ? (box.maxX - box.minX) : u.y != 0 ? (box.maxY - box.minY) : (box.maxZ - box.minZ)) * 0.5;
        double halfV = (v.x != 0 ? (box.maxX - box.minX) : v.y != 0 ? (box.maxY - box.minY) : (box.maxZ - box.minZ)) * 0.5;
        double rad = Math.toRadians(lastYaw + 90);
        Vec3 dir = new Vec3(Math.cos(rad), 0, Math.sin(rad));

        Vec3 normal = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
        double denom = normal.dot(dir);

        if (Math.abs(denom) > 1e-6) {
            double t = normal.dot(center.subtract(eye)) / denom;
            if (t > 0) {
                Vec3 hit = eye.add(dir.scale(t));
                double du = hit.subtract(center).dot(u);
                double dv = hit.subtract(center).dot(v);

                if (Math.abs(du) <= halfU && Math.abs(dv) <= halfV) {
                    return adjustInsideFace(center, u, v, du, dv, halfU, halfV, epsilon);
                }
            }
        }
        return projectToFaceBoundary(eye, dir, center, u, v, halfU, halfV, epsilon);
    }

    private static boolean tryHit(Vec3 target, BlockPos pos, Direction face, Vec3 eyePos) {
        Rotation rot = calculate(eyePos, target);
        return RotationRaycast.didHitBlockFace(rot, pos, face, true);
    }

    private static double normalizeYaw(double yaw) {
        return ((yaw + 180) % 360 + 360) % 360 - 180;
    }

    public static double getRotationDifference(Rotation a, Rotation b) {
        return Math.hypot(getAngleDifference(a.yaw(), b.yaw()), a.pitch() - b.pitch());
    }

    public static float getAngleDifference(float a, float b) {
        return ((((a - b) % 360F) + 540F) % 360F) - 180F;
    }

    public static Rotation toRotation(Vec3 vec, boolean predict) {
        if (mc.player == null || vec == null) {
            return new Rotation(0f, 0f);
        }

        Vec3 eyesPos = mc.player.getEyePosition();

        if (predict) {
            eyesPos = eyesPos.add(mc.player.getDeltaMovement());

        }

        double diffX = vec.x - eyesPos.x;
        double diffY = vec.y - eyesPos.y;
        double diffZ = vec.z - eyesPos.z;

        float yaw = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90f);
        float pitch = Mth.wrapDegrees(-(float) Math.toDegrees(Math.atan2(diffY, Math.sqrt(diffX * diffX + diffZ * diffZ))));

        return new Rotation(yaw, pitch);
    }

    public static Rotation toRotation(Vec3 vec, float partialTicks) {
        if (mc.player == null || vec == null) {
            return new Rotation(0f, 0f);
        }

        final Vec3 eyesPos = new Vec3(mc.player.getX(), mc.player.getBoundingBox().minY +
                mc.player.getEyeHeight(mc.player.getPose()), mc.player.getZ()).add(mc.player.getDeltaMovement().x * partialTicks, mc.player.getDeltaMovement().y * partialTicks, mc.player.getDeltaMovement().z * partialTicks);


        double diffX = vec.x - eyesPos.x;
        double diffY = vec.y - eyesPos.y;
        double diffZ = vec.z - eyesPos.z;

        float yaw = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90f);
        float pitch = Mth.wrapDegrees(-(float) Math.toDegrees(Math.atan2(diffY, Math.sqrt(diffX * diffX + diffZ * diffZ))));

        return new Rotation(yaw, pitch);
    }

    public static Rotation toRotation(Vec3 origin, Vec3 point) {
        if (mc.player == null) return new Rotation(0,0);

        double diffX = point.x - origin.x;
        double diffY = point.y - origin.y;
        double diffZ = point.z - origin.z;

        float yaw = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90f);
        float pitch = Mth.wrapDegrees(-(float) Math.toDegrees(Math.atan2(diffY, Math.hypot(diffX, diffZ))));

        return new Rotation(yaw, pitch);
    }

    public static Rotation toRotation(Vec3 point) {
        if (mc.player == null) return new Rotation(0,0);

        return toRotation(mc.player.getEyePosition(),point);
    }

    public static Rotation getRotationBlock(BlockPos pos) {
        if (mc.player == null) {
            return Rotation.ZERO;
        }

        Vec3 eyes = mc.player.position().add(0.0D, mc.player.getEyeHeight(mc.player.getPose()), 0.0D);
        Vec3 target = new Vec3(pos.getX() + 0.51D, pos.getY() + 0.51D, pos.getZ() + 0.51D);
        return toRotation(eyes, target);
    }

    public static Rotation getRotationBlock(final BlockPos pos, Direction direction) {
        Vec3 position = new Vec3(pos.getX() + 0.51 + direction.getStepX() * 0.5F, pos.getY() + 0.51 + direction.getStepY() * 0.5F, pos.getZ() + 0.51 + direction.getStepZ() * 0.5F);
        return getRotationsByVec(mc.player.position().add(0.0, mc.player.getEyeHeight(mc.player.getPose()), 0.0), position);
    }
    public static Rotation getRotationsByVec(final Vec3 origin, final Vec3 position) {
        final Vec3 difference = position.subtract(origin);
        final double distance = Math.hypot(difference.x, difference.z);
        final float yaw = (float) Math.toDegrees(Math.atan2(difference.z, difference.x)) - 90.0f;
        final float pitch = (float) (-Math.toDegrees(Math.atan2(difference.y, distance)));
        return new Rotation(yaw, pitch);
    }
    public static List<AABB> getBlockBoundingBoxes(BlockPos pos) {
        if (mc.level == null || pos == null) {
            return Collections.emptyList();
        }

        VoxelShape shape = mc.level.getBlockState(pos).getShape(mc.level, pos);
        if (shape.isEmpty()) {
            return Collections.singletonList(new AABB(pos));
        }

        List<AABB> boxes = new ArrayList<>();
        for (AABB localBox : shape.toAabbs()) {
            boxes.add(localBox.move(pos));
        }
        return boxes;
    }

    public static AABB getEntityBoundingBox(Entity entity) {
        double borderSize = entity.getPickRadius();
        AABB aabb = entity.getBoundingBox();
        return aabb.inflate(borderSize);
    }

    public static Vec3 getNearestPointBB(Vec3 eye, AABB aabb) {
        double x = eye.x;
        double y = eye.y;
        double z = eye.z;

        if (x > aabb.maxX) x = aabb.maxX;
        else if (x < aabb.minX) x = aabb.minX;

        if (y > aabb.maxY) y = aabb.maxY;
        else if (y < aabb.minY) y = aabb.minY;

        if (z > aabb.maxZ) z = aabb.maxZ;
        else if (z < aabb.minZ) z = aabb.minZ;

        return new Vec3(x, y, z);
    }

    public static double getDistanceToEntityBox(Entity from, Entity to) {
        Vec3 eyes = from.getEyePosition();
        Vec3 nearest = getNearestPointBB(eyes, getEntityBoundingBox(to));
        return eyes.distanceTo(nearest);
    }

    public static boolean noBlocksBlocked(Level level, Vec3 start, Vec3 end, Entity entity) {
        ClipContext context = new ClipContext(
                start,
                end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                entity
        );

        BlockHitResult hitResult = level.clip(context);
        return hitResult.getType() == HitResult.Type.MISS;
    }

    public static EntityData getInteractiveEntityData(Entity from,Entity to, double throughWallsRange) {
        Vec3 eyes = from.getEyePosition();
        Level level = from.level();
        AABB aabb = getEntityBoundingBox(to);

        Vec3 nearest = getNearestPointBB(eyes,aabb);
        double nearestDist = getDistanceToEntityBox(from,to);

        if (nearestDist <= throughWallsRange || noBlocksBlocked(level,eyes,nearest,from))
            return new EntityData(nearest,nearestDist);
        
        double best = Double.MAX_VALUE;
        Vec3 bestPoint = null;

        double xSearch = 0.15;
        while (xSearch < 0.85) {

            double ySearch = 0.15;
            while (ySearch < 1.0) {

                double zSearch = 0.15;
                while (zSearch < 0.85) {

                    Vec3 sample = new Vec3(
                            aabb.minX + (aabb.maxX - aabb.minX) * xSearch,
                            aabb.minY + (aabb.maxY - aabb.minY) * ySearch,
                            aabb.minZ + (aabb.maxZ - aabb.minZ) * zSearch
                    );

                    double dist = eyes.distanceTo(sample);

                    if (dist >= best) {
                        zSearch += 0.1;
                        continue;
                    }

                    if (dist <= throughWallsRange ||
                            noBlocksBlocked(level, eyes, sample, from)) {
                        best = dist;
                        bestPoint = sample;
                    }

                    zSearch += 0.1;
                }
                ySearch += 0.1;
            }
            xSearch += 0.1;
        }

        return new EntityData(bestPoint,best);
    }

    private static Vec3 adjustInsideFace(Vec3 center, Vec3 u, Vec3 v, double du, double dv, double halfU, double halfV, double epsilon) {
        if (Math.abs(du) >= halfU - 1e-4) {
            du = Math.copySign(halfU - epsilon, du);
        }
        if (Math.abs(dv) >= halfV - 1e-4) {
            dv = Math.copySign(halfV - epsilon, dv);
        }
        return center.add(u.scale(du)).add(v.scale(dv));
    }

    private static Vec3 projectToFaceBoundary(Vec3 eye, Vec3 dir, Vec3 center, Vec3 u, Vec3 v, double halfU, double halfV, double epsilon) {
        Vec3 proj = eye.add(dir.scale(1000));
        double du = proj.subtract(center).dot(u);
        double dv = proj.subtract(center).dot(v);
        du = Math.max(-halfU, Math.min(halfU, du));
        dv = Math.max(-halfV, Math.min(halfV, dv));
        return adjustInsideFace(center, u, v, du, dv, halfU, halfV, epsilon);
    }
}
