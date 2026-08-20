package cc.nimbus.utils.entity.player;

import cc.nimbus.core.Core;
import cc.nimbus.utils.client.IMinecraft;
import net.minecraft.world.entity.player.Input;

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

        float rotationYaw = Core.INSTANCE.getRotationManager().getEffectiveRotation().yaw();
        Input input = mc.player.input.keyPresses;
        float forwardImpulse = axis(input.forward(), input.backward());
        float leftImpulse = axis(input.left(), input.right());

        if (forwardImpulse < 0F) {
            rotationYaw += 180F;
        }

        float forward = forwardImpulse < 0F ? -0.5F : forwardImpulse > 0F ? 0.5F : 1F;

        if (leftImpulse > 0F) {
            rotationYaw -= 90F * forward;
        }

        if (leftImpulse < 0F) {
            rotationYaw += 90F * forward;
        }

        return Math.toRadians(rotationYaw);
    }

    private static float axis(boolean positive, boolean negative) {
        if (positive == negative) return 0.0F;
        return positive ? 1.0F : -1.0F;
    }
}
