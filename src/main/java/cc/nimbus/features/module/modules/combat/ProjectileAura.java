package cc.nimbus.features.module.modules.combat;

import cc.nimbus.core.Core;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.game.EventClick;
import cc.nimbus.event.impl.game.EventTick;
import cc.nimbus.event.impl.game.EventWorldLoad;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.BooleanSetting;
import cc.nimbus.features.module.api.setting.impl.ModeSetting;
import cc.nimbus.features.module.api.setting.impl.NumberSetting;
import cc.nimbus.features.rotation.MovementFix;
import cc.nimbus.features.rotation.Rotation;
import cc.nimbus.features.rotation.RotationMath;
import cc.nimbus.features.rotation.RotationPriority;
import cc.nimbus.features.rotation.RotationRequest;
import cc.nimbus.features.rotation.SmoothMode;
import cc.nimbus.utils.entity.ProjectilePhysicsUtils;
import cc.nimbus.utils.entity.SimulatedPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@ModuleInfo(name = "ProjectileAura", category = ModuleCategory.Combat, description = "Throws snowballs and eggs at predicted targets.")
public final class ProjectileAura extends BasicModule {
    private final Minecraft minecraft = Minecraft.getInstance();
    private static final int MAX_FLIGHT_TICKS = 80;
    private static final double HITBOX_EXPAND = 0.32D;
    private static final int MAX_TARGET_EVALUATIONS = 10;
    private static final int IMPACT_TICK_WINDOW = 10;
    private static final int MAX_ROTATION_CANDIDATES = 320;
    private static final int SIMULATION_EXTRA_TICKS = 14;
    private static final int MAX_REFINEMENT_SEEDS = 3;
    private static final int SOLVE_INTERVAL_TICKS = 2;
    private static final int REQUIRED_PREDICTION_STABLE_TICKS = 2;
    private static final double PREHEAT_RANGE_EXTENSION = 5.0D;
    private static final double MAX_PREHEAT_RANGE = 24.0D;
    private static final double STABLE_BOX_DISTANCE_SQ = 1.0D;
    private static final double STABLE_ROTATION_DELTA = 6.0D;
    private static final float THROW_READY_YAW_DELTA = 1.0F;
    private static final float THROW_READY_PITCH_DELTA = 1.0F;
    private static final float[] PITCH_OFFSETS = new float[]{-6.0F, -4.0F, -2.0F, -1.0F, 0.0F, 1.0F, 2.0F, 4.0F, 6.0F};
    private static final float[] REFINEMENT_OFFSETS = new float[]{-0.8F, 0.0F, 0.8F};

    public final ModeSetting switchMode = new ModeSetting("Switch Mode", new String[]{"Single", "Switch"}, "Single");
    public final ModeSetting priority = new ModeSetting("Priority", new String[]{"Health", "Distance"}, "Health", () -> switchMode.is("Single"));
    public final BooleanSetting predict = new BooleanSetting("Predict", true);
    public final BooleanSetting snowball = new BooleanSetting("Snowball", true);
    public final BooleanSetting egg = new BooleanSetting("Egg", true);
    public final NumberSetting minRange = new NumberSetting("Min Range", 3.0, 0.0, 12.0, 0.01);
    public final NumberSetting maxRange = new NumberSetting("Max Range", 15.0, 0.0, 20.0, 0.01);
    public final NumberSetting delay = new NumberSetting("Delay", 350.0, 0.0, 3000.0, 50.0);
    public final NumberSetting rotationSpeed = new NumberSetting("Rotation Speed", 180.0, 0.0, 180.0, 1.0);
    public final ModeSetting movementFix = new ModeSetting("Movement Fix", new String[]{"None", "Strict", "Silent"}, "Silent");
    public final ModeSetting swingMode = new ModeSetting("Swing Mode", new String[]{"Off", "Normal", "Silent"}, "Normal");

    private long lastThrowTime;
    private ThrowAction throwAction;
    private LivingEntity target;
    private int oldSlot = -1;
    private int rotationReadyTicks;
    private int lastSolveTick = Integer.MIN_VALUE;
    private int predictionStableTicks;
    private int predictionStableTick = Integer.MIN_VALUE;
    private ThrowAction lastStabilityAction;
    private boolean forceFreshSolve;

