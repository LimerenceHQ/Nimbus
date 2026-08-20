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
import cc.nimbus.features.module.api.setting.impl.submodule.ModeValue;
import cc.nimbus.features.module.api.setting.impl.NumberSetting;
import cc.nimbus.features.rotation.*;
import cc.nimbus.utils.entity.ProjectilePhysicsUtils;
import cc.nimbus.utils.entity.SimulatedPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@ModuleInfo(name = "BowAssistant", category = ModuleCategory.Combat, description = "Aims bows and auto releases fully charged shots.")
public final class BowAssistant extends BasicModule {
    private static final int MAX_FLIGHT_TICKS = 120;
    private static final double HITBOX_EXPAND = 0.34D;
    private static final int IMPACT_TICK_WINDOW = 14;
    private static final int IMPACT_TICK_STEP = 2;
    private static final int MAX_ROTATION_CANDIDATES = 192;
    private static final int MAX_ROTATIONS_PER_IMPACT_TICK = 24;
    private static final int MAX_IMPACT_TICK_CANDIDATES = 15;
    private static final int SIMULATION_EXTRA_TICKS = 18;
    private static final int MAX_TARGET_EVALUATIONS = 3;
    private static final int MAX_SELECTION_CANDIDATES = 8;
    private static final int MAX_VISIBLE_CANDIDATES = 5;
    private static final double MAX_TARGET_RANGE = 72.0D;
    private static final double MAX_SELECTION_ANGLE = 85.0D;
    private static final int RECOMPUTE_INTERVAL_TICKS = 2;
    private static final int POWER_BUCKET_SCALE = 20;
    private static final double VISIBILITY_EPSILON = 1.0E-4D;
    private static final float[] PITCH_OFFSETS = new float[]{-5.0F, -3.0F, -1.0F, 0.0F, 1.0F, 3.0F, 5.0F};

    private final Minecraft minecraft = Minecraft.getInstance();

    public final BooleanSetting bowAimbot = new BooleanSetting("Bow Aimbot", true);
    public final BooleanSetting autoBow = new BooleanSetting("Auto Bow", true);
    public final BooleanSetting predict = new BooleanSetting("Predict", true, bowAimbot::getValue);
    public final NumberSetting minAimPower = new NumberSetting("Min Aim Power", 0.20, 0.0, 1.0, 0.01, bowAimbot::getValue);
    public final NumberSetting rotationSpeed = new NumberSetting("Rotation Speed", 180.0, 0.0, 180.0, 1.0, bowAimbot::getValue);
    public final ModeValue movementFix = new ModeValue("Movement Fix", new String[]{"None", "Strict", "Silent"}, "Silent", bowAimbot::getValue);

    private AimAction aimAction;
    private LivingEntity target;
    private int lastSolveTick = Integer.MIN_VALUE;
    private int lastPowerBucket = -1;
    private boolean autoReleasedThisTick;

    @Override
    protected void onEnable() {
        reset();
    }

    @Override
    protected void onDisable() {
        reset();
    }

    @EventTarget
    private void onWorldLoad(EventWorldLoad event) {
        reset();
    }

    @EventTarget
    private void onTick(EventTick event) {
        if (event.getPhase() != EventTick.Phase.PRE) {
            return;
        }

        autoReleasedThisTick = false;

        if (minecraft.player == null || minecraft.level == null || minecraft.gameMode == null
                || minecraft.screen != null || isKillAuraWorking()) {
            reset();
            return;
        }

        if (!isUsingBow()) {
            reset();
            return;
        }

        if (!bowAimbot.getValue()) {
            clearAim();
            return;
        }

        double power = getBowPower();
        if (power < minAimPower.getValue().doubleValue()) {
            clearAim();
            return;
        }

        int powerBucket = getPowerBucket(power);
        if (canReuseAim(powerBucket)) {
            target = aimAction.target();
            applyRotation(aimAction.rotation());
            return;
        }

        Rotation viewRotation = new Rotation(minecraft.player.getYRot(), minecraft.player.getXRot());
        List<TargetCandidate> candidates = collectTargetCandidates(viewRotation);
        if (candidates.isEmpty()) {
            clearAim();
            return;
        }

        TargetCandidate primaryCandidate = candidates.getFirst();
        if (canReuseAim(primaryCandidate.entity(), powerBucket)) {
            target = aimAction == null ? null : aimAction.target();
            applyRotation(aimAction.rotation());
            return;
        }

        aimAction = findAimAction(candidates, power);
        target = aimAction == null ? null : aimAction.target();
        if (aimAction != null) {
            lastSolveTick = minecraft.player.tickCount;
            lastPowerBucket = powerBucket;
            applyRotation(aimAction.rotation());
        }
    }

