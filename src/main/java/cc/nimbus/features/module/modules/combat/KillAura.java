package cc.nimbus.features.module.modules.combat;

import cc.nimbus.core.Core;
import cc.nimbus.event.EventBus;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.game.EventClick;
import cc.nimbus.event.impl.game.EventTick;
import cc.nimbus.event.impl.game.EventWorldLoad;
import cc.nimbus.event.impl.player.EventAttack;
import cc.nimbus.event.impl.player.EventMotion;
import cc.nimbus.event.impl.player.EventUpdate;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.BooleanSetting;
import cc.nimbus.features.module.api.setting.impl.submodule.ModeValue;
import cc.nimbus.features.module.api.setting.impl.MultiSelectSetting;
import cc.nimbus.features.module.api.setting.impl.NumberSetting;
import cc.nimbus.features.module.modules.movement.Scaffold;
import cc.nimbus.features.rotation.*;
import cc.nimbus.mixin.accessor.player.MultiPlayerGameModeAccessor;
import cc.nimbus.managers.RotationManager;
import cc.nimbus.managers.TargetManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.*;

import java.util.Comparator;
import java.util.List;

@ModuleInfo(
        name = "KillAura",
        description = "Attacks valid targets using server-side rotations.",
        category = ModuleCategory.Combat
)
public final class KillAura extends BasicModule {
    public final NumberSetting cps = new NumberSetting("CPS", 10, 1, 20, 1);
    public final ModeValue switchMode = new ModeValue("Switch Mode", new String[]{"Single", "Switch"}, "Single");
    public final ModeValue priority = new ModeValue(
            "Priority", new String[]{"Health", "Distance"}, "Health", () -> switchMode.is("Single"));
    public final ModeValue swingMode = new ModeValue("Swing Mode", new String[]{"Off", "Normal", "Silent"}, "Normal");
    public final NumberSetting searchRange = new NumberSetting("Search Range", 6, 0, 8, 0.01);
    public final NumberSetting rotationRange = new NumberSetting("Rotation Range", 4, 0, 7, 0.01);
    public final NumberSetting attackRange = new NumberSetting("Attack Range", 3, 0, 6, 0.01);
    public final BooleanSetting throughWalls = new BooleanSetting("Through Walls", false);
    public final NumberSetting throughWallsRange = new NumberSetting(
            "Through Walls Range", 0, 0, 6, 0.01, throughWalls::getValue);
    public final NumberSetting rotationSpeed = new NumberSetting("Rotation Speed", 180, 0, 180, 1);
    public final ModeValue movementFix = new ModeValue(
            "Movement Fix", new String[]{"None", "Strict", "Silent"}, "None");
    public final BooleanSetting stopAttackWhileUsingItem = new BooleanSetting("Stop Attack While Using Item", false);
    public final BooleanSetting stopWhileScaffolding = new BooleanSetting("Stop While Scaffolding", true);
    public final BooleanSetting disableOnWorldChange = new BooleanSetting("Disable On World Change", true);
    public final ModeValue keepSprint = new ModeValue(
            "Keep Sprint", new String[]{"Off", "Vanilla", "Legit"}, "Off");
    public final BooleanSetting skipFirstAttack = new BooleanSetting(
            "Skip First Attack", false, () -> keepSprint.is("Legit"));
    public final MultiSelectSetting particles = new MultiSelectSetting(
            "Particles", new String[]{"Critical", "Sharpness"});
    public final ModeValue autoBlock = new ModeValue(
            "AutoBlock", new String[]{"Off", "Fake"}, "Off");

    public LivingEntity attackTarget;
    private List<LivingEntity> searchedTargets = List.of();
    private List<LivingEntity> targets = List.of();
    private Rotation requestedRotation;
    private double attackAccumulator;
    private boolean firstAttack;
    private boolean renderBlocking;
    private boolean shouldStopSprint;

    @Override
    protected void onEnable() {
        resetState(false);
    }

    @Override
    protected void onDisable() {
        resetState(true);
    }

    @EventTarget
    private void onWorldLoad(EventWorldLoad event) {
        resetState(true);
        if (disableOnWorldChange.getValue() && isEnabled()) setEnabled(false);
    }

