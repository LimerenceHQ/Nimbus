package cc.nimbus.features.module.modules.movement.scaffold;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

public class FallingPlayer {
    private double x;
    public double y;
    private double z;
    private double motionX;
    private double motionY;
    private double motionZ;
    private final float yaw;
    private final float strafe;
    private final float forward;
    private float jumpMovementFactor;
    private final Minecraft mc;

    FallingPlayer(double x, double y, double z, double motionX, double motionY, double motionZ, float yaw, float strafe, float forward) {
        this.mc = Minecraft.getInstance();
        this.x = x;
        this.y = y;
        this.z = z;
        this.motionX = motionX;
        this.motionY = motionY;
        this.motionZ = motionZ;
        this.yaw = yaw;
        this.strafe = strafe;
        this.forward = forward;
    }

    public FallingPlayer(Player player) {
        this(
                player.getX(),
                player.getY(),
                player.getZ(),
                player.getDeltaMovement().x,
                player.getDeltaMovement().y,
                player.getDeltaMovement().z,
                player.getYRot(),
                player.xxa,
                player.zza
        );
        float jumpFactor = player.level().getBlockState(player.blockPosition()).getBlock().getJumpFactor();
        float onPosJumpFactor = player.level().getBlockState(player.getOnPos()).getBlock().getJumpFactor();
        this.jumpMovementFactor = 0.42F * ((double) jumpFactor == 1.0D ? onPosJumpFactor : jumpFactor) + player.getJumpBoostPower();
    }

    void calculateForTick() {
        float strafeValue = this.strafe * 0.98F;
        float forwardValue = this.forward * 0.98F;
        float magnitude = strafeValue * strafeValue + forwardValue * forwardValue;
        if (magnitude >= 1.0E-4F) {
            magnitude = Mth.sqrt(magnitude);
            if (magnitude < 1.0F) {
                magnitude = 1.0F;
            }

            float fixedJumpFactor = this.jumpMovementFactor;
            if (this.mc.player != null && this.mc.player.isSprinting()) {
                fixedJumpFactor *= 1.3F;
            }

            magnitude = fixedJumpFactor / magnitude;
            strafeValue *= magnitude;
            forwardValue *= magnitude;
            float sin = Mth.sin(this.yaw * (float) Math.PI / 180.0F);
            float cos = Mth.cos(this.yaw * (float) Math.PI / 180.0F);
            this.motionX += strafeValue * cos - forwardValue * sin;
            this.motionZ += forwardValue * cos + strafeValue * sin;
        }

        this.motionY -= 0.08;
        this.motionY *= 0.98F;
        this.x += this.motionX;
        this.y += this.motionY;
        this.z += this.motionZ;
        this.motionX *= 0.91;
        this.motionZ *= 0.91;
    }

    public void calculate(int ticks) {
        for (int i = 0; i < ticks; ++i) {
            this.calculateForTick();
        }
    }
}
