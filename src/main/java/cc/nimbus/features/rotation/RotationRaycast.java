package cc.nimbus.features.rotation;

import cc.nimbus.core.Core;
import cc.nimbus.utils.client.IMinecraft;
import cc.nimbus.utils.entity.rotation.RotationUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector2f;
import java.util.Optional;
import java.util.function.Predicate;


public class RotationRaycast implements IMinecraft {
    public static Vec3 eyePos = null;

    public static Vec3 getRotationVector(float pitch, float yaw) {
        float f = pitch * ((float) Math.PI / 180.0F);
        float g = -yaw * ((float) Math.PI / 180.0F);
        float h = Mth.cos(g);
        float i = Mth.sin(g);
        float j = Mth.cos(f);
        float k = Mth.sin(f);

        return new Vec3(i * j, -k, h * j);
    }
    public static BlockPos rayCastBlock(double reach) {
        return rayCastBlock(new Rotation(Core.INSTANCE.getRotationManager().getTargetRotation().yaw(),Core.INSTANCE.getRotationManager().getTargetRotation().pitch()), reach);
    }
    public static BlockPos rayCastBlock(Rotation rotation, double reach) {

        var blockHit = rayCast(rotation, mc.player, mc.level, reach, false);

        if (blockHit != null) {
            return blockHit.getBlockPos();
        }

        return null;
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


    public static BlockHitResult rayCast(Rotation rotation, Entity entity, Level level, double maxDistance, boolean includeFluids) {
        Vec3 start = entity.getEyePosition();
        Vec3 look = getRotationVector(rotation.pitch(), rotation.yaw());
        Vec3 end = start.add(look.x * maxDistance, look.y * maxDistance, look.z * maxDistance);

        return rayCast(start, end, includeFluids, false, entity, level);
    }

    public static BlockHitResult rayCast(Vector2f toRotation, double reach) {
        Vec3 eyesPos = mc.player.getEyePosition(1f);
        Vec3 rotationVec = getVectorForRotation(toRotation);
        return mc.level.clip(new ClipContext(eyesPos,eyesPos.add(new Vec3(rotationVec.x * reach, rotationVec.y * reach, rotationVec.z * reach)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE,mc.player));
    }

    public static BlockHitResult rayCast(Vec3 start, Vec3 end, boolean includeFluids, boolean ignoreBlockWithoutBoundingBox, Entity entity, Level level) {
        return level.clip(new ClipContext(
                start,
                end,
                ignoreBlockWithoutBoundingBox ? ClipContext.Block.COLLIDER : ClipContext.Block.OUTLINE,
                includeFluids ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE,
                entity
        ));
    }

    public static Vec3 getVectorForRotation(final Vector2f rotation) {
        float yawCos = (float) Math.cos(-rotation.x * 0.017453292F - (float) Math.PI);
        float yawSin = (float) Math.sin(-rotation.x * 0.017453292F - (float) Math.PI);
        float pitchCos = (float) -Math.cos(-rotation.y * 0.017453292F);
        float pitchSin = (float) Math.sin(-rotation.y * 0.017453292F);
        return new Vec3(yawSin * pitchCos, pitchSin, yawCos * pitchCos);
    }

    public static EntityHitResult rayCastEntity(Rotation rotation, double reach, boolean throughWalls) {
        if (mc.player == null || mc.level == null) {
            return null;
        }

        Vec3 start = mc.player.getEyePosition();
        Vec3 look = getRotationVector(rotation.pitch(), rotation.yaw());
        Vec3 end = start.add(look.scale(reach));
        double maxSq = reach * reach;

        if (!throughWalls) {
            BlockHitResult blockHit = rayCast(rotation, mc.player, mc.level, reach, false);

            if (blockHit.getType() != HitResult.Type.MISS) {
                double blockDistSq = start.distanceToSqr(blockHit.getLocation());

                if (blockDistSq < maxSq) {
                    maxSq = blockDistSq;
                }
            }
        }

        AABB searchBox = mc.player.getBoundingBox()
                .expandTowards(look.scale(reach))
                .inflate(1.0D, 1.0D, 1.0D);

        return ProjectileUtil.getEntityHitResult(
                mc.player,
                start,
                end,
                searchBox,
                e -> e != mc.player && !e.isSpectator() && e.isPickable(),
                maxSq
        );
    }

    public static boolean canHit(LivingEntity entity, double range, double throughWallsRange) {
        if (mc.player == null) return false;

        RotationUtils.EntityData entityData = RotationUtils.getInteractiveEntityData(mc.player,entity,throughWallsRange);
        if (entityData.distance() <= range) return false;

        Rotation rotation = RotationUtils.toRotation(entityData.point());

        EntityHitResult entityHitResult = rayCastEntity(rotation,range,true);
        if (entityHitResult == null || entityHitResult.getType() != HitResult.Type.ENTITY) return false;

        return entityHitResult.getEntity().getId() == entity.getId();
    }

    public static boolean didHitBlockFace(Rotation rotation, BlockPos targetPos, Direction expectedFace, boolean strict) {
        return didHitBlockFace(mc.player, rotation.yaw(), rotation.pitch(), targetPos, expectedFace, strict);
    }

    public static boolean didHitBlockFace(Player player, float yaw, float pitch, BlockPos targetPos, Direction expectedFace, boolean strict) {
        if (player == null || expectedFace == null) {
            return false;
        }

        BlockHitResult result = getFacedBlock(yaw, pitch);
        return checkBlockFaceHit(result, targetPos, expectedFace, strict);
    }

    public static boolean checkBlockFaceHit(BlockHitResult result, BlockPos targetPos, Direction expectedFace, boolean strict) {
        if (result == null || expectedFace == null) {
            return false;
        }
        if (targetPos.getX() != result.getBlockPos().getX() || targetPos.getY() != result.getBlockPos().getY() || targetPos.getZ() != result.getBlockPos().getZ() || (expectedFace != result.getDirection() && strict)) {
            return false;
        }
        return true;
    }

    private static boolean isContainerBlock(BlockState state) {
        return state.getBlock() instanceof ChestBlock ||
                state.getBlock() instanceof EnderChestBlock ||
                state.getBlock() instanceof ShulkerBoxBlock;
    }

    public static BlockHitResult getFacedContainerBlock(float yaw, float pitch) {
        final double reachDistance = mc.player.blockInteractionRange();
        Vec3 startPos = eyePos;;
        Vec3 direction = getRotationVector(pitch, yaw);
        Vec3 endPos = startPos.add(direction.scale(reachDistance));
        double EPSILON = 1.0E-7;
        if (direction.x == 0) direction = new Vec3(EPSILON, direction.y, direction.z);
        if (direction.y == 0) direction = new Vec3(direction.x, EPSILON, direction.z);
        if (direction.z == 0) direction = new Vec3(direction.x, direction.y, EPSILON);

        BlockPos currentPos = BlockPos.containing(startPos);
        int stepX = (int) Math.signum(direction.x);
        int stepY = (int) Math.signum(direction.y);
        int stepZ = (int) Math.signum(direction.z);

        double nextBoundaryX = (stepX > 0) ? currentPos.getX() + 1 : currentPos.getX();
        double nextBoundaryY = (stepY > 0) ? currentPos.getY() + 1 : currentPos.getY();
        double nextBoundaryZ = (stepZ > 0) ? currentPos.getZ() + 1 : currentPos.getZ();

        double tMaxX = (nextBoundaryX - startPos.x) / direction.x;
        double tMaxY = (nextBoundaryY - startPos.y) / direction.y;
        double tMaxZ = (nextBoundaryZ - startPos.z) / direction.z;

        double tDeltaX = stepX / direction.x;
        double tDeltaY = stepY / direction.y;
        double tDeltaZ = stepZ / direction.z;

        while (startPos.distanceTo(currentPos.getCenter()) <= reachDistance) {
            if (!mc.player.level().getBlockState(currentPos).isAir() && isContainerBlock(mc.player.level().getBlockState(currentPos))) {
                AABB currentBox = new AABB(currentPos);
                Optional<Vec3> intercept = currentBox.clip(startPos, endPos);

                if (intercept.isPresent()) {
                    Vec3 hitVec = intercept.get();
                    Direction side = getHitFaceFromBox(hitVec, currentBox);
                    boolean isInside = currentBox.contains(startPos);
                    return new BlockHitResult(hitVec, side, currentPos, isInside);
                }
            }

            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) {
                    currentPos = currentPos.offset(stepX, 0, 0);
                    tMaxX += tDeltaX;
                } else {
                    currentPos = currentPos.offset(0, 0, stepZ);
                    tMaxZ += tDeltaZ;
                }
            } else {
                if (tMaxY < tMaxZ) {
                    currentPos = currentPos.offset(0, stepY, 0);
                    tMaxY += tDeltaY;
                } else {
                    currentPos = currentPos.offset(0, 0, stepZ);
                    tMaxZ += tDeltaZ;
                }
            }
        }
        return null;
    }