    @EventTarget
    private void onTick(EventTick event) {
        if (event.getPhase() == EventTick.Phase.PRE) prepareTick();
    }

    @EventTarget
    private void onClick(EventClick event) {
        attackTick();
    }

    @EventTarget
    private void onMotion(EventMotion event) {
        if (!event.isPre()) return;
        renderBlocking = autoBlock.is("Fake") && attackTarget != null && holdingSword();
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

    public boolean isRenderBlocking() {
        return isEnabled() && renderBlocking && holdingSword();
    }

    public LivingEntity getAttackTarget() {
        return attackTarget;
    }

    public List<LivingEntity> getTargets() {
        return targets;
    }

    private void prepareTick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || !minecraft.player.isAlive()) {
            resetState(true);
            return;
        }

        if (shouldYieldToScaffold() || isEatingOrDrinking()) {
            resetState(true);
            return;
        }

        updateTargets();
        if (attackTarget == null) {
            firstAttack = false;
            stopBlocking();
            return;
        }
        if (autoBlock.is("Off")) stopBlocking();

        RotationMath.AimPoint point = aimPoint(attackTarget);
        if (!point.exists() || point.distance() > rotationRange.getValue().doubleValue()) {
            stopBlocking();
            return;
        }
        requestedRotation = RotationMath.toRotation(minecraft.player.getEyePosition(), point.point());
        Core.INSTANCE.getRotationManager().setRotations(requestedRotation,selectedMovementFix(),true, SmoothMode.ADVANCED,rotationSpeed.getValue().floatValue(),0,0, Priority.MEDIUM);