    @EventTarget
    private void onClick(EventClick event) {
        if (minecraft.player == null || minecraft.gameMode == null || !isUsingBow() || isKillAuraWorking()) {
            return;
        }

        double power = getBowPower();
        if (!autoBow.getValue() || power < 1.0D) {
            return;
        }

        if (bowAimbot.getValue()) {
            if (minecraft.player.tickCount != lastSolveTick) {
                refreshAimAction(power);
            }

            if (!isAimActionReady()) {
                return;
            }
            applyRotation(aimAction.rotation());
        }

        autoReleasedThisTick = true;
        minecraft.gameMode.releaseUsingItem(minecraft.player);
    }

    private List<TargetCandidate> collectTargetCandidates(Rotation viewRotation) {
        Vec3 origin = minecraft.player.getEyePosition();
        List<TargetCandidate> roughCandidates = new ArrayList<>();

        for (LivingEntity entity : Core.INSTANCE.getTargetManager().getTargets(MAX_TARGET_RANGE)) {
            double distance = ProjectilePhysicsUtils.distanceToBox(origin, entity.getBoundingBox());
            double crosshairScore = getCrosshairScore(viewRotation, entity);
            if (crosshairScore <= MAX_SELECTION_ANGLE) {
                roughCandidates.add(new TargetCandidate(entity, crosshairScore, distance));
            }
        }

        roughCandidates.sort(Comparator
                .comparingDouble(TargetCandidate::crosshairScore)
                .thenComparingDouble(TargetCandidate::distance));

        List<TargetCandidate> candidates = new ArrayList<>();
        int checks = Math.min(roughCandidates.size(), MAX_SELECTION_CANDIDATES);
        for (int i = 0; i < checks && candidates.size() < MAX_VISIBLE_CANDIDATES; i++) {
            TargetCandidate candidate = roughCandidates.get(i);
            if (hasVisibleSelectionPoint(candidate.entity())) {
                candidates.add(candidate);
            }
        }

        LivingEntity crosshairEntity = findCrosshairPriorityTarget(viewRotation, candidates);
        candidates.sort(Comparator.<TargetCandidate>comparingInt(
                        candidate -> candidate.entity() == crosshairEntity ? 0 : 1)
                .thenComparingDouble(TargetCandidate::crosshairScore)
                .thenComparingDouble(TargetCandidate::distance));
        return candidates;
    }

    private void refreshAimAction(double power) {
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        Rotation viewRotation = new Rotation(minecraft.player.getYRot(), minecraft.player.getXRot());
        List<TargetCandidate> candidates = collectTargetCandidates(viewRotation);
        if (candidates.isEmpty()) {
            clearAim();
            return;
        }

        aimAction = findAimAction(candidates, power);
        target = aimAction == null ? null : aimAction.target();
        lastSolveTick = minecraft.player.tickCount;
        lastPowerBucket = getPowerBucket(power);
    }

    private LivingEntity findCrosshairPriorityTarget(Rotation viewRotation, List<TargetCandidate> candidates) {
        if (minecraft.player == null || minecraft.level == null || candidates.isEmpty()) {
            return null;
        }

        Vec3 origin = minecraft.player.getEyePosition();
        double maxDistance = 0.0D;
        for (TargetCandidate candidate : candidates) {
            maxDistance = Math.max(maxDistance,
                    ProjectilePhysicsUtils.distanceToBox(origin, candidate.entity().getBoundingBox()) + 1.0D);
        }

        if (maxDistance <= 0.0D) {
            return null;
        }

        Vec3 end = origin.add(RotationMath.direction(viewRotation).scale(maxDistance));
        BlockHitResult blockHit = minecraft.level.clip(new ClipContext(
                origin,
                end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                minecraft.player
        ));

        double bestDistanceSq = blockHit.getType() == HitResult.Type.MISS
                ? origin.distanceToSqr(end)
                : origin.distanceToSqr(blockHit.getLocation());
        LivingEntity bestEntity = null;

        for (TargetCandidate candidate : candidates) {
            LivingEntity entity = candidate.entity();
            AABB box = entity.getBoundingBox().inflate(entity.getPickRadius());
            var hit = box.clip(origin, end);
            if (hit.isEmpty()) {
                continue;
            }

            double distanceSq = origin.distanceToSqr(hit.get());
            if (distanceSq <= bestDistanceSq) {
                bestDistanceSq = distanceSq;
                bestEntity = entity;
            }
        }

        return bestEntity;
    }

