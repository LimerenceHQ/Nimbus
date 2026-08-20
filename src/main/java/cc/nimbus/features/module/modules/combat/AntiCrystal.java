package cc.nimbus.features.module.modules.combat;

import cc.nimbus.core.Core;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.player.EventUpdate;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.submodule.ModeValue;
import cc.nimbus.features.module.api.setting.impl.NumberSetting;
import cc.nimbus.features.module.api.setting.impl.modes.Modes;
import cc.nimbus.features.rotation.*;
import cc.nimbus.utils.entity.rotation.RotationUtils;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@ModuleInfo(name = "AntiCrystal", category = ModuleCategory.Combat, description = "Automatically hit crystal in range")
public class AntiCrystal extends BasicModule {

    private final NumberSetting range = new NumberSetting("Range", 4.5, 0, 6, 0.01);
    private final NumberSetting throughRange = new NumberSetting("ThroughWallRange", 2, 0, 6, 0.01);

    private final ModeValue rotationMode = new ModeValue("Rotation Mode", new Modes("Instant", "Smooth"));
    private final NumberSetting rotateSpeed = new NumberSetting("Rotate Speed", 180, 1, 180, 1, () -> rotationMode.is("Smooth"));

    private final List<EndCrystal> crystalTargets = new ArrayList<>();
    public EndCrystal currentTarget = null;
    private int currentIndex = 0;

    @Override
    protected void onEnable() {
        crystalTargets.clear();
        currentTarget = null;
        currentIndex = 0;
    }

    @Override
    protected void onDisable() {
        crystalTargets.clear();
        currentTarget = null;
    }

    @EventTarget
    private void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.level == null) return;

        Iterator<EndCrystal> iterator = crystalTargets.iterator();
        while (iterator.hasNext()) {
            EndCrystal crystal = iterator.next();
            if (crystal == null || !crystal.isAlive()) {
                iterator.remove();
                continue;
            }

            if (hasNoEnemyNearby(crystal, mc.level, mc.player) || isNotInValidRange(mc.player, crystal)) {
                iterator.remove();
            }
        }

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof EndCrystal crystal)) continue;
            if (!crystal.isAlive()) continue;
            if (crystalTargets.contains(crystal)) continue;

            if (hasNoEnemyNearby(crystal, mc.level, mc.player)) continue;
            if (isNotInValidRange(mc.player, crystal)) continue;

            crystalTargets.add(crystal);
        }

        if (crystalTargets.isEmpty()) {
            currentTarget = null;
            return;
        }

        if (currentIndex >= crystalTargets.size()) currentIndex = 0;
        currentTarget = crystalTargets.get(currentIndex);

        if (currentTarget == null || !currentTarget.isAlive() || isNotInValidRange(mc.player, currentTarget)) {
            crystalTargets.remove(currentTarget);
            if (currentIndex >= crystalTargets.size()) currentIndex = 0;
            return;
        }

        if (currentTarget != null) {
            Vec3 crystalPos = currentTarget.position().add(0, 0.5, 0);
            Rotation targetRot = RotationUtils.toRotation(crystalPos, false);
            Core.INSTANCE.getRotationManager().submit(this, new RotationRequest(
                    targetRot,
                    RotationPriority.MEDIUM,
                    MovementFix.SILENT,
                    rotationMode.is("Smooth"),
                    SmoothMode.ADVANCED,
                    rotateSpeed.getValue().floatValue(),
                    0,
                    0
            ));
        }

        // 击打水晶
        boolean allowThroughWall = getDistanceToEntityBox(mc.player, currentTarget) <= throughRange.getValue().doubleValue();
        
        EntityHitResult hit = RotationRaycast.rayCastEntity(Core.INSTANCE.getRotationManager().getTargetRotation(), range.getValue().doubleValue(), allowThroughWall);
        
        if (hit != null && hit.getEntity() == currentTarget) {
            mc.player.connection.send(ServerboundInteractPacket.createAttackPacket(currentTarget, mc.player.isShiftKeyDown()));
            mc.player.swing(InteractionHand.MAIN_HAND);
            currentIndex++;
        }
    };
    private boolean hasNoEnemyNearby(EndCrystal crystal, Level world, Player player) {
        for (Player target : world.players()) {
            if (target == player || !target.isAlive() || target.isSpectator()) continue;
            if (target.distanceToSqr(crystal) <= 25.0) { // 5格半径
                return false;
            }
        }
        return true;
    }
    private boolean isNotInValidRange(Player player, EndCrystal crystal) {
        boolean canSee = player.hasLineOfSight(crystal);
        double dist = getDistanceToEntityBox(player, crystal);
        double maxRange = canSee ? range.getValue().doubleValue() : throughRange.getValue().doubleValue();
        return !(dist <= maxRange);
    }
    
    private double getDistanceToEntityBox(Player player, Entity entity) {
        return player.distanceTo(entity);
    }
}
