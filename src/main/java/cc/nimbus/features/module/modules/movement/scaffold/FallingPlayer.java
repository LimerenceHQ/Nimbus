package cc.nimbus.features.module.modules.movement.scaffold;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

public final class FallingPlayer {
    private double y;
    private double motionX;
    private double motionY;
    private double motionZ;
    private final float yaw;
    private final float strafe;
    private final float forward;
    private final float jumpMovementFactor;

    public FallingPlayer(Player player) {
        y = player.getY();
        motionX = player.getDeltaMovement().x;
        motionY = player.getDeltaMovement().y;
        motionZ = player.getDeltaMovement().z;
        yaw = player.getYRot();
        strafe = player.xxa;
        forward = player.zza;
        float jumpFactor = player.level().getBlockState(player.blockPosition()).getBlock().getJumpFactor();
        float onPosFactor = player.level().getBlockState(player.getOnPos()).getBlock().getJumpFactor();
        jumpMovementFactor = 0.42f * (jumpFactor == 1.0f ? onPosFactor : jumpFactor) + player.getJumpBoostPower();
    }

    public double calculateY(int ticks) {
        for (int i = 0; i < ticks; i++) calculateTick();
        return y;
    }

    private void calculateTick() {
        float strafeValue = strafe * 0.98f;
        float forwardValue = forward * 0.98f;
        float magnitude = strafeValue * strafeValue + forwardValue * forwardValue;
        if (magnitude >= 1.0E-4f) {
            magnitude = Math.max(1.0f, Mth.sqrt(magnitude));
            float factor = jumpMovementFactor;
            Player player = Minecraft.getInstance().player;
            if (player != null && player.isSprinting()) factor *= 1.3f;
            magnitude = factor / magnitude;
            strafeValue *= magnitude;
            forwardValue *= magnitude;
            float sin = Mth.sin(yaw * Mth.DEG_TO_RAD);
            float cos = Mth.cos(yaw * Mth.DEG_TO_RAD);
            motionX += strafeValue * cos - forwardValue * sin;
            motionZ += forwardValue * cos + strafeValue * sin;
        }

        motionY = (motionY - 0.08) * 0.98;
        y += motionY;
        motionX *= 0.91;
        motionZ *= 0.91;
    }
}