    private boolean hasVisibleSelectionPoint(LivingEntity entity) {
        if (minecraft.player == null || minecraft.level == null) {
            return false;
        }

        Vec3 origin = minecraft.player.getEyePosition();
        AABB box = entity.getBoundingBox().inflate(entity.getPickRadius());
        if (box.contains(origin)) {
            return true;
        }

        for (Vec3 point : sampleSelectionPoints(box)) {
            if (isPointVisible(origin, point)) {
                return true;
            }
        }

        return false;
    }

    private boolean isPointVisible(Vec3 origin, Vec3 point) {
        if (minecraft.level == null) {
            return false;
        }

        BlockHitResult blockHit = minecraft.level.clip(new ClipContext(
                origin,
                point,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                minecraft.player
        ));

        if (blockHit.getType() == HitResult.Type.MISS) {
            return true;
        }

        return origin.distanceToSqr(blockHit.getLocation()) + VISIBILITY_EPSILON >= origin.distanceToSqr(point);
    }

    private AimAction findAimAction(List<TargetCandidate> candidates, double power) {
        if (minecraft.player == null || minecraft.level == null) {
            return null;
        }

        double speed = power * 3.0D;
        if (speed < 0.15D) {
            return null;
        }

        int evaluations = Math.min(candidates.size(), MAX_TARGET_EVALUATIONS);
        AimAction bestAction = null;
        for (int i = 0; i < evaluations; i++) {
            TargetCandidate candidate = candidates.get(i);
            AimSolution solution = findAimSolution(candidate.entity(), speed);
            if (solution == null) {
                continue;
            }

            AimAction action = new AimAction(candidate.entity(), solution.rotation(), solution.score(), candidate.crosshairScore());
            if (bestAction == null
                    || action.crosshairScore() < bestAction.crosshairScore()
                    || (Math.abs(action.crosshairScore() - bestAction.crosshairScore()) < 1.0E-3D
                    && action.score() < bestAction.score())) {
                bestAction = action;
            }
        }

        return bestAction;
    }

    private double getCrosshairScore(Rotation viewRotation, LivingEntity entity) {
        Vec3 origin = minecraft.player.getEyePosition();
        double best = Double.MAX_VALUE;
        for (Vec3 point : sampleSelectionPoints(entity.getBoundingBox())) {
            Rotation rotation = RotationMath.toRotation(origin, point);
            double delta = viewRotation.distanceTo(rotation);
            if (delta < best) {
                best = delta;
            }
        }
        return best;
    }

    private AimSolution findAimSolution(LivingEntity entity, double speed) {
        Vec3 origin = ProjectilePhysicsUtils.getLaunchOrigin(minecraft.player, ProjectilePhysicsUtils.ARROW);
        List<AABB> boxes = predict.getValue() ? predictTargetBoxes(entity) : staticTargetBoxes(entity);
        List<Integer> impactTicks = getCandidateImpactTicks(origin, boxes, speed);
        List<Rotation> rotations = new ArrayList<>();
        int maxSimulationTicks = SIMULATION_EXTRA_TICKS;
        Vec3 inheritedVelocity = ProjectilePhysicsUtils.inheritedVelocity(minecraft.player);

        for (int tick : impactTicks) {
            maxSimulationTicks = Math.max(maxSimulationTicks, tick + SIMULATION_EXTRA_TICKS);
            AABB box = boxes.get(Math.min(tick, boxes.size() - 1));
            int tickRotations = 0;

            tickSearch:
            for (Vec3 point : sampleAimPoints(box)) {
                for (Rotation rotation : ProjectilePhysicsUtils.buildBallisticRotations(
                        ProjectilePhysicsUtils.ARROW, origin, point, speed, inheritedVelocity, tick, PITCH_OFFSETS)) {
                    if (!ProjectilePhysicsUtils.addRotationCandidate(rotations, rotation)) {
                        continue;
                    }

                    tickRotations++;
                    if (tickRotations >= MAX_ROTATIONS_PER_IMPACT_TICK) {
                        break tickSearch;
                    }
                    if (rotations.size() >= MAX_ROTATION_CANDIDATES) {
                        break;
                    }
                }

                if (rotations.size() >= MAX_ROTATION_CANDIDATES) {
                    break;
                }
            }

            if (rotations.size() >= MAX_ROTATION_CANDIDATES) {
                break;
            }
        }

        if (rotations.isEmpty()) {
            return null;
        }

        maxSimulationTicks = Math.min(MAX_FLIGHT_TICKS, Math.max(1, maxSimulationTicks));
        List<AimSolution> solutions = new ArrayList<>();
        Rotation current = currentRotation();

        for (Rotation rotation : rotations) {
            ProjectilePhysicsUtils.SimulationResult result = ProjectilePhysicsUtils.simulateTrajectory(
                    minecraft.level,
                    minecraft.player,
                    ProjectilePhysicsUtils.ARROW,
                    rotation,
                    speed,
                    boxes,
                    maxSimulationTicks,
                    HITBOX_EXPAND,
                    6.0D,
                    40.0D,
                    0.002D,
                    18.0D,
                    0.002D,
                    30.0D
            );
            if (result.hit()) {
                solutions.add(new AimSolution(rotation, result.score()));
            }
        }

        if (solutions.isEmpty()) {
            return null;
        }

        return solutions.stream()
                .min(Comparator
                        .comparingDouble(AimSolution::score)
                        .thenComparingDouble(solution -> current.distanceTo(solution.rotation())))
                .orElse(null);
    }

