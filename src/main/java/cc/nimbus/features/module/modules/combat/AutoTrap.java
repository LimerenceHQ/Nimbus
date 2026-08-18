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
import cc.nimbus.features.module.modules.player.FireGuard;
import cc.nimbus.features.rotation.MovementFix;
import cc.nimbus.features.rotation.Rotation;
import cc.nimbus.features.rotation.RotationMath;
import cc.nimbus.features.rotation.RotationPriority;
import cc.nimbus.features.rotation.RotationRequest;
import cc.nimbus.features.rotation.SmoothMode;
import cc.nimbus.utils.entity.SimulatedPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@ModuleInfo(
        name = "AutoTrap",
        description = "Traps nearby targets with cobwebs, lava or fire.",
        category = ModuleCategory.Combat
)
public final class AutoTrap extends BasicModule {
    private static final double MAX_BLOCK_INTERACTION_RANGE = 4.5D;
    private static final int MAX_BURN_WAIT_TICKS = 5;
    private static final Direction[] PLACE_DIRECTIONS = new Direction[]{
            Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN
    };
    private static final double[] FACE_OFFSETS = new double[]{
            0.08D, 0.20D, 0.32D, 0.44D, 0.56D, 0.68D, 0.80D, 0.92D
    };

    private final Minecraft minecraft = Minecraft.getInstance();

    public final BooleanSetting webs = new BooleanSetting("Webs", true);
    public final BooleanSetting lava = new BooleanSetting("Lava", true);
    public final BooleanSetting flint = new BooleanSetting("Flint", true);
    public final NumberSetting targetRange = new NumberSetting("Target Range", 3.0, 0.0, 6.0, 0.01);
    public final NumberSetting searchRange = new NumberSetting("Search Range", 6.0, 0.0, 12.0, 0.01);
    public final NumberSetting minPlaceRange = new NumberSetting(
            "Min Place Range", 3.0, 0.0, MAX_BLOCK_INTERACTION_RANGE, 0.01);
    public final NumberSetting placeRange = new NumberSetting(
            "Place Range", MAX_BLOCK_INTERACTION_RANGE, 0.0, MAX_BLOCK_INTERACTION_RANGE, 0.01);
    public final NumberSetting webCount = new NumberSetting(
            "Web Count", 2.0, 1.0, 2.0, 1.0, webs::getValue);
    public final NumberSetting delay = new NumberSetting("Delay", 4.0, 0.0, 20.0, 1.0);
    public final NumberSetting rotationSpeed = new NumberSetting("Rotation Speed", 180.0, 0.0, 180.0, 1.0);
    public final ModeSetting swingMode = new ModeSetting(
            "Swing Mode", new String[]{"Off", "Normal", "Silent"}, "Normal");
    public final ModeSetting movementFix = new ModeSetting(
            "Movement Fix", new String[]{"None", "Strict", "Silent"}, "Silent");
    public final BooleanSetting avoidSelf = new BooleanSetting("Avoid Self", true);

    private Stage stage = Stage.IDLE;
    private LavaAction lavaAction;
    private PickupAction pickupAction;
    private IgniteAction igniteAction;
    private ClearAction clearAction;
    private LivingEntity lavaTarget;
    private LivingEntity igniteTarget;
    private BlockPos placedLavaPos;
    private BlockPos placedFirePos;
    private WebAction webAction;
    private final List<WebAction> webPlan = new ArrayList<>();
    private LavaAction previewLavaAction;
    private IgniteAction previewIgniteAction;
    private final List<WebAction> previewWebPlan = new ArrayList<>();
    private int oldSlot = -1;
    private int lavaTicks;
    private int igniteTicks;
    private int cooldownTicks;

    @Override
    protected void onEnable() {
        reset(true);
        cooldownTicks = 0;
    }

    @Override
    protected void onDisable() {
        reset(true);
        cooldownTicks = 0;
    }

    @EventTarget
    private void onWorldLoad(EventWorldLoad event) {
        reset(true);
        cooldownTicks = 0;
    }

