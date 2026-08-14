package cc.nimbus.features.module.modules.render;

import cc.nimbus.core.Core;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.render.EventRender3D;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.utils.render.Render3DUtils;
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
    @EventTarget
    private void onRender3D(EventRender3D event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.gui.screen() != null) return;

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
        Vec3 inherited = minecraft.player.getKnownMovement();
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
        Render3DUtils.drawPolylineWithDepth(event.getPoseStack(), points, color, 2f,
                Render3DUtils.DepthMode.BOTH);
        if (landingHit instanceof BlockHitResult blockHit) {
            var blockPos = blockHit.getBlockPos();
            VoxelShape shape = minecraft.level.getBlockState(blockPos)
                    .getCollisionShape(minecraft.level, blockPos);
            AABB blockBox = shape.isEmpty()
                    ? new AABB(blockPos.getX(), blockPos.getY(), blockPos.getZ(),
                    blockPos.getX() + 1, blockPos.getY() + 1, blockPos.getZ() + 1)
                    : shape.bounds().move(blockPos);
            Render3DUtils.drawFilledBoxWithDepth(event.getPoseStack(), blockBox,
                    0x24000000 | (color & 0x00FFFFFF), Render3DUtils.DepthMode.BOTH);
            Render3DUtils.drawBoxOutlineWithDepth(event.getPoseStack(), blockBox, color, 2.5f,
                    Render3DUtils.DepthMode.BOTH);

            Vec3 impact = blockHit.getLocation();
            Render3DUtils.drawBoxOutlineWithDepth(event.getPoseStack(),
                    new AABB(impact, impact).inflate(0.035), 0xF0FFFFFF, 2f,
                    Render3DUtils.DepthMode.NONE);
        } else if (landingHit instanceof EntityHitResult entityHit) {
            Render3DUtils.drawFilledBoxWithDepth(event.getPoseStack(), entityHit.getEntity().getBoundingBox(),
                    0x24FF5C68, Render3DUtils.DepthMode.BOTH);
            Render3DUtils.drawBoxOutlineWithDepth(event.getPoseStack(), entityHit.getEntity().getBoundingBox(),
                    0xE6FF5C68, 2.5f, Render3DUtils.DepthMode.BOTH);
        } else if (landingHit != null) {
            Vec3 landing = landingHit.getLocation();
            Render3DUtils.drawBoxOutlineWithDepth(event.getPoseStack(),
                    new AABB(landing, landing).inflate(0.09), color, 2.5f,
                    Render3DUtils.DepthMode.BOTH);
        }
    }

    private static boolean supported(ItemStack stack) {
        return stack != null && (stack.is(Items.BOW) || stack.is(Items.CROSSBOW)
                || stack.is(Items.TRIDENT) || stack.is(Items.SNOWBALL) || stack.is(Items.EGG)
                || stack.is(Items.ENDER_PEARL) || stack.is(Items.SPLASH_POTION)
                || stack.is(Items.LINGERING_POTION));
    }
}
