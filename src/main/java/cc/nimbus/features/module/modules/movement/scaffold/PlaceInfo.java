package cc.nimbus.features.module.modules.movement.scaffold;

import cc.nimbus.core.Core;
import cc.nimbus.features.rotation.Rotation;
import cc.nimbus.features.rotation.RotationRaycast;
import cc.nimbus.utils.client.IMinecraft;
import cc.nimbus.utils.entity.rotation.RotationUtils;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Lightweight adapter of Fuliki's PlaceInfo for Mizore.
 * Computes rotation and hitVec targets for scaffolding, supporting Normal/Corner/Nearest modes.
 */
public class PlaceInfo implements IMinecraft {

    private static final double[] placeOffsets = new double[]{0.03125, 0.09375, 0.15625, 0.21875, 0.28125, 0.34375, 0.40625, 0.46875, 0.53125, 0.59375, 0.65625, 0.71875, 0.78125, 0.84375, 0.90625, 0.96875};


    private final Direction direction;
    private final BlockPos blockPos;

    public PlaceInfo(BlockPos blockPos, Direction direction) {
        this.blockPos = blockPos;
        this.direction = direction;
    }

    /** Expose target block position for integration. */
    public BlockPos getBlockPos() {
        return this.blockPos;
    }

    /** Expose target click-facing for integration. */
    public Direction getDirection() {
        return this.direction;
    }

    /**
     * Compute rotation and hit vector using Normal mode (center of block, slight inset).
     */
    @Nullable
    public RotationHit getRotationAndHitVec() {
        return getRotationAndHitVec(RotatePoint.Normal, null);
    }

    /**
     * Compute rotation and hit vector using provided mode. Corner/Nearest require lastRotation.
     */
    @Nullable
    public RotationHit getRotationAndHitVec(RotatePoint rotatePoint, @Nullable Rotation lastRotation) {
        if (mc.player == null || mc.level == null) return null;

        switch (rotatePoint) {
            case New: {
                var player = mc.player;
                var eyePos = player.getEyePosition();

                Vec3 hitVec = Vec3.atLowerCornerOf(this.blockPos).add(0.51, 0.51, 0.51);
                BlockHitResult result = mc.level.clip(new ClipContext(
                        eyePos,
                        hitVec,
                        ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE,
                        player
                ));

                if (result.getType() == HitResult.Type.BLOCK) {
                    if (result.getBlockPos().equals(blockPos)) {
                        return new RotationHit(
                                RotationUtils.toRotation(eyePos, hitVec),
                                hitVec
                        );
                    }
                }
                if (lastRotation != null) {
                    var hitVec2 = this.getNearestBlockCorner(this.blockPos, lastRotation);

                    BlockHitResult result2 = mc.level.clip(new ClipContext(
                            eyePos,
                            hitVec2,
                            ClipContext.Block.COLLIDER,
                            ClipContext.Fluid.NONE,
                            player
                    ));

                    if (result2.getType() == HitResult.Type.BLOCK) {
                        if (result2.getBlockPos().equals(blockPos)) {
                            return new RotationHit(RotationUtils.toRotation(mc.player.getEyePosition(), hitVec), hitVec);
                        }
                    }
                    return this.getNearestPoint(mc.player, this.blockPos, lastRotation);
                }
            }
            case Normal: {
                Vec3 hit = Vec3.atLowerCornerOf(blockPos).add(0.51, 0.51, 0.51);
                Rotation rot = RotationUtils.toRotation(hit, 1F);
                return new RotationHit(rot, hit);
            }
            case Better: {
                Vec3 hit = Vec3.atLowerCornerOf(blockPos).add(0.51, 0.51, 0.51);
                Rotation rot = RotationUtils.getClosestToBlockFace(blockPos, direction, Core.INSTANCE.getRotationManager().getServerRotation().yaw(),Core.INSTANCE.getRotationManager().getServerRotation().pitch());
                return new RotationHit(rot, hit);
            }
            case Corner: {
                if (lastRotation != null) {
                    Vec3 hit = getNearestBlockCorner(this.blockPos, lastRotation);
                    Rotation rot = RotationUtils.toRotation(hit, false);
                    return new RotationHit(rot, hit);
                }
                break;
            }
            case Nearest: {
                if (lastRotation != null) {
                    return getNearestPoint(mc.player, this.blockPos, lastRotation);
                }
                break;
            }
        }
        return null;
    }
    private Vec3 getNearestBlockCorner(BlockPos blockPos, Rotation lastRotation) {

        var inset = 0.15;
        var eyePos = mc.player.getEyePosition();
        var playerYaw = lastRotation.yaw();

        while (playerYaw < 0) {
            playerYaw += 360;
        }

        while (playerYaw >= 360) {
            playerYaw -= 360;
        }

        var corner1 = (Vec3) null;
        var corner2 = (Vec3) null;

        if (playerYaw >= 315 || playerYaw < 45) {
            corner1 = new Vec3(blockPos.getX() + 1.0 - inset, blockPos.getY() + 0.2, blockPos.getZ() + inset);
            corner2 = new Vec3(blockPos.getX() + 1.0 - inset, blockPos.getY() + 0.2, blockPos.getZ() + 1.0 - inset);
        } else if (playerYaw >= 45 && playerYaw < 135) {
            corner1 = new Vec3(blockPos.getX() + inset, blockPos.getY() + 0.2, blockPos.getZ() + 1.0 - inset);
            corner2 = new Vec3(blockPos.getX() + 1.0 - inset, blockPos.getY() + 0.2, blockPos.getZ() + 1.0 - inset);
        } else if (playerYaw >= 135 && playerYaw < 225) {
            corner1 = new Vec3(blockPos.getX() + inset, blockPos.getY() + 0.2, blockPos.getZ() + inset);
            corner2 = new Vec3(blockPos.getX() + inset, blockPos.getY() + 0.2, blockPos.getZ() + 1.0 - inset);
        } else {
            corner1 = new Vec3(blockPos.getX() + inset, blockPos.getY() + 0.2, blockPos.getZ() + inset);
            corner2 = new Vec3(blockPos.getX() + 1.0 - inset, blockPos.getY() + 0.2, blockPos.getZ() + inset);
        }

        var distance1 = eyePos.distanceToSqr(corner1);
        var distance2 = eyePos.distanceToSqr(corner2);

        return distance1 < distance2 ? corner1 : corner2;
    }