    private List<AABB> predictTargetBoxes(LivingEntity entity) {
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

    private List<Integer> getCandidateImpactTicks(Vec3 origin, List<AABB> boxes, double speed) {
        List<Integer> ticks = new ArrayList<>();
        if (boxes.isEmpty()) {
            ticks.add(1);
            return ticks;
        }

        int maxTick = Math.min(MAX_FLIGHT_TICKS, boxes.size() - 1);
        int estimatedTick = estimateImpactTick(origin, boxes.getFirst().getCenter(), speed, maxTick);
        int roughTick = estimatedTick;
        for (int i = 0; i < 2; i++) {
            Vec3 predictedCenter = boxes.get(Math.min(estimatedTick, maxTick)).getCenter();
            estimatedTick = estimateImpactTick(origin, predictedCenter, speed, maxTick);
        }

        addImpactTick(ticks, estimatedTick, maxTick);
        addImpactTick(ticks, roughTick, maxTick);

        if (!predict.getValue()) {
            return ticks;
        }

        int adaptiveWindow = Mth.clamp(Math.max(IMPACT_TICK_WINDOW, estimatedTick / 3), IMPACT_TICK_WINDOW, 24);
        for (int offset = 1; offset <= Math.min(3, adaptiveWindow); offset++) {
            addImpactTick(ticks, estimatedTick - offset, maxTick);
            addImpactTick(ticks, estimatedTick + offset, maxTick);
        }

        for (int offset = IMPACT_TICK_STEP;
             offset <= adaptiveWindow && ticks.size() < MAX_IMPACT_TICK_CANDIDATES;
             offset += IMPACT_TICK_STEP) {
            addImpactTick(ticks, estimatedTick - offset, maxTick);
            addImpactTick(ticks, estimatedTick + offset, maxTick);
        }

        return ticks;
    }

    private int estimateImpactTick(Vec3 origin, Vec3 targetPoint, double speed, int maxTick) {
        double distance = origin.distanceTo(targetPoint);
        double effectiveSpeed = ProjectilePhysicsUtils.effectiveSpeed(
                origin,
                targetPoint,
                ProjectilePhysicsUtils.inheritedVelocity(minecraft.player),
                speed,
                0.82D,
                0.15D,
                0.8D
        );
        for (int tick = 1; tick <= maxTick; tick++) {
            double travel = effectiveSpeed * ProjectilePhysicsUtils.ARROW.dragDisplacementScale(tick);
            if (travel >= distance) {
                return tick;
            }
        }

        return Math.max(1, maxTick);
    }

    private void addImpactTick(List<Integer> ticks, int tick, int maxTick) {
        if (ticks.size() >= MAX_IMPACT_TICK_CANDIDATES || tick < 1 || tick > maxTick || ticks.contains(tick)) {
            return;
        }

        ticks.add(tick);
    }

    private List<Vec3> sampleSelectionPoints(AABB box) {
        List<Vec3> points = new ArrayList<>();
        Vec3 center = box.getCenter();
        points.add(center);
        points.add(new Vec3(center.x, box.minY + box.getYsize() * 0.55D, center.z));
        points.add(new Vec3(center.x, box.minY + box.getYsize() * 0.82D, center.z));
        return points;
    }

    private List<Vec3> sampleAimPoints(AABB box) {
        List<Vec3> points = new ArrayList<>();
        Vec3 center = box.getCenter();
        double widthInset = Math.max(0.08D, box.getXsize() * 0.22D);
        double depthInset = Math.max(0.08D, box.getZsize() * 0.22D);
        double lowY = box.minY + box.getYsize() * 0.30D;
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

    private boolean isUsingBow() {
        return minecraft.player != null
                && minecraft.player.isUsingItem()
                && minecraft.player.getUseItem().getItem() instanceof BowItem;
    }

    private double getBowPower() {
        if (!isUsingBow()) {
            return 0.0D;
        }

        double useTicks = minecraft.player.getTicksUsingItem() / 20.0D;
        double power = (useTicks * useTicks + useTicks * 2.0D) / 3.0D;
        return Math.min(power, 1.0D);
    }

    private void applyRotation(Rotation rotation) {
        MovementFix moveFix = switch (movementFix.getValue()) {
            case "Strict" -> MovementFix.STRICT;
            case "Silent" -> MovementFix.SILENT;
            default -> MovementFix.NONE;
        };

        Core.INSTANCE.getRotationManager().setRotations(
                rotation,
                moveFix,
                false,
                SmoothMode.ADVANCED,
                rotationSpeed.getValue().floatValue(),
                0,
                0,
                Priority.MEDIUM
        );
    }

    private Rotation currentRotation() {
        return Core.INSTANCE.getRotationManager().getEffectiveRotation();
    }

    private void clearAim() {
        aimAction = null;
        target = null;
    }

    private void reset() {
        clearAim();
        lastSolveTick = Integer.MIN_VALUE;
        lastPowerBucket = -1;
        autoReleasedThisTick = false;
    }

    private boolean isKillAuraWorking() {
        KillAura killAura = Core.INSTANCE.getModuleManager().getModule(KillAura.class);
        return killAura != null && killAura.isEnabled() && killAura.getAttackTarget() != null;
    }

    public LivingEntity getTarget() {
        return target;
    }

    public boolean autoReleasedThisTick() {
        return autoReleasedThisTick;
    }

    private boolean isAimActionReady() {
        if (minecraft.player == null || minecraft.level == null || aimAction == null
                || aimAction.target() == null || !aimAction.target().isAlive()) {
            return false;
        }
        if (minecraft.player.tickCount - lastSolveTick > RECOMPUTE_INTERVAL_TICKS) {
            return false;
        }
        if (!hasVisibleSelectionPoint(aimAction.target())) {
            return false;
        }

        List<AABB> boxes = predict.getValue()
                ? predictTargetBoxes(aimAction.target())
                : staticTargetBoxes(aimAction.target());
        return ProjectilePhysicsUtils.simulateTrajectory(
                minecraft.level,
                minecraft.player,
                ProjectilePhysicsUtils.ARROW,
                aimAction.rotation(),
                ProjectilePhysicsUtils.ARROW.speed(),
                boxes,
                MAX_FLIGHT_TICKS,
                HITBOX_EXPAND,
                6.0D,
                40.0D,
                0.002D,
                18.0D,
                0.002D,
                30.0D
        ).hit();
    }

    private boolean canReuseAim(int powerBucket) {
        return minecraft.player != null
                && aimAction != null
                && target == aimAction.target()
                && target != null
                && target.isAlive()
                && RotationMath.distanceToBox(minecraft.player, target) <= MAX_TARGET_RANGE + 2.0D
                && minecraft.player.tickCount - lastSolveTick < RECOMPUTE_INTERVAL_TICKS
                && Math.abs(powerBucket - lastPowerBucket) <= 1;
    }

    private boolean canReuseAim(LivingEntity primaryTarget, int powerBucket) {
        return canReuseAim(powerBucket)
                && target == primaryTarget
                && primaryTarget == aimAction.target();
    }

    private int getPowerBucket(double power) {
        return Mth.clamp((int) Math.floor(power * POWER_BUCKET_SCALE), 0, POWER_BUCKET_SCALE);
    }

    private record TargetCandidate(LivingEntity entity, double crosshairScore, double distance) {
    }

    private record AimAction(LivingEntity target, Rotation rotation, double score, double crosshairScore) {
    }

    private record AimSolution(Rotation rotation, double score) {
    }
}