        if (autoBlock.is("Fake")) renderBlocking = holdingSword();
    }

    private void attackTick() {
        if (mc.player == null) return;
        if (shouldYieldToScaffold() || isEatingOrDrinking()) {
            resetState(true);
            return;
        }
        double delay = 1000.0 / cps.getValue().doubleValue();
        attackAccumulator = Math.min(attackAccumulator + 50.0, delay * 2.0);
        if (mc.level == null || mc.gameMode == null
                || mc.getConnection() == null || attackTarget == null) return;
        if (stopAttackWhileUsingItem.getValue() && mc.player.isUsingItem()) return;

        LivingEntity rayCastTarget = resolveRayCastTarget();
        if (rayCastTarget == null) return;
        attackTarget = rayCastTarget;

        boolean skipSprintStop = skipFirstAttack.getValue() && !firstAttack;
        if (attackAccumulator >= delay) {
            if (keepSprint.is("Legit")) {
                if (mc.player.isSprinting() && !skipSprintStop) {
                    shouldStopSprint = true;
                }
            }
            if (!shouldStopSprint || skipSprintStop) {
                performAttack(rayCastTarget);
                attackAccumulator -= delay;
            }
        }
    }

    private void performAttack(LivingEntity target) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.gameMode == null || minecraft.getConnection() == null) return;

        if (keepSprint.is("Vanilla")) {
            EventAttack pre = EventBus.call(new EventAttack(target, EventAttack.Stage.PRE));
            if (pre.isCancel() || pre.getEntity() == null) return;
            Entity actualTarget = pre.getEntity();
            ((MultiPlayerGameModeAccessor) minecraft.gameMode).nimbus$ensureHasSentCarriedItem();
            minecraft.getConnection().send(ServerboundInteractPacket.createAttackPacket(
                    actualTarget, minecraft.player.isShiftKeyDown()));
            EventBus.call(new EventAttack(actualTarget, EventAttack.Stage.POST));
        } else {
            minecraft.gameMode.attack(minecraft.player, target);
        }

        if (minecraft.gameMode.getPlayerMode() != GameType.SPECTATOR) {
            if (swingMode.is("Normal")) minecraft.player.swing(InteractionHand.MAIN_HAND);
            else if (swingMode.is("Silent")) {
                minecraft.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
            }
            if (particles.isSelected("Critical")) minecraft.player.crit(target);
            if (particles.isSelected("Sharpness")) minecraft.player.magicCrit(target);
        }
    }

    private LivingEntity resolveRayCastTarget() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || attackTarget == null) return null;
        RotationManager rotations = Core.INSTANCE.getRotationManager();
        RotationMath.AimPoint point = aimPoint(attackTarget);
        boolean through = throughWalls.getValue() && point.exists()
                && point.distance() <= throughWallsRange.getValue().doubleValue();
        HitResult hit = RotationRaycast.raycast(
                minecraft.player,
                rotations.getEffectiveRotation(),
                attackRange.getValue().doubleValue(),
                through,
                Entity::isPickable
        );
        if (!(hit instanceof EntityHitResult entityHit)
                || !(entityHit.getEntity() instanceof LivingEntity living)) return null;
        TargetManager targetManager = Core.INSTANCE.getTargetManager();
        if (!targetManager.isTarget(living)) return null;
        RotationMath.AimPoint hitPoint = aimPoint(living);
        return hitPoint.exists() && hitPoint.distance() <= attackRange.getValue().doubleValue() ? living : null;
    }

    private void updateTargets() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        searchedTargets = Core.INSTANCE.getTargetManager().getTargets(searchRange.getValue().doubleValue());
        if (searchedTargets.isEmpty()) {
            targets = List.of();
            attackTarget = null;
            return;
        }

        List<LivingEntity> rotationTargets = searchedTargets.stream()
                .filter(entity -> {
                    RotationMath.AimPoint point = aimPoint(entity);
                    return point.exists() && point.distance() <= rotationRange.getValue().doubleValue();
                }).toList();
        List<LivingEntity> attackTargets = rotationTargets.stream()
                .filter(entity -> aimPoint(entity).distance() <= attackRange.getValue().doubleValue())
                .toList();
        targets = attackTargets.isEmpty() ? rotationTargets : attackTargets;
        if (targets.isEmpty()) {
            attackTarget = null;
            return;
        }
        attackTarget = targets.stream().min(Comparator.comparingDouble(
                switchMode.is("Switch") ? this::switchScore : this::priorityScore)).orElse(null);
    }

    private double priorityScore(LivingEntity entity) {
        return priority.is("Distance") ? aimPoint(entity).distance() : entity.getHealth();
    }

    private double switchScore(LivingEntity entity) {
        double distance = aimPoint(entity).distance();
        return entity.hurtTime * 6.0 + distance;
    }

    private RotationMath.AimPoint aimPoint(LivingEntity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return new RotationMath.AimPoint(null, Double.POSITIVE_INFINITY, false);
        return RotationMath.interactivePoint(
                minecraft.player,
                entity,
                throughWalls.getValue() ? throughWallsRange.getValue().doubleValue() : 0.0
        );
    }

    private MovementFix selectedMovementFix() {
        return switch (movementFix.getValue()) {
            case "Strict" -> MovementFix.STRICT;
            case "Silent" -> MovementFix.SILENT;
            default -> MovementFix.NONE;
        };
    }

    private void stopBlocking() {
        renderBlocking = false;
    }

    private boolean holdingSword() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && minecraft.player.getMainHandItem().is(ItemTags.SWORDS);
    }

    private boolean isEatingOrDrinking() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return false;
        if (minecraft.player.isUsingItem()) {
            return isFoodOrDrink(minecraft.player.getUseItem());
        }
        if (minecraft.options == null || !minecraft.options.keyUse.isDown()) return false;
        return isFoodOrDrink(minecraft.player.getMainHandItem())
                || isFoodOrDrink(minecraft.player.getOffhandItem());
    }

    private boolean isFoodOrDrink(ItemStack stack) {
        ItemUseAnimation animation = stack.getUseAnimation();
        return animation == ItemUseAnimation.EAT || animation == ItemUseAnimation.DRINK;
    }

    private boolean shouldYieldToScaffold() {
        if (!stopWhileScaffolding.getValue() || !Core.INSTANCE.isInitialized()) return false;
        Scaffold scaffold = Core.INSTANCE.getModuleManager().getModule(Scaffold.class);
        return scaffold != null && scaffold.isEnabled();
    }

    private void resetState(boolean releaseRotation) {
        stopBlocking();
        attackTarget = null;
        searchedTargets = List.of();
        targets = List.of();
        requestedRotation = null;
        attackAccumulator = 0.0;
    }
}
