package cc.nimbus.features.module.modules.combat.velocity.legitVelocity;

import cc.nimbus.core.Core;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.component.Priority;
import cc.nimbus.event.impl.game.EventRotation;
import cc.nimbus.event.impl.network.PacketEvent;
import cc.nimbus.event.impl.player.EventMoveInput;
import cc.nimbus.features.module.api.setting.impl.submodule.Mode;
import cc.nimbus.features.module.modules.combat.velocity.Legit;
import cc.nimbus.managers.GameStateManager;
import cc.nimbus.features.rotation.MovementFix;
import cc.nimbus.features.rotation.Rotation;
import cc.nimbus.features.rotation.SmoothMode;
import cc.nimbus.utils.client.IMinecraft;
import cc.nimbus.utils.entity.rotation.RotationUtils;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;


public class JumpReset extends Mode<Legit> implements IMinecraft {
    public JumpReset() {
        super("Jump Reset");
    }
    private RotationData finalRotation = new RotationData(new Rotation(0,0),0);
    private boolean handleJumpReset = false;

    private record RotationData(Rotation rotation, float difference) {
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (mc.player == null || event.getPacketState() != PacketEvent.PacketState.RECEIVE) {
            return;
        }
        if (event.getPacket() instanceof ClientboundSetEntityMotionPacket packet && getParent().isValidVelocityPacket(packet)) {
            double velocityX = packet.getMovement().x / 8000.0;
            double velocityZ = packet.getMovement().z / 8000.0;
            Vec3 knockback = new Vec3(velocityX, 0.0, velocityZ).normalize();
            Vec3 opposite = mc.player.position().add(-knockback.x, 0.0, -knockback.z);
            finalRotation = getOppositeRotation(RotationUtils.toRotation(opposite, false));
        }
    }

    @EventTarget
    public void onRotation(EventRotation event) {
        if (mc.player == null || mc.getConnection() == null || mc.gameMode == null) return;
        if (getParent().needHandleRotation) {
            if (mc.player.onGround() && Math.abs(finalRotation.difference) <= 45F) {
                Core.INSTANCE.getRotationManager().setRotations(finalRotation.rotation, MovementFix.SILENT, false, SmoothMode.ADVANCED, 180F, 0, 0, cc.nimbus.features.rotation.Priority.VERY_HIGH);
            }
            getParent().needHandleRotation = false;
        }
    }

    @EventTarget(priority = Priority.Low)
    public void onMoveInput(EventMoveInput event) {
        if (getParent().ticksSinceVelocity >= 0) {
            if (getParent().ticksSinceVelocity < 4 && Math.abs(finalRotation.difference) <= 45F) {
                if (GameStateManager.clientOnGround()) {
                    handleJumpReset = true;
                    event.setJump(true);
                }
                if (handleJumpReset) {
                    float[] movement = computeMovementForward(finalRotation.rotation, finalRotation.difference);
                    if (movement[0] != 0)
                        event.setForward(movement[0]);
                    if (movement[1] != 0)
                        event.setStrafe(movement[1]);
                }
            } else if (handleJumpReset) handleJumpReset = false;
        }
    }

    private RotationData getOppositeRotation(Rotation rotation) {
        if (mc.player == null) return null;

        float targetYaw = rotation.yaw();
        Rotation oppositeRotation = new Rotation(targetYaw,Core.INSTANCE.getRotationManager().getEffectiveRotation().pitch());
        return new RotationData(oppositeRotation,0);
    }

    public float[] computeMovementForward(Rotation targetRotation, float rotationDifference) {
        float deltaYaw = Mth.wrapDegrees(
                targetRotation.yaw() - Core.INSTANCE.getRotationManager().getEffectiveRotation().yaw()
        );

        if (rotationDifference > 22.5f) {
            deltaYaw -= 45F;
        } else if (rotationDifference < -22.5f) {
            deltaYaw += 45F;
        }

        double radian = Math.toRadians(deltaYaw);
        double x = Math.sin(radian);
        double z = Math.cos(radian);

        float forward;
        float strafe;

        if (z > 0.707) {
            forward = 1;
        } else if (z < -0.707) {
            forward = -1;
        } else {
            forward = 0;
        }

        if (x > 0.707) {
            strafe = -1;
        } else if (x < -0.707) {
            strafe = 1;
        } else {
            strafe = 0;
        }

        return new float[]{forward, strafe};
    }
}
