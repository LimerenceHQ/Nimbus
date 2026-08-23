package cc.nimbus.features.module.modules.combat.velocity.legitVelocity;

import cc.nimbus.core.Core;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.component.Priority;
import cc.nimbus.event.impl.game.EventClick;
import cc.nimbus.event.impl.game.EventTick;
import cc.nimbus.event.impl.player.EventMoveInput;
import cc.nimbus.features.module.api.setting.impl.BooleanSetting;
import cc.nimbus.features.module.api.setting.impl.NumberSetting;
import cc.nimbus.features.module.api.setting.impl.submodule.Mode;
import cc.nimbus.features.module.api.setting.impl.submodule.ModeValue;
import cc.nimbus.features.module.modules.combat.velocity.Legit;
import cc.nimbus.features.rotation.RotationRaycast;
import cc.nimbus.mixin.accessor.player.MultiPlayerGameModeAccessor;
import cc.nimbus.utils.client.IMinecraft;
import cc.nimbus.utils.client.MessageUtils;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public class Reduce extends Mode<Legit> implements IMinecraft {
    public Reduce() {
        super("Reduce");
    }
    private final ModeValue mode = new ModeValue("Attack Count Mode",new String[]{"Custom","Calculate"},"Custom");
    private final NumberSetting count = new NumberSetting("Count",5,1,5,1, () -> mode.is("Custom"));
    private final NumberSetting attackRange = new NumberSetting("Attack Range",3,0,6,0.01);
    private final BooleanSetting throughWall = new BooleanSetting("Through Walls",false);
    private final NumberSetting throughRange = new NumberSetting("Through Walls Range",0,0,6,0.01, throughWall::getValue);

    @EventTarget(priority = Priority.High)
    public void onTick(EventTick event) {
        if (!event.isPre()) {
            return;
        }

        if (mode.is("Custom")) {
            getParent().attackCount = count.getValue().intValue();
        }
    }

    @EventTarget(priority = Priority.High)
    public void onClick(EventClick event) {
        if (mc.player == null || mc.getConnection() == null || mc.gameMode == null) return;

        if (getParent().handleVelocity) {
            if (getRayCastPlayer() != null && mc.player.isSprinting()) {
                ((MultiPlayerGameModeAccessor)mc.gameMode).nimbus$ensureHasSentCarriedItem();
                mc.getConnection().send(ServerboundInteractPacket.createAttackPacket(getRayCastPlayer(), mc.player.isShiftKeyDown()));
                if (mc.gameMode.getPlayerMode() != GameType.SPECTATOR) {
                    mc.player.setDeltaMovement(mc.player.getDeltaMovement().multiply(0.6, 1.0, 0.6));
                    mc.player.setSprinting(false);
                }
                mc.player.swing(InteractionHand.MAIN_HAND);
            }
        }
    }

    @EventTarget(priority = Priority.Lowest)
    public void onMoveInput(EventMoveInput event) {
        if (getParent().handleVelocity) {
            event.setForward(1);
        }
    }

    private Player getRayCastPlayer() {
        EntityHitResult hitResult = RotationRaycast.rayCastEntity(
                Core.INSTANCE.getRotationManager().getEffectiveRotation(), attackRange.getValue().doubleValue(), false);

        if (!(hitResult != null && hitResult.getType() == HitResult.Type.ENTITY)
                && throughRange.getValue().doubleValue() > 0) {
            hitResult = RotationRaycast.rayCastEntity(
                    Core.INSTANCE.getRotationManager().getEffectiveRotation(), throughRange.getValue().doubleValue(), false);
        }

        if (hitResult != null && hitResult.getType() == HitResult.Type.ENTITY
                && hitResult.getEntity() instanceof Player) {
            return (Player) hitResult.getEntity();
        }

        return null;
    }
}
