package cc.nimbus.utils.entity.player;

import cc.nimbus.core.Core;
import cc.nimbus.utils.client.IMinecraft;

public class MovementUtils implements IMinecraft {
    public static boolean isMoving() {
        return mc.player != null && mc.player.input.getMoveVector().lengthSquared() > 1.0E-6F;
    }

    public static void strafe(double speed) {
        if (mc.player == null) return;

        double yaw = getDirection();

        mc.player.setDeltaMovement(-Math.sin(yaw) * speed, mc.player.getDeltaMovement().y, Math.cos(yaw) * speed);
    }
    public static double getDirection() {
        if (mc.player == null) return 0;

        var rotationYaw = Core.INSTANCE.getRotationManager().getEffectiveRotation().yaw();

        if (mc.player.input.forwardImpulse < 0F) {
            rotationYaw += 180F;
        }

        var forward = 1F;

        if (mc.player.input.leftImpulse < 0F) {
            forward = -0.5F;
        } else if (mc.player.input.forwardImpulse > 0F) {
            forward = 0.5F;
        }

        if (mc.player.input.leftImpulse > 0F) {
            rotationYaw -= 90F * forward;
        }

        if (mc.player.input.leftImpulse < 0F) {
            rotationYaw += 90F * forward;
        }

        return Math.toRadians(rotationYaw);
    }

    private static float axis(boolean positive, boolean negative) {
        if (positive == negative) return 0.0F;
        return positive ? 1.0F : -1.0F;
    }
}