    @Override
    protected void onEnable() {
        reset(true);
        lastSolveTick = Integer.MIN_VALUE;
        resetTimer();
    }

    @Override
    protected void onDisable() {
        reset(true);
        lastSolveTick = Integer.MIN_VALUE;
    }

    @EventTarget
    private void onWorldLoad(EventWorldLoad event) {
        reset(true);
        lastSolveTick = Integer.MIN_VALUE;
    }

    @EventTarget
    private void onTick(EventTick event) {
        if (event.getPhase() != EventTick.Phase.PRE) {
            return;
        }

        if (minecraft.player == null || minecraft.level == null || minecraft.gameMode == null || minecraft.screen != null || minecraft.player.isUsingItem() || isKillAuraWorking()) {
            reset(true);
            return;
        }

        if (findProjectileSlot() == -1) {
            reset(true);
            return;
        }

        forceFreshSolve = shouldForceFreshSolve();
        throwAction = getThrowAction();
        forceFreshSolve = false;
        updatePredictionStability(throwAction);
        if (throwAction == null) {
            target = null;
            rotationReadyTicks = 0;
            return;
        }

        target = throwAction.target();
        if (!shouldPrepareThrowAction(throwAction)) {
            rotationReadyTicks = 0;
            return;
        }

        applyRotation(throwAction.rotation());
        updateRotationReadyTicks();
    }

    @EventTarget
    private void onClick(EventClick event) {
        if (minecraft.player == null || minecraft.level == null || minecraft.gameMode == null || throwAction == null || minecraft.player.isUsingItem() || isKillAuraWorking()) {
            if (throwAction != null || isKillAuraWorking()) {
                reset(true);
            }
            return;
        }

        if (!shouldPrepareThrowAction(throwAction)) {
            return;
        }

        if (!refreshThrowActionForThrow()) {
            return;
        }

        if (!isThrowActionReady(throwAction)) {
            return;
        }

        if (!isPredictionStableForThrow()) {
            applyRotation(throwAction.rotation());
            return;
        }

        if (!isRotationReadyToThrow()) {
            applyRotation(throwAction.rotation());
            return;
        }

        oldSlot = minecraft.player.getInventory().getSelectedSlot();
        minecraft.player.getInventory().setSelectedSlot(throwAction.slot());
        InteractionResult result = useItem(throwAction.rotation());
        if (result.consumesAction() || result == InteractionResult.SUCCESS) {
            swing();
            resetTimer();
        }

        reset(true);
    }

    private ThrowAction getThrowAction() {
        if (minecraft.player == null) {
            return null;
        }

        int elapsed = minecraft.player.tickCount - lastSolveTick;
        if (forceFreshSolve && throwAction != null && lastSolveTick == minecraft.player.tickCount && shouldKeepThrowAction(throwAction)) {
            return throwAction;
        }
        if (!forceFreshSolve && throwAction != null && elapsed < getSolveInterval(throwAction) && shouldKeepThrowAction(throwAction)) {
            return throwAction;
        }
        if (!forceFreshSolve && throwAction == null && elapsed < SOLVE_INTERVAL_TICKS) {
            return null;
        }

        ThrowAction solved = findThrowAction();
        lastSolveTick = minecraft.player.tickCount;
        return solved;
    }

    private boolean refreshThrowActionForThrow() {
        forceFreshSolve = true;
        throwAction = getThrowAction();
        forceFreshSolve = false;
        updatePredictionStability(throwAction);
        if (throwAction == null) {
            return false;
        }

        target = throwAction.target();
        return true;
    }

    private int getSolveInterval(ThrowAction action) {
        if (forceFreshSolve || action != null && shouldPrepareThrowAction(action)) {
            return 1;
        }

        return SOLVE_INTERVAL_TICKS;
    }

