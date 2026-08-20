package cc.nimbus.features.module.modules.movement;

import cc.nimbus.core.Core;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.game.EventClick;
import cc.nimbus.event.impl.game.EventTick;
import cc.nimbus.event.impl.game.EventWorldLoad;
import cc.nimbus.event.impl.player.EventMoveInput;
import cc.nimbus.event.impl.player.EventTravel;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.BooleanSetting;
import cc.nimbus.features.module.api.setting.impl.submodule.ModeValue;
import cc.nimbus.features.module.api.setting.impl.NumberSetting;
import cc.nimbus.features.module.modules.movement.scaffold.FallingPlayer;
import cc.nimbus.features.module.modules.movement.scaffold.ScaffoldPlaceInfo;
import cc.nimbus.features.module.modules.movement.scaffold.ScaffoldPlaceSearch;
import cc.nimbus.managers.GameStateManager;
import cc.nimbus.managers.rotation.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.ConcretePowderBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.FungusBlock;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TintedGlassBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.Set;

@ModuleInfo(
        name = "Scaffold",
        description = "Automatically places blocks beneath you while moving.",
        category = ModuleCategory.Move
)
public final class Scaffold extends BasicModule {
    private static final Set<Block> BLACKLISTED_BLOCKS = Set.of(Blocks.TNT);

    public final ModeValue searchMode = new ModeValue(
            "Search Mode", new String[]{"Normal", "Hypixel"}, "Normal");
    public final ModeValue mode = new ModeValue(
            "Mode", new String[]{"Normal", "Telly"}, "Normal");
    public final ModeValue rotatePoint = new ModeValue(
            "Rotate Point", new String[]{"Normal", "Corner", "Nearest"}, "Normal");
    public final ModeValue rotationMode = new ModeValue(
            "Rotation Mode", new String[]{"Instant", "Smooth", "Hypixel"}, "Instant");
    public final NumberSetting rotateSpeed = new NumberSetting(
            "Rotate Speed", 180.0, 1.0, 180.0, 1.0, () -> rotationMode.is("Smooth"));
    public final BooleanSetting silentRotate = new BooleanSetting("Silent Rotate", false);
    public final BooleanSetting autoSwitch = new BooleanSetting("Auto Switch", true);
    public final BooleanSetting autoSneak = new BooleanSetting("Auto Sneak", false);
    public final BooleanSetting keepY = new BooleanSetting("Keep Y", false);
    public final NumberSetting jumpDelayMax = new NumberSetting(
            "Max Jump Delay", 2.0, 0.0, 3.0, 1.0, () -> mode.is("Telly"));
    public final NumberSetting jumpDelayMin = new NumberSetting(
            "Min Jump Delay", 1.0, 0.0, 3.0, 1.0, () -> mode.is("Telly"));
    public final ModeValue raycast = new ModeValue(
            "Raycast", new String[]{"Normal", "Medium", "Off"}, "Normal");
    public final NumberSetting placeDelayMin = new NumberSetting(
            "Min Place Delay", 0.0, 0.0, 200.0, 1.0);
    public final NumberSetting placeDelayMax = new NumberSetting(
            "Max Place Delay", 0.0, 0.0, 200.0, 1.0);
    public final BooleanSetting autoStuck = new BooleanSetting("Auto Stuck", true);
    public final BooleanSetting slow = new BooleanSetting(
            "Slow Hypixel Rotation", false, () -> rotationMode.is("Hypixel"));
    public final ModeValue placeDelayMode = new ModeValue(
            "Placeable Mode", new String[]{"Check Height", "Check Tick", "Off"}, "Check Height",
            () -> mode.is("Telly") && !rotationMode.is("Hypixel"));
    public final NumberSetting height = new NumberSetting(
            "Height", 0.5, 0.0, 1.25, 0.05,
            () -> mode.is("Telly") && placeDelayMode.is("Check Height") && !rotationMode.is("Hypixel"));
    public final NumberSetting upTellyHeight = new NumberSetting(
            "Up Telly Height", 0.2, 0.0, 1.25, 0.05,
            () -> mode.is("Telly") && placeDelayMode.is("Check Height")
                    && !rotationMode.is("Hypixel") && !keepY.getValue());
    public final NumberSetting tick = new NumberSetting(
            "Tick", 3.0, 0.0, 5.0, 1.0,
            () -> mode.is("Telly") && placeDelayMode.is("Check Tick") && !rotationMode.is("Hypixel"));
    public final NumberSetting upTellyTick = new NumberSetting(
            "Up Telly Tick", 1.0, 0.0, 5.0, 1.0,
            () -> mode.is("Telly") && placeDelayMode.is("Check Tick")
                    && !rotationMode.is("Hypixel") && !keepY.getValue());