    @EventTarget
    private void onTick(EventTick event) {
        if (event.getPhase() != EventTick.Phase.PRE) return;

        if (cooldownTicks > 0) cooldownTicks--;
        if (stage == Stage.LAVA_WAIT_BURN) lavaTicks++;
        if (stage == Stage.IGNITE_WAIT_BURN) igniteTicks++;

        if (!validEnvironment() || shouldYieldToItemUse()) {
            reset(true);
            return;
        }

        if (stage == Stage.IDLE) refreshPreviewActions();

        if (isKillAuraWorking() || isFireGuardWorking()) {
            if (stage != Stage.IDLE) reset(true);
            return;
        }

        if (stage == Stage.LAVA_WAIT_BURN) {
            if (shouldWaitLavaBurn()) return;
            pickupAction = findLavaPickupAction();
            int bucketSlot = findHotbarSlot(Items.BUCKET);
            if (pickupAction == null || bucketSlot == -1) {
                reset(true);
                return;
            }
            minecraft.player.getInventory().setSelectedSlot(bucketSlot);
            stage = Stage.LAVA_PICKUP;
            applyRotation(pickupAction.rotation(), RotationPriority.HIGH);
            return;
        }

        if (stage == Stage.LAVA_PICKUP && pickupAction != null) {
            applyRotation(pickupAction.rotation(), RotationPriority.HIGH);
            return;
        }

        if (stage == Stage.IGNITE_WAIT_BURN) {
            if (shouldWaitIgniteBurn()) return;
            clearAction = findFireClearAction(placedFirePos);
            if (clearAction == null) {
                reset(false);
                cooldownTicks = delayTicks();
                return;
            }
            stage = Stage.IGNITE_CLEAR;
            applyRotation(clearAction.rotation());
            return;
        }

        if (stage == Stage.IGNITE_CLEAR && clearAction != null) {
            applyRotation(clearAction.rotation());
            return;
        }

        if (stage == Stage.IGNITE_PLACE && igniteAction != null) {
            int slot = findHotbarSlot(Items.FLINT_AND_STEEL);
            if (slot == -1) {
                reset(true);
                return;
            }
            minecraft.player.getInventory().setSelectedSlot(slot);
            applyRotation(igniteAction.rotation());
            return;
        }

        if (stage == Stage.LAVA_PLACE && lavaAction != null) {
            int slot = findHotbarSlot(Items.LAVA_BUCKET);
            if (slot == -1) {
                reset(true);
                return;
            }
            minecraft.player.getInventory().setSelectedSlot(slot);
            applyRotation(lavaAction.rotation());
            return;
        }

        if (stage == Stage.WEB_PLACE && webAction != null) {
            int slot = findHotbarSlot(Items.COBWEB);
            if (slot == -1) {
                reset(true);
                return;
            }
            minecraft.player.getInventory().setSelectedSlot(slot);
            applyRotation(webAction.rotation());
            return;
        }

        if (cooldownTicks > 0) return;

        if (lava.getValue() && findHotbarSlot(Items.LAVA_BUCKET) != -1) {
            lavaAction = isWithinTargetRange(previewLavaAction) ? previewLavaAction : findLavaAction(true);
            if (lavaAction != null) {
                oldSlot = minecraft.player.getInventory().getSelectedSlot();
                lavaTarget = lavaAction.target();
                minecraft.player.getInventory().setSelectedSlot(findHotbarSlot(Items.LAVA_BUCKET));
                stage = Stage.LAVA_PLACE;
                applyRotation(lavaAction.rotation());
                return;
            }
        }

        if (flint.getValue() && findHotbarSlot(Items.FLINT_AND_STEEL) != -1) {
            igniteAction = isWithinTargetRange(previewIgniteAction) ? previewIgniteAction : findIgniteAction(true);
            if (igniteAction != null) {
                oldSlot = minecraft.player.getInventory().getSelectedSlot();
                igniteTarget = igniteAction.target();
                minecraft.player.getInventory().setSelectedSlot(findHotbarSlot(Items.FLINT_AND_STEEL));
                stage = Stage.IGNITE_PLACE;
                applyRotation(igniteAction.rotation());
                return;
            }
        }

        if (webs.getValue() && findHotbarSlot(Items.COBWEB) != -1) {
            List<WebAction> plan = isWithinTargetRange(previewWebPlan)
                    ? new ArrayList<>(previewWebPlan) : findWebPlan(true);
            if (!plan.isEmpty()) {
                oldSlot = minecraft.player.getInventory().getSelectedSlot();
                webPlan.clear();
                webPlan.addAll(plan);
                webAction = webPlan.remove(0);
                minecraft.player.getInventory().setSelectedSlot(findHotbarSlot(Items.COBWEB));
                stage = Stage.WEB_PLACE;
                applyRotation(webAction.rotation());
            }
        }
    }

