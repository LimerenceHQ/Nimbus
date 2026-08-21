package cc.nimbus.features.module.modules.combat;

import cc.nimbus.core.Core;
import cc.nimbus.event.EventBus;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.game.EventClick;
import cc.nimbus.event.impl.game.EventRotation;
import cc.nimbus.event.impl.player.EventAttack;
import cc.nimbus.event.impl.player.EventUpdate;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.BooleanSetting;
import cc.nimbus.features.module.api.setting.impl.ModeSetting;
import cc.nimbus.features.module.api.setting.impl.MultiSelectSetting;
import cc.nimbus.features.module.api.setting.impl.NumberSetting;
import cc.nimbus.features.module.modules.player.AutoTool;
import cc.nimbus.features.rotation.*;
import cc.nimbus.managers.TargetManager;
import cc.nimbus.mixin.accessor.player.MultiPlayerGameModeAccessor;
import cc.nimbus.utils.entity.rotation.RotationUtils;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.HitResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@ModuleInfo(name = "KillAura",category = ModuleCategory.Combat, description = "Automatically attack selected enemies")
public class KillAura extends BasicModule {
    private final NumberSetting cps = new NumberSetting("CPS",10,1,20,1);
    private final ModeSetting switchMode = new ModeSetting("Switch Mode",new String[]{"Single","Switch"},"Single");
    private final ModeSetting priority = new ModeSetting("Priority",new String[]{"Health","Distance"},"Health",() -> switchMode.is("Single"));
    private final ModeSetting swingMode = new ModeSetting("Swing Mode",new String[]{"Off","Normal","Silent"},"Normal");
    private final NumberSetting searchRange = new NumberSetting("Search Range",6,0,8,0.01);
    private final BooleanSetting throughWall = new BooleanSetting("Through Walls",false);
    private final NumberSetting throughWallRange = new NumberSetting("Through Walls Range",0,0,6,0.01,throughWall::getValue);
    private final NumberSetting rotationRange = new NumberSetting("Rotation Range",4,0,7,0.01);
    private final NumberSetting attackRange = new NumberSetting("Attack Range",3,0,6,0.01);
    private final NumberSetting rotationSpeed = new NumberSetting("Rotation Speed",180,0,180,1);
    private final BooleanSetting stopAttack = new BooleanSetting("StopAttack When UsingItem",false);
    private final ModeSetting movementFix = new ModeSetting("Movement Fix",new String[]{"None","Strict","Silent"},"None");
    private final ModeSetting keepSprint = new ModeSetting("Keep Sprint",new String[]{"Off","Vanilla","Legit"},"Off");
    private final BooleanSetting skipFirstAttack = new BooleanSetting("Skip First Attack",false,() -> keepSprint.is("Legit"));
    public final MultiSelectSetting particleOption = new MultiSelectSetting(
            "Particle Option", new String[]{"Sharpness", "Critical"});

    private boolean firstAttack = false;
    private double attackAccumulator = 0.0;
    private boolean shouldStopSprint = false;
    public LivingEntity attackTarget = null;
    public List<LivingEntity> targets = new ArrayList<>();
    public List<LivingEntity> searchedTargets = new ArrayList<>();
    private Rotation rotation = new Rotation(0,0);

    @Override
    protected void onEnable() {
        firstAttack = false;
        targets = new ArrayList<>();
        searchedTargets = new ArrayList<>();
        attackTarget = null;
        attackAccumulator = 0.0;
        shouldStopSprint = false;
    }

    @Override
    protected void onDisable() {
        firstAttack = false;
        targets = new ArrayList<>();
        searchedTargets = new ArrayList<>();
        attackTarget = null;
        attackAccumulator = 0.0;
        shouldStopSprint = false;
    }

    @EventTarget
    public void onRotation(EventRotation event) {
        updateTargets();

        if (mc.player == null || attackTarget == null || targets.isEmpty() || searchedTargets.isEmpty()) return;

        MovementFix moveFix = switch (movementFix.getValue()) {
            case "Strict" -> MovementFix.STRICT;
            case "Silent" -> MovementFix.SILENT;
            default -> MovementFix.NONE;
        };

        rotation = RotationUtils.toRotation(RotationUtils.getInteractiveEntityData(mc.player,attackTarget,throughWall.getValue() ? throughWallRange.getValue().doubleValue() : 0.0).point());
        Core.INSTANCE.getRotationManager().setRotations(rotation,moveFix,true, SmoothMode.ADVANCED,rotationSpeed.getValue().floatValue(),0,0, Priority.MEDIUM);
    }

    @EventTarget
    public void onClick(EventClick event) {
        if (mc.player == null) return;

        double delay = 1000.0 / cps.getValue().intValue();

        attackAccumulator += 50.0;
        if (attackAccumulator > delay * 2) {
            attackAccumulator = delay * 2;
        }

        if (attackTarget == null) {
            firstAttack = false;
            return;
        }

        if (!rayCast(attackTarget)) return;
        boolean cantKeepSprint = !firstAttack && skipFirstAttack.getValue();

        if (attackAccumulator >= delay) {
            if (keepSprint.is("Legit")) {
                if (mc.player.isSprinting() && !cantKeepSprint) {
                    shouldStopSprint = true;
                }
            }
            if ((!stopAttack.getValue() || !mc.player.isUsingItem()) && (!shouldStopSprint || cantKeepSprint)) {
                handleAttack();
                attackAccumulator -= delay;
            }
        }
    }

