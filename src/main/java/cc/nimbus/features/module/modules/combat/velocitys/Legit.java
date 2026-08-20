package cc.nimbus.features.module.modules.combat.velocitys;

import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.game.EventTick;
import cc.nimbus.event.impl.network.PacketEvent;
import cc.nimbus.features.module.api.setting.impl.submodule.ComponentValue;
import cc.nimbus.features.module.api.setting.impl.submodule.Mode;
import cc.nimbus.features.module.modules.combat.Velocity;
import cc.nimbus.features.module.modules.combat.velocitys.legitVelocity.Delay;
import cc.nimbus.features.module.modules.combat.velocitys.legitVelocity.JumpReset;
import cc.nimbus.features.module.modules.combat.velocitys.legitVelocity.Reduce;
import cc.nimbus.utils.client.IMinecraft;
import cc.nimbus.utils.client.MessageUtils;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

public class Legit extends Mode<Velocity> implements IMinecraft {
    public Legit() {
        super("Legit");
    }
    public ComponentValue modules = new ComponentValue("Legit-Modules",new String[]{},
            new Delay(),
            new JumpReset(),
            new Reduce());

    public boolean needHandleRotation = false;
    public int attackCount = -1;
    public int ticksSinceVelocity = -1;
    public boolean handleVelocity = false;

    @EventTarget
    public void onTick(EventTick event) {
        if (ticksSinceVelocity >= 0) {
            ticksSinceVelocity++;
            if (ticksSinceVelocity >= 10) {
                ticksSinceVelocity = -1;
                handleVelocity = false;
            }
            if (ticksSinceVelocity >= attackCount) {
                handleVelocity = false;
            }
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (mc.player == null) return;

        if (event.getPacketState() == PacketEvent.PacketState.RECEIVE) {
            if (event.getPacket() instanceof ClientboundSetEntityMotionPacket packet) {
                if (isValidVelocityPacket(packet)) {
                    attackCount = computeReduceTicks((int) packet.getMovement().x, (int) packet.getMovement().z);

                    if (!modules.isEnabled("Delay") || cantSprint()) {
                        handleVelocity = true;
                        ticksSinceVelocity = 0;
                        if (modules.isEnabled("Jump Reset")) {
                            needHandleRotation = true;
                        }
                    }
                }
            }
            if (event.getPacket() instanceof ClientboundPlayerPositionPacket) {
                handleVelocity = false;
                ticksSinceVelocity = -1;
                needHandleRotation = false;
            }
        }
    }

    public boolean isValidVelocityPacket(ClientboundSetEntityMotionPacket packet) {
        return mc.player != null && packet.getId() == mc.player.getId();
    }

    public boolean cantSprint() {
        return mc.player == null
                || mc.player.hasEffect(MobEffects.BLINDNESS)
                || mc.player.hasEffect(MobEffects.SLOWNESS)
                || mc.player.isShiftKeyDown()
                || mc.player.isCrouching()
                || (!mc.player.isPassenger() && mc.player.getFoodData().getFoodLevel() <= 6 && !mc.player.getAbilities().mayfly);
    }

    private int computeReduceTicks(int motionX, int motionZ) {
        double kb = Math.hypot(motionX, motionZ);
        double y = 0.000408163 * kb + 0.7142857;

        int result = (int) Math.round(y);

        if (result < 1) result = 1;
        if (result > 5) result = 5;

        return result;
    }
}
