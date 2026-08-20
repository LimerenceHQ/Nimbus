package cc.nimbus.features.module.modules.combat;

import cc.nimbus.core.Core;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.game.EventClick;
import cc.nimbus.event.impl.game.EventTick;
import cc.nimbus.event.impl.game.EventWorldLoad;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.BooleanSetting;
import cc.nimbus.features.module.api.setting.impl.submodule.ModeValue;
import cc.nimbus.features.module.api.setting.impl.NumberSetting;
import cc.nimbus.features.rotation.MovementFix;
import cc.nimbus.features.rotation.Rotation;
import cc.nimbus.features.rotation.RotationMath;
import cc.nimbus.features.rotation.RotationPriority;
import cc.nimbus.features.rotation.RotationRaycast;
import cc.nimbus.features.rotation.RotationRequest;
import cc.nimbus.features.rotation.SmoothMode;
import cc.nimbus.mixin.accessor.player.MultiPlayerGameModeAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@ModuleInfo(
        name = "AntiFireball",
        description = "Automatically knocks away incoming fireballs.",
        category = ModuleCategory.Combat
)
public final class AntiFireball extends BasicModule {
    private final Minecraft minecraft = Minecraft.getInstance();

    public final ModeValue swingMode = new ModeValue(
            "Swing Mode", new String[]{"Off", "Normal", "Silent"}, "Normal");
    public final NumberSetting rotationSpeed = new NumberSetting("Rotation Speed", 180.0, 0.0, 180.0, 1.0);
    public final NumberSetting rotationRange = new NumberSetting("Rotation Range", 9.0, 0.0, 20.0, 0.01);
    public final NumberSetting attackRange = new NumberSetting("Attack Range", 6.5, 0.0, 20.0, 0.01);
    public final ModeValue movementFix = new ModeValue(
            "Movement Fix", new String[]{"None", "Strict", "Silent"}, "None");
    public final BooleanSetting reduceVelocity = new BooleanSetting(
            "Reduce Velocity (Grim false detection)", false);

    private final List<LargeFireball> trackedFireballs = new ArrayList<>();
    private LargeFireball closest;

    @Override
    protected void onDisable() {
        reset();
    }

    @EventTarget
    private void onWorldLoad(EventWorldLoad event) {
        reset();
    }

    @EventTarget
    private void onTick(EventTick event) {
        if (event.getPhase() != EventTick.Phase.PRE) return;
        if (minecraft.player == null || minecraft.level == null || minecraft.getConnection() == null) {
            reset();
            return;
        }

        double rangeSquared = rotationRange.getValue().doubleValue()
                * rotationRange.getValue().doubleValue();
        for (var entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof LargeFireball fireball) || !isIncoming(fireball, rangeSquared)) continue;
            if (!trackedFireballs.contains(fireball)) trackedFireballs.add(fireball);
        }

        trackedFireballs.removeIf(fireball -> !isIncoming(fireball, rangeSquared));
        closest = trackedFireballs.stream()
                .min(Comparator.comparingDouble(fireball -> fireball.distanceToSqr(minecraft.player)))
                .orElse(null);

        if (closest == null) {
            Core.INSTANCE.getRotationManager().cancel(this);
            return;
        }

        Rotation rotation = RotationMath.toRotation(
                minecraft.player.getEyePosition(), closest.getBoundingBox().getCenter());
        Core.INSTANCE.getRotationManager().submit(this, new RotationRequest(
                rotation,
                RotationPriority.MEDIUM,
                selectedMovementFix(),
                false,
                SmoothMode.ADVANCED,
                rotationSpeed.getValue().floatValue(),
                0,
                2
        ));
    }

    @EventTarget
    private void onClick(EventClick event) {
        if (minecraft.player == null || minecraft.level == null || minecraft.gameMode == null
                || minecraft.getConnection() == null || closest == null || closest.isRemoved()) return;

        HitResult hit = RotationRaycast.raycast(
                minecraft.player,
                Core.INSTANCE.getRotationManager().getEffectiveRotation(),
                attackRange.getValue().doubleValue(),
                false,
                entity -> true
        );
        if (!(hit instanceof EntityHitResult entityHit) || entityHit.getEntity() != closest) return;

        ((MultiPlayerGameModeAccessor) minecraft.gameMode).nimbus$ensureHasSentCarriedItem();
        minecraft.getConnection().send(ServerboundInteractPacket.createAttackPacket(
                closest, minecraft.player.isShiftKeyDown()));

        if (reduceVelocity.getValue() && minecraft.player.isSprinting()) {
            Vec3 velocity = minecraft.player.getDeltaMovement();
            minecraft.player.setDeltaMovement(velocity.x * 0.6D, velocity.y, velocity.z * 0.6D);
            minecraft.player.setSprinting(false);
        }

        if (swingMode.is("Normal")) {
            minecraft.player.swing(InteractionHand.MAIN_HAND);
        } else if (swingMode.is("Silent")) {
            minecraft.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
        }
    }

    private boolean isIncoming(LargeFireball fireball, double rangeSquared) {
        if (minecraft.player == null || fireball == null || fireball.isRemoved()) return false;
        Vec3 motion = fireball.position().subtract(fireball.xo, fireball.yo, fireball.zo);
        Vec3 toPlayer = minecraft.player.position().subtract(fireball.position());
        return motion.dot(toPlayer) > 0.0D && fireball.distanceToSqr(minecraft.player) <= rangeSquared;
    }

    private MovementFix selectedMovementFix() {
        return switch (movementFix.getValue()) {
            case "Strict" -> MovementFix.STRICT;
            case "Silent" -> MovementFix.SILENT;
            default -> MovementFix.NONE;
        };
    }

    private void reset() {
        trackedFireballs.clear();
        closest = null;
        if (Core.INSTANCE.isInitialized()) Core.INSTANCE.getRotationManager().cancel(this);
    }
}