    private ThrowAction findThrowAction() {
        if (minecraft.player == null || minecraft.level == null) {
            return null;
        }

        int slot = findProjectileSlot();
        if (slot == -1) {
            return null;
        }
        ProjectilePhysicsUtils.ProjectileProfile profile = getProjectileProfile(slot);

        double effectiveSearchRange = getEffectiveSearchRange();
        List<TargetCandidate> candidates = new ArrayList<>();
        for (LivingEntity entity : Core.INSTANCE.getTargetManager().getTargets(effectiveSearchRange)) {
            double distance = RotationMath.distanceToBox(minecraft.player, entity);
            if (distance < minRange.getValue().doubleValue() || distance > effectiveSearchRange) {
                continue;
            }

            candidates.add(new TargetCandidate(entity, distance));
        }

        if (candidates.isEmpty()) {
            return null;
        }

        if (switchMode.is("Single")) {
            candidates.sort(Comparator
                    .<TargetCandidate, Boolean>comparing(candidate -> !isWithinThrowDistance(candidate.distance()))
                    .thenComparingDouble(candidate -> getPriorityValue(candidate.entity()))
                    .thenComparingDouble(TargetCandidate::distance));
        } else {
            candidates.sort(Comparator
                    .<TargetCandidate, Boolean>comparing(candidate -> !isWithinThrowDistance(candidate.distance()))
                    .thenComparingDouble(candidate -> candidate.entity().hurtTime * 6.0D + candidate.distance())
                    .thenComparingDouble(TargetCandidate::distance));
        }

        List<ThrowAction> actions = new ArrayList<>();
        int evaluated = 0;
        for (TargetCandidate candidate : candidates) {
            if (evaluated++ >= MAX_TARGET_EVALUATIONS) {
                break;
            }

            AimSolution solution = findAimSolution(candidate.entity(), profile);
            if (solution != null) {
                actions.add(new ThrowAction(candidate.entity(), slot, solution.rotation(), solution.score(), candidate.distance(), solution.predictedBox(), profile));
            }
        }

        if (actions.isEmpty()) {
            return null;
        }

        if (switchMode.is("Single")) {
            return actions.stream()
                    .min(Comparator
                            .<ThrowAction, Boolean>comparing(action -> !isWithinThrowDistance(action.distance()))
                            .thenComparingDouble(action -> getPriorityValue(action.target()))
                            .thenComparingDouble(ThrowAction::distance)
                            .thenComparingDouble(ThrowAction::score))
                    .orElse(null);
        }

        return actions.stream()
                .min(Comparator
                        .<ThrowAction, Boolean>comparing(action -> !isWithinThrowDistance(action.distance()))
                        .thenComparingDouble(action -> action.target().hurtTime * 6.0D + action.distance())
                        .thenComparingDouble(ThrowAction::score))
                .orElse(null);
    }

    private boolean shouldForceFreshSolve() {
        if (minecraft.player == null || minecraft.level == null || !hasDelayElapsed()) {
            return false;
        }

        for (LivingEntity entity : Core.INSTANCE.getTargetManager().getTargets(maxRange.getValue().doubleValue())) {
            double distance = RotationMath.distanceToBox(minecraft.player, entity);
            if (isWithinThrowDistance(distance)) {
                return true;
            }
        }

        return false;
    }

    private double getEffectiveSearchRange() {
        double range = maxRange.getValue().doubleValue();
        return Math.max(range, Math.min(MAX_PREHEAT_RANGE, range + PREHEAT_RANGE_EXTENSION));
    }