    private ScaffoldPlaceInfo currentPlace;
    private ScaffoldPlaceInfo lastPlace;
    private ScaffoldPlaceInfo.RotationHit currentHit;
    private Rotation targetRotation;
    private Rotation lastHypixelRotation;
    private int rotationTick;
    private int stage;
    private int baseY;
    private int yLevel;
    private int rotateCount;
    private int oldSlot = -1;
    private boolean canRotate;
    private boolean canPlaceNow;
    private boolean reachable = true;
    private boolean yawNormal;
    private boolean forcedSneak;
    private int placementAttemptTick = Integer.MIN_VALUE;
    private long lastPlaceTime;

    @Override
    protected void onEnable() {
        resetRuntime();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            oldSlot = minecraft.player.getInventory().getSelectedSlot();
            baseY = yLevel = Mth.floor(minecraft.player.getY() - 1.0);
            stopGoldenAppleUse(minecraft);
        }
    }

    @Override
    protected void onDisable() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && oldSlot >= 0 && oldSlot < 9) {
            minecraft.player.getInventory().setSelectedSlot(oldSlot);
        }
        releaseSneak();
        resetRuntime();
    }

    @EventTarget
    private void onWorldLoad(EventWorldLoad event) {
        releaseSneak();
        resetRuntime();
    }

    @EventTarget
    private void onTick(EventTick event) {
        if (event.getPhase() != EventTick.Phase.PRE) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.gameMode == null) {
            releaseSneak();
            return;
        }
        if (oldSlot < 0) {
            oldSlot = minecraft.player.getInventory().getSelectedSlot();
            baseY = yLevel = Mth.floor(minecraft.player.getY() - 1.0);
        }

        stopGoldenAppleUse(minecraft);
        setSuffix(mode.getValue());
        float normalized = (Mth.wrapDegrees(mc.player.getYRot() - movementYawOffset()) % 360.0f + 360.0f) % 360.0f;
        yawNormal = normalized % 90.0f < 45.0f;
        updateSneak(minecraft.player, minecraft.level);
        selectBlock(minecraft.player);
        preparePlacement(minecraft.player, minecraft.level);
    }

    @EventTarget
    private void onClick(EventClick event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.gameMode == null
                || currentPlace == null || currentHit == null || currentHit.hitVec() == null || !canPlaceNow) {
            return;
        }
        if (!isValidStack(minecraft.player.getMainHandItem())) return;
        if (!ScaffoldPlaceSearch.canPlaceAt(BlockPos.containing(
                minecraft.player.getX(), yLevel, minecraft.player.getZ()))) return;
        if (!placeDelayElapsed(minecraft.player)) return;

        Rotation rayRotation = Core.INSTANCE.getRotationManager().getEffectiveRotation();
        if (!raycast.is("Off")) {
            Rotation checkRotation = raycast.is("Medium") && targetRotation != null ? targetRotation : rayRotation;
            BlockPos hitBlock = RotationRaycast.rayCastBlock(checkRotation, 4.5);
            if (!currentPlace.blockPos().equals(hitBlock)
                    && minecraft.level.noCollision(minecraft.player,
                    minecraft.player.getBoundingBox().inflate(0.1, 0.0, 0.1))) {
                return;
            }
        } else if (rotationMode.is("Hypixel") && rotationTick >= 1) {
            return;
        }

        if (mode.is("Telly") && GameStateManager.offGroundTicks <= 3
                && (!minecraft.player.onGround() || GameStateManager.onGroundTicks >= jumpDelayMin.getValue().intValue())) {
            return;
        }

        BlockHitResult hit = new BlockHitResult(
                currentHit.hitVec(), currentPlace.direction(), currentPlace.blockPos(), false);
        placementAttemptTick = minecraft.player.tickCount;
        if (minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit).consumesAction()) {
            minecraft.player.swing(InteractionHand.MAIN_HAND);
            lastPlaceTime = System.currentTimeMillis();
        }
    }

    @EventTarget
    private void onMoveInput(EventMoveInput event) {
        if (!mode.is("Telly") || event.getForward() == 0.0f && event.getStrafe() == 0.0f) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return;

        int min = Math.min(jumpDelayMin.getValue().intValue(), jumpDelayMax.getValue().intValue());
        int max = Math.max(jumpDelayMin.getValue().intValue(), jumpDelayMax.getValue().intValue());
        int jumpDelay = min == max ? min : min + minecraft.player.getRandom().nextInt(max - min + 1);
        event.setJump(jumpDelay == 0 || GameStateManager.onGroundTicks >= jumpDelay);
    }

    @EventTarget
    private void onTravel(EventTravel event) {
        if (reachable || rotateCount <= 0) return;
        event.cancelEvent();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.player.getAbilities().mayfly && minecraft.options.keyJump.isDown()) {
            Vec3 velocity = minecraft.player.getDeltaMovement();
            minecraft.player.setDeltaMovement(velocity.x, 0.42, velocity.z);
        }
    }

    private void preparePlacement(Player player, Level level) {
        if (mode.is("Telly") && rotationMode.is("Hypixel")) {
            if (rotationTick > 0) rotationTick--;
            if (player.onGround()) {
                if (stage > 0) stage--;
                else if (stage < 0) stage++;
                if (stage == 0 && !keepY.getValue() && !Minecraft.getInstance().options.keyJump.isDown()) stage = 1;
                canRotate = false;
            }
        }

        yLevel = getPlaceY(player);
        if (shouldDelayTellyPlacement(player)) {
            clearPlacement();
            return;
        }

        ScaffoldPlaceSearch.SearchMode search = searchMode.is("Hypixel")
                ? ScaffoldPlaceSearch.SearchMode.HYPIXEL : ScaffoldPlaceSearch.SearchMode.NORMAL;
        currentPlace = ScaffoldPlaceSearch.find(
                BlockPos.containing(player.getX(), yLevel, player.getZ()), search);
        if (mode.is("Normal") && currentPlace == null) currentPlace = lastPlace;
        if (currentPlace == null) {
            clearPlacement();
            return;
        }

        lastPlace = currentPlace;
        currentHit = currentPlace.rotationAndHit(selectedRotatePoint(), referenceRotation(player));
        if (currentHit == null) {
            clearPlacement();
            return;
        }

        Rotation applied = currentHit.rotation();
        canPlaceNow = true;
        if (rotationMode.is("Hypixel")) {
            applied = applyHypixelRotation(player, level, applied);
            canPlaceNow = !raycast.is("Off") || rotationTick < 1;
        }

        if (canPlaceNow && autoStuck.getValue() && currentPlace.blockPos().getY() + 1 < player.getY()) {
            Rotation faceRotation = RotationMath.toRotation(player.getEyePosition(), currentPlace.faceCenter());
            if ((int) player.getEyePosition().distanceTo(Vec3.atCenterOf(currentPlace.blockPos())) < 7
                    && horizontalSpeed(player) > 0.4 && player.tickCount % 20 <= 10) {
                reachable = false;
                applied = faceRotation;
                if (rotateCount == 0) rotateCount = 10;
                rotateCount--;
            } else {
                if (player.getDeltaMovement().y < -0.05
                        && currentPlace.blockPos().getY() > new FallingPlayer(player).calculateY(2)) {
                    reachable = false;
                    applied = faceRotation;
                }
                if (player.fallDistance <= 1.9f) reachable = true;
                if (!reachable) {
                    if (rotateCount == 0) rotateCount = 19;
                    rotateCount--;
                }
            }
        } else if (!autoStuck.getValue()) {
            reachable = true;
            rotateCount = 0;
        }

        targetRotation = applied.normalized();
        submitRotation(player, targetRotation);
    }

    private Rotation applyHypixelRotation(Player player, Level level, Rotation rotation) {
        if (lastHypixelRotation == null) lastHypixelRotation = referenceRotation(player);
        Rotation applied = rotation;

        if (canRotate && (player.getDeltaMovement().y > 0.0 || player.getY() > yLevel + 1.0)) {
            float deltaYaw = Mth.wrapDegrees(applied.yaw() - lastHypixelRotation.yaw());
            int speed = slow.getValue() ? (GameStateManager.offGroundTicks <= 1 ? 60 : 30) : (GameStateManager.offGroundTicks <= 1 ? 130 : 90);
            if (!currentPlace.blockPos().equals(Objects.requireNonNull(RotationRaycast.rayCastBlock(lastHypixelRotation, 4.5)))
                    && Math.abs(deltaYaw) > speed) {
                applied = new Rotation(updateRotation(lastHypixelRotation.yaw(),
                        lastHypixelRotation.yaw() + deltaYaw, speed), applied.pitch());
                rotationTick = 0;
            }
        }

        boolean side = Mth.wrapDegrees(lastHypixelRotation.yaw() - player.getYRot() - movementYawOffset()) > 0.0f;
        Minecraft minecraft = Minecraft.getInstance();
        boolean movingKeys = minecraft.options.keyUp.isDown() != minecraft.options.keyDown.isDown()
                || minecraft.options.keyLeft.isDown() != minecraft.options.keyRight.isDown();
        if (player.onGround() && movingKeys && level.getBlockState(player.blockPosition().above()).isAir()
                && (stage > 0 || !keepY.getValue() && minecraft.options.keyJump.isDown())
                && GameStateManager.onGroundTicks >= jumpDelayMin.getValue().intValue()) {
            float yaw = player.getYRot() - movementYawOffset()
                    + (isDiagonal(player) ? (yawNormal ? 157.0f : -157.0f) : (side ? 157.0f : -157.0f));
            applied = new Rotation(yaw, 80.0f);
            rotationTick = 0;
            canRotate = true;
        }

        lastHypixelRotation = applied;
        return applied;
    }

    private void submitRotation(Player player, Rotation rotation) {
        boolean smooth = rotationMode.is("Smooth");
        Core.INSTANCE.getRotationManager().setRotations(
                rotation,
                silentRotate.getValue() ? MovementFix.SILENT : MovementFix.NONE,
                smooth,
                smooth ? SmoothMode.ADVANCED : SmoothMode.LINEAR,
                rotateSpeed.getValue().floatValue(),
                0,
                0,
                Priority.MEDIUM
        );

        if (!silentRotate.getValue()) {
            player.setYRot(rotation.yaw());
            player.setXRot(rotation.pitch());
            player.setYHeadRot(rotation.yaw());
            player.setYBodyRot(rotation.yaw());
        }
    }

    private boolean shouldDelayTellyPlacement(Player player) {
        if (!mode.is("Telly") || rotationMode.is("Hypixel")) return false;
        Minecraft minecraft = Minecraft.getInstance();
        if (placeDelayMode.is("Check Height")) {
            double threshold = 1.0 + (minecraft.options.keyJump.isDown() && !keepY.getValue()
                    ? upTellyHeight.getValue().doubleValue() : height.getValue().doubleValue());
            return player.fallDistance == 0.0f && player.getY() < yLevel + threshold && player.hurtTime == 0;
        }
        if (placeDelayMode.is("Check Tick")) {
            int required = minecraft.options.keyJump.isDown() && !keepY.getValue()
                    ? upTellyTick.getValue().intValue() : tick.getValue().intValue();
            return GameStateManager.offGroundTicks < required;
        }
        return false;
    }

    private void selectBlock(Player player) {
        if (!autoSwitch.getValue()) return;
        for (int slot = 0; slot < 9; slot++) {
            if (isValidStack(player.getInventory().getItem(slot))) {
                if (player.getInventory().getSelectedSlot() != slot) player.getInventory().setSelectedSlot(slot);
                return;
            }
        }
    }

    private void updateSneak(Player player, Level level) {
        if (!autoSneak.getValue()) {
            releaseSneak();
            return;
        }
        boolean shouldSneak = player.onGround() && level.getBlockState(player.blockPosition().below()).isAir();
        Minecraft.getInstance().options.keyShift.setDown(shouldSneak);
        forcedSneak = shouldSneak;
    }

    private void releaseSneak() {
        if (forcedSneak && Minecraft.getInstance().options != null) {
            Minecraft.getInstance().options.keyShift.setDown(false);
        }
        forcedSneak = false;
    }

    private int getPlaceY(Player player) {
        if (Minecraft.getInstance().options.keyJump.isDown() && !keepY.getValue()) {
            return baseY = Mth.floor(player.getY() - 1.0);
        }
        if (baseY > player.getY() - 1.0 || player.onGround()) baseY = Mth.floor(player.getY() - 1.0);
        return baseY;
    }

    private boolean placeDelayElapsed(Player player) {
        int min = Math.min(placeDelayMin.getValue().intValue(), placeDelayMax.getValue().intValue());
        int max = Math.max(placeDelayMin.getValue().intValue(), placeDelayMax.getValue().intValue());
        int delay = min == max ? min : min + player.getRandom().nextInt(max - min + 1);
        return delay == 0 || System.currentTimeMillis() - lastPlaceTime >= delay;
    }

    private Rotation referenceRotation(Player player) {
        Rotation server = Core.INSTANCE.getRotationManager().getServerRotation();
        return server == null ? new Rotation(player.getYRot(), player.getXRot()) : server;
    }

    private ScaffoldPlaceInfo.RotatePoint selectedRotatePoint() {
        return switch (rotatePoint.getValue()) {
            case "Corner" -> ScaffoldPlaceInfo.RotatePoint.CORNER;
            case "Nearest" -> ScaffoldPlaceInfo.RotatePoint.NEAREST;
            default -> ScaffoldPlaceInfo.RotatePoint.NORMAL;
        };
    }

    private static boolean isDiagonal(Player player) {
        return Math.abs(player.getDeltaMovement().x) > 0.07 && Math.abs(player.getDeltaMovement().z) > 0.07;
    }

    private static double horizontalSpeed(Player player) {
        Vec3 velocity = player.getDeltaMovement();
        return Math.hypot(velocity.x, velocity.z);
    }

    private static float updateRotation(float current, float target, float maxDelta) {
        return current + Mth.clamp(Mth.wrapDegrees(target - current), -maxDelta, maxDelta);
    }

    private static float movementYawOffset() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null) return 180.0f;
        boolean forward = minecraft.options.keyUp.isDown();
        boolean backward = minecraft.options.keyDown.isDown();
        boolean left = minecraft.options.keyLeft.isDown();
        boolean right = minecraft.options.keyRight.isDown();
        boolean bothSide = left && right;
        float yaw = 0.0f;

        if (forward) {
            yaw -= 180.0f;
            if (!bothSide) {
                if (left) yaw += 45.0f;
                if (right) yaw -= 45.0f;
            }
        } else if (!backward) {
            yaw -= 180.0f;
            if (!bothSide) {
                if (left) yaw += 90.0f;
                if (right) yaw -= 90.0f;
            }
        } else if (!bothSide) {
            if (left) yaw -= 45.0f;
            if (right) yaw += 45.0f;
        }
        return yaw;
    }

    public static boolean isValidStack(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getCount() <= 0
                || !(stack.getItem() instanceof BlockItem blockItem)
                || stack.getItem() instanceof StandingAndWallBlockItem) {
            return false;
        }
        String name = stack.getHoverName().getString();
        if (name.contains("Click") || name.contains("\u70b9\u51fb")) return false;

        Block block = blockItem.getBlock();
        if (BLACKLISTED_BLOCKS.contains(block)
                || block instanceof AirBlock
                || block instanceof StairBlock
                || block instanceof SlabBlock
                || block instanceof FenceBlock
                || block instanceof FenceGateBlock
                || block instanceof DoorBlock
                || block instanceof TrapDoorBlock
                || block instanceof PressurePlateBlock
                || block instanceof ButtonBlock
                || block instanceof WallBlock
                || block instanceof CarpetBlock
                || block instanceof ConcretePowderBlock
                || block instanceof TintedGlassBlock
                || block instanceof StainedGlassBlock
                || block instanceof StainedGlassPaneBlock
                || block instanceof BannerBlock
                || block instanceof LeavesBlock
                || block instanceof SaplingBlock
                || block instanceof MushroomBlock
                || block instanceof FlowerBlock
                || block instanceof BushBlock
                || block instanceof FungusBlock
                || block instanceof CropBlock
                || block instanceof ComposterBlock
                || block instanceof NoteBlock
                || block instanceof JukeboxBlock
                || block instanceof SignBlock) {
            return false;
        }
        return block != Blocks.IRON_BARS
                && block != Blocks.IRON_CHAIN
                && block != Blocks.GLASS_PANE
                && block != Blocks.DIRT_PATH
                && block != Blocks.FARMLAND
                && block != Blocks.SNOW
                && block != Blocks.AMETHYST_CLUSTER
                && block != Blocks.MANGROVE_ROOTS
                && block != Blocks.SUGAR_CANE
                && block != Blocks.CACTUS
                && block != Blocks.LILY_PAD;
    }

    public boolean attemptedPlacementThisTick() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && placementAttemptTick == minecraft.player.tickCount;
    }

    private static void stopGoldenAppleUse(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.gameMode == null || !minecraft.player.isUsingItem()) return;
        ItemStack stack = minecraft.player.getUseItem();
        if (stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE)) {
            minecraft.gameMode.releaseUsingItem(minecraft.player);
        }
    }

    private void clearPlacement() {
        currentPlace = null;
        currentHit = null;
        targetRotation = null;
        canPlaceNow = false;
    }

    private void resetRuntime() {
        currentPlace = null;
        lastPlace = null;
        currentHit = null;
        targetRotation = null;
        lastHypixelRotation = null;
        rotationTick = 3;
        stage = 0;
        baseY = 0;
        yLevel = 0;
        rotateCount = 0;
        oldSlot = -1;
        canRotate = false;
        canPlaceNow = false;
        reachable = true;
        yawNormal = false;
        forcedSneak = false;
        placementAttemptTick = Integer.MIN_VALUE;
        lastPlaceTime = 0L;
    }
}