    public static BlockHitResult getFacedBlock(float yaw, float pitch) {
        final double reachDistance = mc.player.blockInteractionRange();
        final double reachDistanceSq = reachDistance * reachDistance;

        Vec3 startPos = eyePos;
        Vec3 direction = getRotationVector(pitch, yaw);
        Vec3 endPos = startPos.add(direction.scale(reachDistance));
        double EPSILON = 1.0E-7;
        if (direction.x == 0) direction = new Vec3(EPSILON, direction.y, direction.z);
        if (direction.y == 0) direction = new Vec3(direction.x, EPSILON, direction.z);
        if (direction.z == 0) direction = new Vec3(direction.x, direction.y, EPSILON);

        int cx = Mth.floor(startPos.x);
        int cy = Mth.floor(startPos.y);
        int cz = Mth.floor(startPos.z);
        int stepX = (int) Math.signum(direction.x);
        int stepY = (int) Math.signum(direction.y);
        int stepZ = (int) Math.signum(direction.z);

        double nextBoundaryX = (stepX > 0) ? cx + 1 : cx;
        double nextBoundaryY = (stepY > 0) ? cy + 1 : cy;
        double nextBoundaryZ = (stepZ > 0) ? cz + 1 : cz;

        double tMaxX = (nextBoundaryX - startPos.x) / direction.x;
        double tMaxY = (nextBoundaryY - startPos.y) / direction.y;
        double tMaxZ = (nextBoundaryZ - startPos.z) / direction.z;

        double tDeltaX = stepX / direction.x;
        double tDeltaY = stepY / direction.y;
        double tDeltaZ = stepZ / direction.z;

        final var world = mc.player.level();
        final CollisionContext collisionCtx = CollisionContext.of(mc.player);
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos(cx, cy, cz);
        AABB box;

        while (true) {
            double dx = mutablePos.getX() + 0.5 - startPos.x;
            double dy = mutablePos.getY() + 0.5 - startPos.y;
            double dz = mutablePos.getZ() + 0.5 - startPos.z;
            if (dx * dx + dy * dy + dz * dz > reachDistanceSq + 3.0) break;

            BlockState state = world.getBlockState(mutablePos);
            if (!state.isAir()) {
                VoxelShape shape;
                FluidState fluidState = state.getFluidState();
                if (!fluidState.isEmpty() && fluidState.isSource() && fluidState.getType() == Fluids.WATER) {
                    shape = Shapes.block();
                } else {
                    shape = state.getCollisionShape(world, mutablePos, collisionCtx);
                }

                if (!shape.isEmpty()) {
                    BlockPos immutablePos = mutablePos.immutable();
                    for (AABB localBox : shape.toAabbs()) {
                        box = localBox.move(immutablePos);
                        Optional<Vec3> intercept = box.clip(startPos, endPos);

                        if (intercept.isPresent()) {
                            Vec3 hitVec = intercept.get();
                            Direction side = getHitFaceFromBox(hitVec, box);
                            boolean isInside = box.contains(startPos);
                            return new BlockHitResult(hitVec, side, immutablePos, isInside);
                        }
                    }
                }
            }
            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) {
                    mutablePos.setX(mutablePos.getX() + stepX);
                    tMaxX += tDeltaX;
                } else {
                    mutablePos.setZ(mutablePos.getZ() + stepZ);
                    tMaxZ += tDeltaZ;
                }
            } else {
                if (tMaxY < tMaxZ) {
                    mutablePos.setY(mutablePos.getY() + stepY);
                    tMaxY += tDeltaY;
                } else {
                    mutablePos.setZ(mutablePos.getZ() + stepZ);
                    tMaxZ += tDeltaZ;
                }
            }
        }
        return null;
    }

    private static Direction getHitFaceFromBox(Vec3 hit, AABB box) {
        final double eps = 1e-7;

        if (Math.abs(hit.x - box.minX) <= eps) return Direction.WEST;
        if (Math.abs(hit.x - box.maxX) <= eps) return Direction.EAST;
        if (Math.abs(hit.y - box.minY) <= eps) return Direction.DOWN;
        if (Math.abs(hit.y - box.maxY) <= eps) return Direction.UP;
        if (Math.abs(hit.z - box.minZ) <= eps) return Direction.NORTH;
        if (Math.abs(hit.z - box.maxZ) <= eps) return Direction.SOUTH;

        double dxMin = Math.abs(hit.x - box.minX);
        double dxMax = Math.abs(hit.x - box.maxX);
        double dyMin = Math.abs(hit.y - box.minY);
        double dyMax = Math.abs(hit.y - box.maxY);
        double dzMin = Math.abs(hit.z - box.minZ);
        double dzMax = Math.abs(hit.z - box.maxZ);

        double m = dxMin; Direction d = Direction.WEST;
        if (dxMax < m) { m = dxMax; d = Direction.EAST; }
        if (dyMin < m) { m = dyMin; d = Direction.DOWN; }
        if (dyMax < m) { m = dyMax; d = Direction.UP; }
        if (dzMin < m) { m = dzMin; d = Direction.NORTH; }
        if (dzMax < m) { /* m = dzMax; */ d = Direction.SOUTH; }

        return d;
    }
}