    private AimSolution findAimSolution(LivingEntity entity, ProjectilePhysicsUtils.ProjectileProfile profile) {
        if (minecraft.player == null || minecraft.level == null) {
            return null;
        }

        Vec3 origin = ProjectilePhysicsUtils.getLaunchOrigin(minecraft.player, profile);
        List<AABB> boxes = predict.getValue() ? predictTargetBoxes(entity) : staticTargetBoxes(entity);
        List<Integer> impactTicks = getCandidateImpactTicks(origin, boxes, profile);
        List<Rotation> rotations = new ArrayList<>();
        int maxSimulationTicks = SIMULATION_EXTRA_TICKS;
        Vec3 inheritedVelocity = ProjectilePhysicsUtils.inheritedVelocity(minecraft.player);

        search:
        for (int tick : impactTicks) {
            maxSimulationTicks = Math.max(maxSimulationTicks, tick + SIMULATION_EXTRA_TICKS);
            AABB box = boxes.get(Math.min(tick, boxes.size() - 1));
            for (Vec3 point : sampleTargetPoints(origin, box)) {
                for (Rotation rotation : ProjectilePhysicsUtils.buildBallisticRotations(profile, origin, point, profile.speed(), inheritedVelocity, tick, PITCH_OFFSETS)) {
                    ProjectilePhysicsUtils.addRotationCandidate(rotations, rotation);
                    if (rotations.size() >= MAX_ROTATION_CANDIDATES) {
                        break search;
                    }
                }
            }
        }

        if (rotations.isEmpty()) {
            return null;
        }

        maxSimulationTicks = Math.min(MAX_FLIGHT_TICKS, Math.max(1, maxSimulationTicks));
        List<AimSolution> solutions = new ArrayList<>();
        List<RefinementSeed> refinementSeeds = new ArrayList<>();

        for (Rotation rotation : rotations) {
            ProjectilePhysicsUtils.SimulationResult result = simulateProjectile(profile, rotation, boxes, maxSimulationTicks);
            if (result.hit()) {
                solutions.add(new AimSolution(rotation, result.score(), result.predictedBox()));
            } else if (!result.blocked() && result.closestDistance() <= HITBOX_EXPAND + 0.18D) {
                addRefinementSeed(refinementSeeds, new RefinementSeed(rotation, result.score(), result.closestDistance()));
            }
        }

        if (solutions.isEmpty() && !refinementSeeds.isEmpty()) {
            refineProjectileSolutions(refinementSeeds, profile, boxes, maxSimulationTicks, solutions);
        }

        if (solutions.isEmpty()) {
            return null;
        }

        Rotation current = currentRotation();
        return solutions.stream()
                .min(Comparator
                        .comparingDouble(AimSolution::score)
                        .thenComparingDouble(solution -> current.distanceTo(solution.rotation())))
                .orElse(null);
    }

    private List<Integer> getCandidateImpactTicks(Vec3 origin, List<AABB> boxes, ProjectilePhysicsUtils.ProjectileProfile profile) {
        List<Integer> ticks = new ArrayList<>();
        if (boxes.isEmpty()) {
            ticks.add(1);
            return ticks;
        }

        int maxTick = Math.min(MAX_FLIGHT_TICKS, boxes.size() - 1);
        double distance = origin.distanceTo(boxes.get(0).getCenter());
        double effectiveSpeed = ProjectilePhysicsUtils.effectiveSpeed(origin, boxes.get(0).getCenter(), ProjectilePhysicsUtils.inheritedVelocity(minecraft.player), profile.speed(), 1.0D, 0.35D, 0.8D);
        int estimatedTick = Mth.clamp((int) Math.round(distance / effectiveSpeed), 1, Math.max(1, maxTick));

        ticks.add(estimatedTick);

        if (!predict.getValue()) {
            return ticks;
        }

        for (int offset = 1; offset <= IMPACT_TICK_WINDOW; offset++) {
            int earlier = estimatedTick - offset;
            if (earlier >= 1) {
                ticks.add(earlier);
            }

            int later = estimatedTick + offset;
            if (later <= maxTick) {
                ticks.add(later);
            }
        }
        return ticks;
    }

    private List<AABB> predictTargetBoxes(LivingEntity entity) {
        if (minecraft.level == null) {
            List<AABB> boxes = new ArrayList<>();
            boxes.add(entity.getBoundingBox());
            return boxes;
        }

        return SimulatedPlayer.predictBoxes(entity, MAX_FLIGHT_TICKS);
    }

    private List<AABB> staticTargetBoxes(LivingEntity entity) {
        List<AABB> boxes = new ArrayList<>();
        AABB box = entity.getBoundingBox();
        for (int tick = 0; tick <= MAX_FLIGHT_TICKS; tick++) {
            boxes.add(box);
        }
        return boxes;
    }