    @EventTarget
    private void onClick(EventClick event) {
        if (!validEnvironment() || shouldYieldToItemUse() || isKillAuraWorking() || isFireGuardWorking()) {
            reset(true);
            return;
        }

        if (stage == Stage.IGNITE_PLACE && igniteAction != null) {
            InteractionResult result = useItemOn(igniteAction.hit(), igniteAction.rotation());
            boolean placed = result.consumesAction() || isFire(igniteAction.firePos());
            if (!placed) {
                reset(true);
                return;
            }
            swing();
            placedFirePos = igniteAction.firePos();
            igniteTicks = 0;
            igniteAction = null;
            restoreSlot(false);
            stage = Stage.IGNITE_WAIT_BURN;
            return;
        }

        if (stage == Stage.IGNITE_CLEAR && clearAction != null) {
            minecraft.hitResult = clearAction.hit();
            if (minecraft.gameMode.startDestroyBlock(clearAction.firePos(), clearAction.hit().getDirection())) swing();
            reset(true);
            cooldownTicks = delayTicks();
            return;
        }

        if (stage == Stage.WEB_PLACE && webAction != null) {
            WebAction placedAction = webAction;
            InteractionResult result = useItemOn(webAction.hit(), webAction.rotation());
            boolean placed = result.consumesAction()
                    || result == InteractionResult.SUCCESS
                    || minecraft.level.getBlockState(webAction.placePos()).is(Blocks.COBWEB);
            if (!placed) {
                reset(true);
                return;
            }
            swing();
            WebAction next = getNextWebAction(placedAction);
            if (next != null && findHotbarSlot(Items.COBWEB) != -1 && !isKillAuraWorking()) {
                webAction = next;
                applyRotation(next.rotation());
            } else {
                reset(true);
                cooldownTicks = delayTicks();
            }
            return;
        }

        if (stage == Stage.LAVA_PLACE && lavaAction != null) {
            InteractionResult result = useItem(lavaAction.rotation(), lavaAction.hit());
            boolean placed = result.consumesAction()
                    || minecraft.player.getMainHandItem().is(Items.BUCKET)
                    || isLavaSource(lavaAction.placePos());
            if (!placed) {
                reset(true);
                return;
            }
            swing();
            placedLavaPos = lavaAction.placePos();
            lavaTicks = 0;
            lavaAction = null;
            restoreSlot(false);
            stage = Stage.LAVA_WAIT_BURN;
            return;
        }

        if (stage == Stage.LAVA_PICKUP && pickupAction != null) {
            InteractionResult result = useItem(pickupAction.rotation(), pickupAction.hit());
            if (result.consumesAction() || minecraft.player.getMainHandItem().is(Items.LAVA_BUCKET)) swing();
            reset(true);
            cooldownTicks = delayTicks();
        }
    }

    public boolean isActing() {
        return isEnabled() && stage != Stage.IDLE;
    }

    private boolean validEnvironment() {
        return minecraft.player != null
                && minecraft.level != null
                && minecraft.gameMode != null
                && minecraft.screen == null
                && minecraft.player.isAlive();
    }

    private boolean shouldYieldToItemUse() {
        if (minecraft.player == null) return false;
        if (minecraft.player.isUsingItem()) return true;
        if (minecraft.options == null || !minecraft.options.keyUse.isDown()) return false;
        if (isFoodOrDrink(minecraft.player.getMainHandItem())
                || isFoodOrDrink(minecraft.player.getOffhandItem())) return true;
        return oldSlot >= 0 && oldSlot < 9
                && isFoodOrDrink(minecraft.player.getInventory().getItem(oldSlot));
    }

    private boolean isFoodOrDrink(ItemStack stack) {
        ItemUseAnimation animation = stack.getUseAnimation();
        return animation == ItemUseAnimation.EAT || animation == ItemUseAnimation.DRINK;
    }

    private void refreshPreviewActions() {
        previewLavaAction = null;
        previewIgniteAction = null;
        previewWebPlan.clear();
        if (lava.getValue() && findHotbarSlot(Items.LAVA_BUCKET) != -1) {
            previewLavaAction = findLavaAction(false);
        }
        if (flint.getValue() && findHotbarSlot(Items.FLINT_AND_STEEL) != -1) {
            previewIgniteAction = findIgniteAction(false);
        }
        if (webs.getValue() && findHotbarSlot(Items.COBWEB) != -1) {
            previewWebPlan.addAll(findWebPlan(false));
        }
    }

    private boolean shouldWaitLavaBurn() {
        if (lavaTarget == null || lavaTarget.isRemoved() || !lavaTarget.isAlive()) return false;
        if (lavaTarget.isOnFire()) return false;
        return lavaTicks < MAX_BURN_WAIT_TICKS;
    }

    private boolean shouldWaitIgniteBurn() {
        if (igniteTarget == null || igniteTarget.isRemoved() || !igniteTarget.isAlive()) return false;
        if (!isFire(placedFirePos) || igniteTarget.isOnFire()) return false;
        return igniteTicks < MAX_BURN_WAIT_TICKS;
    }