    /**
     * Sample points on the target face, pick the one with minimal rotation delta from lastRotation.
     * Falls back to lastRotation when no visible point is found.
     */
    private RotationHit getNearestPoint(LocalPlayer player, BlockPos blockPos, Rotation lastRotation) {
        var placeXs = placeOffsets;
        var placeYs = placeOffsets;
        var placeZs = placeOffsets;

        switch (this.direction) {
            case NORTH -> placeZs = new double[]{0.0};
            case EAST -> placeXs = new double[]{1.0};
            case SOUTH -> placeZs = new double[]{1.0};
            case WEST -> placeXs = new double[]{0.0};
            case DOWN -> placeYs = new double[]{0.0};
            case UP -> placeYs = new double[]{1.0};
        }

        var targetRotation = (Rotation) null;
        var hitVec = (Vec3) null;
        var lastDifference = 0.0;

        for (var x : placeXs) {
            for (var y : placeYs) {
                for (var z : placeZs) {
                    var rotation = RotationUtils.toRotation(player.getEyePosition(), Vec3.atLowerCornerOf(blockPos).add(x, y, z));
                    var rayCastBlock = RotationRaycast.rayCast(rotation, player, player.level(), 4.5, false);

                    var difference = RotationUtils.getRotationDifference(rotation, lastRotation);

                    if (targetRotation == null || difference < lastDifference) {
                        targetRotation = rotation;
                        lastDifference = difference;
                        hitVec = rayCastBlock.getLocation();
                    }
                }
            }
        }

        if (targetRotation != null) {
            return new RotationHit(targetRotation, hitVec);
        }

        return new RotationHit(lastRotation, null);
    }

    private static double rotationDifference(Rotation a, Rotation b) {
        float dyaw = MthWrap(a.yaw(), b.yaw());
        float dpitch = a.pitch() - b.pitch();
        return Math.sqrt(dyaw * dyaw + dpitch * dpitch);
    }

    private static float MthWrap(float a, float b) {
        float d = a - b;
        // Wrap to [-180, 180]
        d = (d % 360f + 540f) % 360f - 180f;
        return d;
    }

    public enum RotatePoint {
        Normal, Corner, Nearest, New, Better
    }

    /** Simple container for rotation and hit vector. */
    public record RotationHit(Rotation rotation, @Nullable Vec3 hitVec) {}
}