    private List<Vec3> sampleTargetPoints(Vec3 origin, AABB box) {
        List<Vec3> points = new ArrayList<>();
        Vec3 center = box.getCenter();
        double widthInset = Math.max(0.08D, box.getXsize() * 0.22D);
        double depthInset = Math.max(0.08D, box.getZsize() * 0.22D);
        double lowY = box.minY + box.getYsize() * 0.32D;
        double midY = box.minY + box.getYsize() * 0.55D;
        double highY = box.minY + box.getYsize() * 0.80D;

        points.add(center);
        points.add(new Vec3(center.x, lowY, center.z));
        points.add(new Vec3(center.x, midY, center.z));
        points.add(new Vec3(center.x, highY, center.z));
        points.add(new Vec3(box.minX + widthInset, midY, center.z));
        points.add(new Vec3(box.maxX - widthInset, midY, center.z));
        points.add(new Vec3(center.x, midY, box.minZ + depthInset));
        points.add(new Vec3(center.x, midY, box.maxZ - depthInset));
        return points;
    }

    private void addRefinementSeed(List<RefinementSeed> seeds, RefinementSeed seed) {
        seeds.add(seed);
        seeds.sort(Comparator
                .comparingDouble(RefinementSeed::closestDistance)
                .thenComparingDouble(RefinementSeed::score));
        if (seeds.size() > MAX_REFINEMENT_SEEDS) {
            seeds.remove(seeds.size() - 1);
        }
    }

    private void refineProjectileSolutions(List<RefinementSeed> seeds, ProjectilePhysicsUtils.ProjectileProfile profile, List<AABB> boxes, int maxSimulationTicks, List<AimSolution> solutions) {
        List<Rotation> refinedRotations = new ArrayList<>();
        for (RefinementSeed seed : seeds) {
            Rotation base = seed.rotation();
            for (float yawOffset : REFINEMENT_OFFSETS) {
                for (float pitchOffset : REFINEMENT_OFFSETS) {
                    ProjectilePhysicsUtils.addRotationCandidate(refinedRotations, new Rotation(
                            Mth.wrapDegrees(base.yaw() + yawOffset),
                            Mth.clamp(base.pitch() + pitchOffset, -90.0F, 90.0F)
                    ).normalized());
                }
            }
        }

        for (Rotation rotation : refinedRotations) {
            ProjectilePhysicsUtils.SimulationResult result = simulateProjectile(profile, rotation, boxes, maxSimulationTicks);
            if (result.hit()) {
                solutions.add(new AimSolution(rotation, result.score(), result.predictedBox()));
            }
        }
    }

    private ProjectilePhysicsUtils.SimulationResult simulateProjectile(ProjectilePhysicsUtils.ProjectileProfile profile, Rotation rotation, List<AABB> boxes, int maxTicks) {
        return ProjectilePhysicsUtils.simulateTrajectory(
                minecraft.level,
                minecraft.player,
                profile,
                rotation,
                profile.speed(),
                boxes,
                maxTicks,
                HITBOX_EXPAND,
                6.0D,
                40.0D,
                0.002D,
                18.0D,
                0.002D,
                30.0D
        );
    }

    private int findProjectileSlot() {
        if (minecraft.player == null) {
            return -1;
        }

        int selected = minecraft.player.getInventory().getSelectedSlot();
        if (isProjectile(minecraft.player.getInventory().getItem(selected))) {
            return selected;
        }

        for (int slot = 0; slot < 9; slot++) {
            if (isProjectile(minecraft.player.getInventory().getItem(slot))) {
                return slot;
            }
        }
        return -1;
    }

    private ProjectilePhysicsUtils.ProjectileProfile getProjectileProfile(int slot) {
        if (minecraft.player == null || slot < 0 || slot > 8) {
            return ProjectilePhysicsUtils.THROWABLE;
        }

        ProjectilePhysicsUtils.ProjectileProfile profile = ProjectilePhysicsUtils.profileForItem(minecraft.player.getInventory().getItem(slot));
        return profile == null ? ProjectilePhysicsUtils.THROWABLE : profile;
    }