    private List<WebAction> findWebPlan(boolean requireTargetRange) {
        if (minecraft.player == null || minecraft.level == null) return List.of();
        double range = effectiveSearchRange();
        return Core.INSTANCE.getTargetManager().getTargets(range).stream()
                .filter(target -> distanceBetweenBoxes(
                        minecraft.player.getBoundingBox(), target.getBoundingBox()) <= range)
                .filter(target -> !requireTargetRange || isWithinTargetRange(target))
                .map(this::findWebPlan)
                .filter(plan -> !plan.isEmpty())
                .min(Comparator.comparingDouble(plan -> distanceBetweenBoxes(
                        minecraft.player.getBoundingBox(), plan.get(0).target().getBoundingBox())))
                .orElse(List.of());
    }

    private List<WebAction> findWebPlan(LivingEntity target) {
        if (minecraft.player == null || minecraft.level == null || target.isRemoved() || !target.isAlive()) {
            return List.of();
        }
        List<WebAction> plan = new ArrayList<>();
        List<BlockPos> used = new ArrayList<>();
        AABB predicted = predictNextBox(target);

        WebAction head = findBestWebAction(target, getTrapCandidates(predicted, true), used, true);
        if (head != null) {
            plan.add(head);
            used.add(head.placePos());
        }
        if (plan.size() < webCount.getValue().intValue()) {
            WebAction lower = findBestWebAction(target, getTrapCandidates(predicted, false), used, false);
            if (lower != null) {
                plan.add(lower);
                used.add(lower.placePos());
            }
        }
        if (plan.isEmpty()) {
            WebAction any = findBestWebAction(target, getTrapCandidates(predicted, false), used, false);
            if (any != null) plan.add(any);
        }
        return plan;
    }

    private WebAction getNextWebAction(WebAction placedAction) {
        if (!webPlan.isEmpty()) return webPlan.remove(0);
        if (placedAction.head()
                || webCount.getValue().intValue() <= 1
                || placedAction.target().isRemoved()
                || !placedAction.target().isAlive()) return null;

        return findBestWebAction(
                placedAction.target(),
                getTrapCandidates(predictNextBox(placedAction.target()), true),
                List.of(placedAction.placePos()),
                true
        );
    }

    private WebAction findBestWebAction(LivingEntity target, List<BlockPos> candidates,
                                        List<BlockPos> excluded, boolean head) {
        List<WebAction> actions = new ArrayList<>();
        for (BlockPos pos : candidates) {
            if (excluded.contains(pos) || !canPlaceWeb(pos) || intersectsSelfNowOrNextTick(pos)) continue;
            PlaceHit hit = findBlockPlaceHit(pos, 0.0D, getPlaceRange(), List.of());
            if (hit != null) actions.add(new WebAction(target, pos, hit.hit(), hit.rotation(), head));
        }
        Rotation current = currentRotation();
        return actions.stream()
                .min(Comparator
                        .comparingDouble((WebAction action) -> current.distanceTo(action.rotation()))
                        .thenComparingDouble(action -> Vec3.atCenterOf(action.placePos())
                                .distanceToSqr(action.target().getBoundingBox().getCenter())))
                .orElse(null);
    }

    private List<BlockPos> getTrapCandidates(AABB box, boolean head) {
        double minY = head ? box.minY + box.getYsize() * 0.55D : box.minY + 0.02D;
        double maxY = head ? box.maxY - 0.02D : Math.min(box.minY + 0.9D, box.maxY - 0.02D);
        AABB section = new AABB(
                box.minX + 1.0E-4D, minY, box.minZ + 1.0E-4D,
                box.maxX - 1.0E-4D, maxY, box.maxZ - 1.0E-4D);
        List<BlockPos> candidates = new ArrayList<>();
        for (int x = (int) Math.floor(section.minX); x <= (int) Math.floor(section.maxX); x++) {
            for (int y = (int) Math.floor(section.minY); y <= (int) Math.floor(section.maxY); y++) {
                for (int z = (int) Math.floor(section.minZ); z <= (int) Math.floor(section.maxZ); z++) {
                    candidates.add(new BlockPos(x, y, z));
                }
            }
        }
        Vec3 center = section.getCenter();
        candidates.sort(Comparator.comparingDouble(pos -> Vec3.atCenterOf(pos).distanceToSqr(center)));
        return candidates;
    }

