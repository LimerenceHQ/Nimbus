package cc.nimbus.features.module.modules.combat.velocitys;

import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.network.PacketEvent;
import cc.nimbus.features.module.api.setting.impl.NumberSetting;
import cc.nimbus.features.module.api.setting.impl.submodule.Mode;
import cc.nimbus.features.module.modules.combat.Velocity;
import cc.nimbus.utils.client.IMinecraft;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.phys.Vec3;

public class Vanilla extends Mode<Velocity> implements IMinecraft {
    public Vanilla() {super("Vanilla");}

    private final NumberSetting horizontal = new NumberSetting("Horizontal",0,0,100,1);
    private final NumberSetting vertical = new NumberSetting("Vertical",0,0,100,1);

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (mc.player == null) return;

        if (event.getPacketState() == PacketEvent.PacketState.RECEIVE) {
            if (event.getPacket() instanceof ClientboundSetEntityMotionPacket packet) {
                if (packet.getId() == mc.player.getId()) {
                    int newXa = (int) (packet.getMovement().x * horizontal.getValue().doubleValue() / 100);
                    int newYa = (int) (packet.getMovement().y * vertical.getValue().doubleValue() / 100);
                    int newZa = (int) (packet.getMovement().z * horizontal.getValue().doubleValue() / 100);
                    event.setPacket(new ClientboundSetEntityMotionPacket(
                            packet.getId(), new Vec3(
                            newXa,
                            newYa,
                            newZa)));
                }
            }
        }
    }
}