    private boolean isProjectile(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        if (snowball.getValue() && stack.is(Items.SNOWBALL)) {
            return true;
        }
        return egg.getValue() && stack.is(Items.EGG);
    }

    private double getPriorityValue(LivingEntity entity) {
        if (minecraft.player == null) {
            return Double.MAX_VALUE;
        }

        return switch (priority.getValue()) {
            case "Health" -> entity.getHealth();
            case "Distance" -> RotationMath.distanceToBox(minecraft.player, entity);
            default -> Double.MAX_VALUE;
        };
    }

    private InteractionResult useItem(Rotation rotation) {
        if (minecraft.player == null || minecraft.gameMode == null) {
            return InteractionResult.FAIL;
        }

        float currentYaw = minecraft.player.getYRot();
        float currentPitch = minecraft.player.getXRot();
        minecraft.player.setYRot(rotation.yaw());
        minecraft.player.setXRot(rotation.pitch());
        InteractionResult result = minecraft.gameMode.useItem(minecraft.player, InteractionHand.MAIN_HAND);
        minecraft.player.setYRot(currentYaw);
        minecraft.player.setXRot(currentPitch);
        return result;
    }

    private boolean isThrowActionReady(ThrowAction action) {
        if (!shouldPrepareThrowAction(action)) {
            return false;
        }

        List<AABB> boxes = predict.getValue() ? predictTargetBoxes(action.target()) : staticTargetBoxes(action.target());
        return simulateProjectile(action.profile(), action.rotation(), boxes, MAX_FLIGHT_TICKS).hit();
    }

    private boolean shouldPrepareThrowAction(ThrowAction action) {
        return shouldKeepThrowAction(action)
                && hasDelayElapsed()
                && isWithinThrowRange(action.target())
                && hasValidProjectileSlot(action);
    }

    private boolean shouldKeepThrowAction(ThrowAction action) {
        if (minecraft.player == null || minecraft.level == null || action == null || action.target() == null || !action.target().isAlive()) {
            return false;
        }

        double distance = RotationMath.distanceToBox(minecraft.player, action.target());
        return distance >= minRange.getValue().doubleValue() && distance <= getEffectiveSearchRange();
    }

    private boolean hasValidProjectileSlot(ThrowAction action) {
        return minecraft.player != null
                && action != null
                && action.slot() >= 0
                && action.slot() <= 8
                && isProjectile(minecraft.player.getInventory().getItem(action.slot()));
    }

    private boolean isWithinThrowRange(LivingEntity entity) {
        if (minecraft.player == null || entity == null) {
            return false;
        }

        return isWithinThrowDistance(RotationMath.distanceToBox(minecraft.player, entity));
    }

    private boolean isWithinThrowDistance(double distance) {
        return distance >= minRange.getValue().doubleValue() && distance <= maxRange.getValue().doubleValue();
    }

    private void applyRotation(Rotation rotation) {
        MovementFix moveFix = switch (movementFix.getValue()) {
            case "Strict" -> MovementFix.STRICT;
            case "Silent" -> MovementFix.SILENT;
            default -> MovementFix.NONE;
        };

        Core.INSTANCE.getRotationManager().submit(this, new RotationRequest(
                rotation,
                RotationPriority.MEDIUM,
                moveFix,
                false,
                SmoothMode.ADVANCED,
                rotationSpeed.getValue().floatValue(),
                0,
                0
        ));
    }

    private void swing() {
        if (minecraft.player == null) {
            return;
        }
        if (swingMode.is("Normal")) {
            minecraft.player.swing(InteractionHand.MAIN_HAND);
        } else if (swingMode.is("Silent") && minecraft.getConnection() != null) {
            minecraft.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
        }
    }

    private void restoreSlot() {
        if (oldSlot != -1 && minecraft.player != null) {
            minecraft.player.getInventory().setSelectedSlot(oldSlot);
        }
        oldSlot = -1;
    }

    private boolean isPredictionStableForThrow() {
        return predictionStableTicks >= REQUIRED_PREDICTION_STABLE_TICKS;
    }