    private LavaAction findLavaAction(boolean requireTargetRange) {
        if (minecraft.player == null || minecraft.level == null) return null;
        double range = effectiveSearchRange();
        return Core.INSTANCE.getTargetManager().getTargets(range).stream()
                .filter(target -> !target.isOnFire())
                .filter(target -> distanceBetweenBoxes(
                        minecraft.player.getBoundingBox(), target.getBoundingBox()) <= range)
                .filter(target -> !requireTargetRange || isWithinTargetRange(target))
                .map(this::findLavaAction)
                .filter(action -> action != null)
                .min(Comparator.comparingDouble(action -> distanceBetweenBoxes(
                        minecraft.player.getBoundingBox(), action.target().getBoundingBox())))
                .orElse(null);
    }

    private LavaAction findLavaAction(LivingEntity target) {
        if (minecraft.player == null || minecraft.level == null || target.isOnFire()) return null;
        AABB predicted = predictNextBox(target);
        for (BlockPos placePos : getFootCandidates(predicted)) {
            if (!canPlaceLava(placePos)) continue;
            if (avoidSelf.getValue() && new AABB(placePos).intersects(minecraft.player.getBoundingBox())) continue;
            PlaceHit hit = findBlockPlaceHit(placePos, getMinPlaceRange(), getPlaceRange(), List.of(
                    target.getBoundingBox().inflate(0.03D), predicted.inflate(0.03D)));
            if (hit != null) return new LavaAction(target, placePos, hit.hit(), hit.rotation());
        }
        return null;
    }

    private IgniteAction findIgniteAction(boolean requireTargetRange) {
        if (minecraft.player == null || minecraft.level == null) return null;
        double range = effectiveSearchRange();
        return Core.INSTANCE.getTargetManager().getTargets(range).stream()
                .filter(target -> !target.isOnFire())
                .filter(target -> distanceBetweenBoxes(
                        minecraft.player.getBoundingBox(), target.getBoundingBox()) <= range)
                .filter(target -> !requireTargetRange || isWithinTargetRange(target))
                .map(this::findIgniteAction)
                .filter(action -> action != null)
                .min(Comparator.comparingDouble(action -> distanceBetweenBoxes(
                        minecraft.player.getBoundingBox(), action.target().getBoundingBox())))
                .orElse(null);
    }

    private IgniteAction findIgniteAction(LivingEntity target) {
        if (minecraft.player == null || minecraft.level == null || target.isOnFire()) return null;
        AABB predicted = predictNextBox(target);
        for (BlockPos firePos : getFootCandidates(predicted)) {
            if (!canPlaceFire(firePos)) continue;
            if (avoidSelf.getValue() && new AABB(firePos).intersects(minecraft.player.getBoundingBox())) continue;
            PlaceHit hit = findBlockPlaceHit(firePos, 0.0D, getPlaceRange(), List.of(
                    target.getBoundingBox().inflate(0.03D), predicted.inflate(0.03D)));
            if (hit != null) return new IgniteAction(target, firePos, hit.hit(), hit.rotation());
        }
        return null;
    }

    private AABB predictNextBox(LivingEntity target) {
        List<AABB> boxes = SimulatedPlayer.predictBoxes(target, 1);
        return boxes.size() > 1 ? boxes.get(1) : target.getBoundingBox();
    }

    private List<BlockPos> getFootCandidates(AABB box) {
        List<BlockPos> candidates = new ArrayList<>();
        double epsilon = 1.0E-4D;
        int y = (int) Math.floor(box.minY + 0.03D);
        for (int x = (int) Math.floor(box.minX + epsilon); x <= (int) Math.floor(box.maxX - epsilon); x++) {
            for (int z = (int) Math.floor(box.minZ + epsilon); z <= (int) Math.floor(box.maxZ - epsilon); z++) {
                candidates.add(new BlockPos(x, y, z));
            }
        }
        Vec3 center = box.getCenter();
        candidates.sort(Comparator.comparingDouble(pos -> Vec3.atCenterOf(pos).distanceToSqr(center)));
        return candidates;
    }

    private PlaceHit findBlockPlaceHit(BlockPos placePos, double minRange, double maxRange,
                                       List<AABB> blockedBoxes) {
        if (minecraft.player == null || minecraft.level == null) return null;
        List<PlaceHit> hits = new ArrayList<>();
        for (Direction face : PLACE_DIRECTIONS) {
            BlockPos neighbor = placePos.relative(face.getOpposite());
            if (!canClickBlock(neighbor)) continue;
            for (Vec3 point : sampleFace(neighbor, face)) {
                if (isBlocked(point, blockedBoxes)) continue;
                double distance = minecraft.player.getEyePosition().distanceTo(point);
                if (distance < minRange || distance > maxRange) continue;
                Rotation rotation = RotationMath.toRotation(minecraft.player.getEyePosition(), point).normalized();
                BlockHitResult raycast = rayCastBlock(rotation, maxRange, false);
                if (raycast.getType() != HitResult.Type.BLOCK
                        || !raycast.getBlockPos().equals(neighbor)
                        || raycast.getDirection() != face) continue;
                hits.add(new PlaceHit(new BlockHitResult(point, face, neighbor, false), rotation));
            }
        }
        Rotation current = currentRotation();
        return hits.stream()
                .min(Comparator.comparingDouble(hit -> current.distanceTo(hit.rotation())))
                .orElse(null);
    }