    @EventTarget
    public void onUpdate(EventUpdate event) {
        if (event.isPre() || mc.player == null) return;

        boolean cantKeepSprint = !firstAttack && skipFirstAttack.getValue();
        if (shouldStopSprint) {
            if (!cantKeepSprint && mc.player.isSprinting()) {
                mc.player.setSprinting(false);
            }
            shouldStopSprint = false;
        }
    }

    private void handleAttack() {
        if (mc.player == null || mc.getConnection() == null || mc.gameMode == null) return;

        EventBus.call(new EventAttack(attackTarget));
        ((MultiPlayerGameModeAccessor)mc.gameMode).nimbus$ensureHasSentCarriedItem();
        mc.getConnection().send(ServerboundInteractPacket.createAttackPacket(attackTarget, mc.player.isShiftKeyDown()));
        if (mc.gameMode.getPlayerMode() != GameType.SPECTATOR && !keepSprint.is("Vanilla")) {
            Core.INSTANCE.getModuleManager().getModule(AutoTool.class).equipBestWeapon();
            mc.player.attack(attackTarget);
            mc.player.resetAttackStrengthTicker();
        }
        if (swingMode.is("Normal")) {
            mc.player.swing(InteractionHand.MAIN_HAND);
        } else if (swingMode.is("Silent")) {
            mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
        }

        if (!firstAttack)
            firstAttack = true;
        if (particleOption.isSelected("Critical"))
            mc.player.crit(attackTarget);
        if (particleOption.isSelected("Sharpness"))
            mc.player.magicCrit(attackTarget);
    }

    private void updateTargets() {
        if (mc.player == null || mc.level == null) return;

        searchedTargets = new ArrayList<>(Core.INSTANCE.getTargetManager().getTargets(searchRange.getValue().doubleValue()));

        if (searchedTargets.isEmpty()) {
            targets = new ArrayList<>();
            attackTarget = null;
            return;
        }

        List<LivingEntity> inRotationRangeTargets = searchedTargets.stream()
                .filter(entity -> RotationUtils.getInteractiveEntityData(mc.player,entity,throughWall.getValue() ? throughWallRange.getValue().doubleValue() : 0.0).distance() <= rotationRange.getValue().doubleValue())
                .collect(Collectors.toList());

        List<LivingEntity> inAttackRangeTargets = searchedTargets.stream()
                .filter(entity -> RotationUtils.getInteractiveEntityData(mc.player,entity,throughWall.getValue() ? throughWallRange.getValue().doubleValue() : 0.0).distance() <= attackRange.getValue().doubleValue())
                .collect(Collectors.toList());

        targets = new ArrayList<>(!inAttackRangeTargets.isEmpty() ? inAttackRangeTargets : inRotationRangeTargets);

        if (switchMode.is("Single")) {
            attackTarget = targets.stream()
                    .min(Comparator.comparingDouble(this::getPriorityValue))
                    .orElse(null);
        } else if (switchMode.is("Switch")) {
            attackTarget = targets.stream()
                    .min(Comparator.comparingDouble(this::ultimateTarget))
                    .orElse(null);
        }
    }

    private double ultimateTarget(LivingEntity entity) {
        if (rotation == null) return 1000.0d;
        if (!RotationRaycast.canHit(entity,attackRange.getValue().doubleValue(),throughWall.getValue() ? throughWallRange.getValue().doubleValue() : 0) || mc.player == null) {
            return 1000.0;
        } else {
            double distance = RotationUtils.getDistanceToEntityBox(mc.player,entity);
            double hurtTime = (entity).hurtTime * 6;
            return hurtTime + distance;
        }
    }

    private double getPriorityValue(LivingEntity entity) {
        if (mc.player == null) return -1;

        return switch (priority.getValue()) {
            case "Health" -> entity.getHealth();
            case "Distance" -> RotationUtils.getDistanceToEntityBox(mc.player,entity);
            default -> -1;
        };
    }

    private boolean rayCast(LivingEntity target) {
        if (mc.player == null) return false;

        var rot = Core.INSTANCE.getRotationManager().getTargetRotation();
        double r = attackRange.getValue().doubleValue();
        boolean through = RotationUtils.getInteractiveEntityData(mc.player,target,throughWallRange.getValue().doubleValue()).distance() <= throughWallRange.getValue().doubleValue();

        var hit = RotationRaycast.rayCastEntity(rot, r, through);
        if (hit == null || hit.getType() != HitResult.Type.ENTITY) return false;
        if (!(hit.getEntity() instanceof LivingEntity living)) return false;

        if (living != target) {
            if (Core.INSTANCE.getTargetManager().isTarget(living)) {
                attackTarget = living;
            } else return false;
        }
        return true;
    }
}