    private void updatePredictionStability(ThrowAction action) {
        if (minecraft.player == null || action == null || !shouldKeepThrowAction(action) || !hasValidProjectileSlot(action)) {
            resetPredictionStability();
            return;
        }

        if (minecraft.player.tickCount == predictionStableTick) {
            if (!isStableContinuation(lastStabilityAction, action)) {
                predictionStableTicks = 1;
                lastStabilityAction = action;
            }
            return;
        }

        if (isStableContinuation(lastStabilityAction, action)) {
            predictionStableTicks++;
        } else {
            predictionStableTicks = 1;
        }

        predictionStableTick = minecraft.player.tickCount;
        lastStabilityAction = action;
    }

    private boolean isStableContinuation(ThrowAction previous, ThrowAction current) {
        if (previous == null || current == null || previous.target() != current.target()) {
            return false;
        }

        if (previous.rotation().distanceTo(current.rotation()) > STABLE_ROTATION_DELTA) {
            return false;
        }

        if (previous.predictedBox() == null || current.predictedBox() == null) {
            return previous.predictedBox() == current.predictedBox();
        }

        return previous.predictedBox().getCenter().distanceToSqr(current.predictedBox().getCenter()) <= STABLE_BOX_DISTANCE_SQ;
    }

    private void resetPredictionStability() {
        predictionStableTicks = 0;
        predictionStableTick = Integer.MIN_VALUE;
        lastStabilityAction = null;
    }

    private void updateRotationReadyTicks() {
        if (throwAction == null) {
            rotationReadyTicks = 0;
            return;
        }

        Rotation targetRotation = Core.INSTANCE.getRotationManager().getTargetRotation();
        if (isAligned(targetRotation, throwAction.rotation())) {
            rotationReadyTicks++;
        } else {
            rotationReadyTicks = 0;
        }
    }

    private boolean isRotationReadyToThrow() {
        return rotationReadyTicks > 0
                && throwAction != null
                && isAligned(Core.INSTANCE.getRotationManager().getTargetRotation(), throwAction.rotation());
    }

    private boolean isAligned(Rotation current, Rotation expected) {
        if (current == null || expected == null) {
            return false;
        }

        return Math.abs(Mth.wrapDegrees(current.yaw() - expected.yaw())) <= THROW_READY_YAW_DELTA
                && Math.abs(current.pitch() - expected.pitch()) <= THROW_READY_PITCH_DELTA;
    }

    private void reset(boolean restoreSlot) {
        if (restoreSlot) {
            restoreSlot();
        } else {
            oldSlot = -1;
        }

        throwAction = null;
        target = null;
        rotationReadyTicks = 0;
        resetPredictionStability();
        if (Core.INSTANCE.isInitialized()) Core.INSTANCE.getRotationManager().cancel(this);
    }

    private Rotation currentRotation() {
        if (Core.INSTANCE.isInitialized()) {
            return Core.INSTANCE.getRotationManager().getEffectiveRotation();
        }
        return minecraft.player == null
                ? new Rotation(0.0F, 0.0F)
                : new Rotation(minecraft.player.getYRot(), minecraft.player.getXRot());
    }

    private boolean hasDelayElapsed() {
        return System.currentTimeMillis() - lastThrowTime >= delay.getValue().longValue();
    }

    private void resetTimer() {
        lastThrowTime = System.currentTimeMillis();
    }

    private boolean isKillAuraWorking() {
        KillAura killAura = Core.INSTANCE.getModuleManager().getModule(KillAura.class);
        return killAura != null && killAura.isEnabled() && killAura.getAttackTarget() != null;
    }

    private record ThrowAction(LivingEntity target, int slot, Rotation rotation, double score, double distance, AABB predictedBox, ProjectilePhysicsUtils.ProjectileProfile profile) {
    }

    private record TargetCandidate(LivingEntity entity, double distance) {
    }

    private record AimSolution(Rotation rotation, double score, AABB predictedBox) {
    }

    private record RefinementSeed(Rotation rotation, double score, double closestDistance) {
    }

}