    private PickupAction findLavaPickupAction() {
        if (minecraft.player == null || minecraft.level == null || !isLavaSource(placedLavaPos)) return null;
        List<PickupAction> actions = new ArrayList<>();
        for (Vec3 point : sampleBlock(placedLavaPos)) {
            if (minecraft.player.getEyePosition().distanceTo(point) > MAX_BLOCK_INTERACTION_RANGE) continue;
            Rotation rotation = RotationMath.toRotation(minecraft.player.getEyePosition(), point).normalized();
            BlockHitResult hit = rayCastBlock(rotation, MAX_BLOCK_INTERACTION_RANGE, true);
            if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(placedLavaPos)) {
                actions.add(new PickupAction(hit, rotation));
            }
        }
        Rotation current = currentRotation();
        return actions.stream()
                .min(Comparator.comparingDouble(action -> current.distanceTo(action.rotation())))
                .orElse(null);
    }

    private ClearAction findFireClearAction(BlockPos firePos) {
        if (minecraft.player == null || minecraft.level == null || !isFire(firePos)) return null;
        List<ClearAction> actions = new ArrayList<>();
        for (Vec3 point : sampleBlockShape(firePos)) {
            if (minecraft.player.getEyePosition().distanceTo(point) > getPlaceRange()) continue;
            Rotation rotation = RotationMath.toRotation(minecraft.player.getEyePosition(), point).normalized();
            BlockHitResult hit = rayCastBlock(rotation, getPlaceRange(), false);
            if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(firePos)) {
                actions.add(new ClearAction(firePos, hit, rotation));
            }
        }
        Rotation current = currentRotation();
        return actions.stream()
                .min(Comparator.comparingDouble(action -> current.distanceTo(action.rotation())))
                .orElse(null);
    }

    private List<Vec3> sampleFace(BlockPos pos, Direction face) {
        List<Vec3> points = new ArrayList<>();
        double min = FACE_OFFSETS[0];
        double max = FACE_OFFSETS[FACE_OFFSETS.length - 1];
        switch (face) {
            case UP, DOWN -> {
                double y = face == Direction.UP ? max : min;
                for (double x : FACE_OFFSETS) for (double z : FACE_OFFSETS) points.add(offset(pos, x, y, z));
            }
            case NORTH, SOUTH -> {
                double z = face == Direction.SOUTH ? max : min;
                for (double x : FACE_OFFSETS) for (double y : FACE_OFFSETS) points.add(offset(pos, x, y, z));
            }
            case EAST, WEST -> {
                double x = face == Direction.EAST ? max : min;
                for (double y : FACE_OFFSETS) for (double z : FACE_OFFSETS) points.add(offset(pos, x, y, z));
            }
        }
        Vec3 eyes = minecraft.player == null ? Vec3.ZERO : minecraft.player.getEyePosition();
        points.sort(Comparator.comparingDouble(eyes::distanceToSqr));
        return points;
    }

    private List<Vec3> sampleBlock(BlockPos pos) {
        List<Vec3> points = new ArrayList<>();
        points.add(Vec3.atCenterOf(pos));
        for (Direction face : Direction.values()) points.addAll(sampleFace(pos, face));
        return points;
    }

    private List<Vec3> sampleBlockShape(BlockPos pos) {
        List<Vec3> points = new ArrayList<>();
        if (minecraft.level == null) return points;
        VoxelShape shape = minecraft.level.getBlockState(pos).getShape(minecraft.level, pos);
        if (shape.isEmpty()) points.add(Vec3.atCenterOf(pos));
        else for (AABB localBox : shape.toAabbs()) points.add(localBox.move(pos).getCenter());
        Vec3 eyes = minecraft.player == null ? Vec3.ZERO : minecraft.player.getEyePosition();
        points.sort(Comparator.comparingDouble(eyes::distanceToSqr));
        return points;
    }

    private BlockHitResult rayCastBlock(Rotation rotation, double range, boolean includeFluids) {
        Vec3 start = minecraft.player.getEyePosition();
        Vec3 end = start.add(RotationMath.direction(rotation).scale(range));
        return minecraft.level.clip(new ClipContext(
                start, end, ClipContext.Block.OUTLINE,
                includeFluids ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE,
                minecraft.player));
    }

    private boolean canPlaceLava(BlockPos pos) {
        if (minecraft.level == null) return false;
        BlockState state = minecraft.level.getBlockState(pos);
        return !state.getFluidState().is(Fluids.LAVA) && state.canBeReplaced();
    }

    private boolean canPlaceFire(BlockPos pos) {
        if (minecraft.level == null) return false;
        BlockState state = minecraft.level.getBlockState(pos);
        return state.getFluidState().isEmpty()
                && state.canBeReplaced()
                && Blocks.FIRE.defaultBlockState().canSurvive(minecraft.level, pos);
    }

    private boolean canPlaceWeb(BlockPos pos) {
        if (minecraft.level == null) return false;
        BlockState state = minecraft.level.getBlockState(pos);
        return !state.is(Blocks.COBWEB) && state.getFluidState().isEmpty() && state.canBeReplaced();
    }

    private boolean canClickBlock(BlockPos pos) {
        if (minecraft.level == null) return false;
        BlockState state = minecraft.level.getBlockState(pos);
        return !state.isAir() && !state.getCollisionShape(minecraft.level, pos).isEmpty();
    }

    private boolean intersectsSelfNowOrNextTick(BlockPos pos) {
        if (minecraft.player == null) return false;
        AABB box = new AABB(pos);
        return box.intersects(minecraft.player.getBoundingBox())
                || box.intersects(predictNextBox(minecraft.player));
    }

    private boolean isLavaSource(BlockPos pos) {
        return pos != null
                && minecraft.level != null
                && minecraft.level.getFluidState(pos).is(Fluids.LAVA)
                && minecraft.level.getFluidState(pos).isSource();
    }

    private boolean isFire(BlockPos pos) {
        return pos != null
                && minecraft.level != null
                && (minecraft.level.getBlockState(pos).is(Blocks.FIRE)
                || minecraft.level.getBlockState(pos).is(Blocks.SOUL_FIRE));
    }

    private boolean isBlocked(Vec3 point, List<AABB> boxes) {
        for (AABB box : boxes) if (box.contains(point)) return true;
        return false;
    }

    private Vec3 offset(BlockPos pos, double x, double y, double z) {
        return new Vec3(pos.getX() + x, pos.getY() + y, pos.getZ() + z);
    }

    private InteractionResult useItemOn(BlockHitResult hit, Rotation rotation) {
        minecraft.hitResult = hit;
        float yaw = minecraft.player.getYRot();
        float pitch = minecraft.player.getXRot();
        try {
            minecraft.player.setYRot(rotation.yaw());
            minecraft.player.setXRot(rotation.pitch());
            return minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit);
        } finally {
            minecraft.player.setYRot(yaw);
            minecraft.player.setXRot(pitch);
        }
    }

    private InteractionResult useItem(Rotation rotation, BlockHitResult hit) {
        minecraft.hitResult = hit;
        float yaw = minecraft.player.getYRot();
        float pitch = minecraft.player.getXRot();
        try {
            minecraft.player.setYRot(rotation.yaw());
            minecraft.player.setXRot(rotation.pitch());
            return minecraft.gameMode.useItem(minecraft.player, InteractionHand.MAIN_HAND);
        } finally {
            minecraft.player.setYRot(yaw);
            minecraft.player.setXRot(pitch);
        }
    }

    private void applyRotation(Rotation rotation) {
        applyRotation(rotation, RotationPriority.MEDIUM);
    }

    private void applyRotation(Rotation rotation, RotationPriority priority) {
        Core.INSTANCE.getRotationManager().submit(this, new RotationRequest(
                rotation,
                priority,
                selectedMovementFix(),
                false,
                SmoothMode.ADVANCED,
                rotationSpeed.getValue().floatValue(),
                0,
                2
        ));
    }

    private MovementFix selectedMovementFix() {
        return switch (movementFix.getValue()) {
            case "Strict" -> MovementFix.STRICT;
            case "Silent" -> MovementFix.SILENT;
            default -> MovementFix.NONE;
        };
    }

    private void swing() {
        if (swingMode.is("Normal")) minecraft.player.swing(InteractionHand.MAIN_HAND);
        else if (swingMode.is("Silent") && minecraft.getConnection() != null) {
            minecraft.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
        }
    }

    private int findHotbarSlot(Item item) {
        if (minecraft.player == null) return -1;
        for (int slot = 0; slot < 9; slot++) {
            if (minecraft.player.getInventory().getItem(slot).is(item)) return slot;
        }
        return -1;
    }

    private Rotation currentRotation() {
        if (Core.INSTANCE.isInitialized()) return Core.INSTANCE.getRotationManager().getEffectiveRotation();
        if (minecraft.player == null) return new Rotation(0.0F, 0.0F);
        return new Rotation(minecraft.player.getYRot(), minecraft.player.getXRot());
    }

    private void restoreSlot(boolean clear) {
        if (oldSlot != -1 && minecraft.player != null) {
            minecraft.player.getInventory().setSelectedSlot(oldSlot);
        }
        if (clear) oldSlot = -1;
    }

    private void reset(boolean restoreSlot) {
        if (restoreSlot) restoreSlot(true);
        else oldSlot = -1;
        stage = Stage.IDLE;
        lavaAction = null;
        pickupAction = null;
        igniteAction = null;
        clearAction = null;
        lavaTarget = null;
        igniteTarget = null;
        placedLavaPos = null;
        placedFirePos = null;
        webAction = null;
        webPlan.clear();
        previewLavaAction = null;
        previewIgniteAction = null;
        previewWebPlan.clear();
        lavaTicks = 0;
        igniteTicks = 0;
        if (Core.INSTANCE.isInitialized()) Core.INSTANCE.getRotationManager().cancel(this);
    }

    private boolean isWithinTargetRange(LavaAction action) {
        return action != null && isWithinTargetRange(action.target());
    }

    private boolean isWithinTargetRange(IgniteAction action) {
        return action != null && isWithinTargetRange(action.target());
    }

    private boolean isWithinTargetRange(List<WebAction> plan) {
        return plan != null && !plan.isEmpty() && isWithinTargetRange(plan.get(0).target());
    }

    private boolean isWithinTargetRange(LivingEntity target) {
        return minecraft.player != null
                && target != null
                && target.isAlive()
                && !target.isRemoved()
                && distanceBetweenBoxes(minecraft.player.getBoundingBox(), target.getBoundingBox())
                <= targetRange.getValue().doubleValue();
    }

    private double distanceBetweenBoxes(AABB first, AABB second) {
        double x = Math.max(0.0D, Math.max(first.minX - second.maxX, second.minX - first.maxX));
        double y = Math.max(0.0D, Math.max(first.minY - second.maxY, second.minY - first.maxY));
        double z = Math.max(0.0D, Math.max(first.minZ - second.maxZ, second.minZ - first.maxZ));
        return Math.sqrt(x * x + y * y + z * z);
    }

    private double getMinPlaceRange() {
        return Math.min(minPlaceRange.getValue().doubleValue(), getPlaceRange());
    }

    private double getPlaceRange() {
        double configured = placeRange.getValue().doubleValue();
        double vanilla = minecraft.player == null
                ? MAX_BLOCK_INTERACTION_RANGE : minecraft.player.blockInteractionRange();
        return Math.min(configured, Math.min(vanilla, MAX_BLOCK_INTERACTION_RANGE));
    }

    private double effectiveSearchRange() {
        return Math.max(searchRange.getValue().doubleValue(), targetRange.getValue().doubleValue());
    }

    private int delayTicks() {
        return delay.getValue().intValue();
    }

    private boolean isKillAuraWorking() {
        KillAura killAura = Core.INSTANCE.getModuleManager().getModule(KillAura.class);
        return killAura != null && killAura.isEnabled() && killAura.getAttackTarget() != null;
    }

    private boolean isFireGuardWorking() {
        FireGuard fireGuard = Core.INSTANCE.getModuleManager().getModule(FireGuard.class);
        return fireGuard != null && fireGuard.isActing();
    }

    private enum Stage {
        IDLE,
        WEB_PLACE,
        LAVA_PLACE,
        LAVA_WAIT_BURN,
        LAVA_PICKUP,
        IGNITE_PLACE,
        IGNITE_WAIT_BURN,
        IGNITE_CLEAR
    }

    private record WebAction(
            LivingEntity target, BlockPos placePos, BlockHitResult hit, Rotation rotation, boolean head) {
    }

    private record LavaAction(LivingEntity target, BlockPos placePos, BlockHitResult hit, Rotation rotation) {
    }

    private record IgniteAction(LivingEntity target, BlockPos firePos, BlockHitResult hit, Rotation rotation) {
    }

    private record ClearAction(BlockPos firePos, BlockHitResult hit, Rotation rotation) {
    }

    private record PickupAction(BlockHitResult hit, Rotation rotation) {
    }

    private record PlaceHit(BlockHitResult hit, Rotation rotation) {
    }
}
