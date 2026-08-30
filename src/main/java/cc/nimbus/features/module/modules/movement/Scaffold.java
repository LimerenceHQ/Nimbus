package cc.nimbus.features.module.modules.movement;

import cc.nimbus.core.Core;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.game.EventClick;
import cc.nimbus.event.impl.game.EventTick;
import cc.nimbus.event.impl.game.EventWorldLoad;
import cc.nimbus.event.impl.player.EventMoveInput;
import cc.nimbus.event.impl.player.EventTravel;
import cc.nimbus.event.impl.player.EventUpdate;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.BooleanSetting;
import cc.nimbus.features.module.api.setting.impl.ModeSetting;
import cc.nimbus.features.module.api.setting.impl.NumberSetting;
import cc.nimbus.features.module.modules.movement.scaffold.FallingPlayer;
import cc.nimbus.features.module.modules.movement.scaffold.PlaceInfo;
import cc.nimbus.features.module.modules.movement.scaffold.PlaceUtils;
import cc.nimbus.features.rotation.*;
import cc.nimbus.managers.GameStateManager;
import cc.nimbus.utils.client.MessageUtils;
import cc.nimbus.utils.client.TimerUtils;
import cc.nimbus.utils.entity.player.InventoryUtils;
import cc.nimbus.utils.entity.player.MovementUtils;
import cc.nimbus.utils.entity.rotation.RotationUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.FungusBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import java.util.HashSet;
import java.util.Set;

@ModuleInfo(name = "Scaffold", category = ModuleCategory.Move, description = "Automatically places blocks beneath you while moving")
public class Scaffold extends BasicModule {
    public static final Set<Block> blacklistedBlocks = new HashSet<>();
    private final ModeSetting searchMode = new ModeSetting("Search Mode", new String[]{"Normal", "Hypixel"}, "Normal");
    private final ModeSetting mode = new ModeSetting("Mode", new String[]{"Normal", "Telly"}, "Normal");
    private final ModeSetting rotatePoint = new ModeSetting("Rotate Point", new String[]{"Normal", "Corner", "Nearest"}, "Normal");
    private final ModeSetting rotationMode = new ModeSetting("Rotation Mode", new String[]{"Instant", "Smooth", "Hypixel"}, "Instant");
    private final NumberSetting rotateSpeed = new NumberSetting("Rotate Speed", 180.0, 1.0, 180.0, 1.0,() -> rotationMode.is("Smooth"));
    private final BooleanSetting silentRotate = new BooleanSetting("Silent rotate", false);
    private final BooleanSetting autoSwitch = new BooleanSetting("Auto Switch", true);
    private final BooleanSetting itemSpoof = new BooleanSetting("Item Spoof", true);
    private final BooleanSetting autoSneak = new BooleanSetting("Auto Sneak", false);
    private final BooleanSetting keepY = new BooleanSetting("Keep Y", false);
    private final NumberSetting jumpDelayMax = new NumberSetting("Max Jump Delay", 2.0, 0.0, 3.0, 1.0,() -> mode.is("Telly"));
    private final NumberSetting jumpDelayMin = new NumberSetting("Min Jump Delay", 1.0, 0.0, 3.0, 1.0,() -> mode.is("Telly"));
    private final ModeSetting raycast = new ModeSetting("Raycast", new String[]{"Normal", "Medium", "Off"}, "Normal");
    private final NumberSetting placeDelayMin = new NumberSetting("Min Place Delay", 0.0, 0.0, 200.0, 1.0);
    private final NumberSetting placeDelayMax = new NumberSetting("Max Place Delay", 0.0, 0.0, 200.0, 1.0);
    private final BooleanSetting autoStuck = new BooleanSetting("AutoStuck", true);
    private final BooleanSetting noFoV = new BooleanSetting("NoFoV", true);
    private final BooleanSetting slow = new BooleanSetting("Slow", false);
    private final ModeSetting placeDelayMode = new ModeSetting("Placeable Mode", new String[]{"Check Height", "Check Tick", "Off"}, "Check Height",() -> mode.is("Telly") && !rotationMode.is("Hypixel"));
    private final NumberSetting height = new NumberSetting("Height", 0.5, 0.0, 1.25, 0.05,() -> mode.is("Telly") && placeDelayMode.is("Check Height") && !rotationMode.is("Hypixel"));
    private final NumberSetting upTellyHeight = new NumberSetting("UpTelly Height", 0.2, 0.0, 1.25, 0.05,() -> mode.is("Telly") && placeDelayMode.is("Check Height") && !rotationMode.is("Hypixel") && !keepY.getValue());
    private final NumberSetting tick = new NumberSetting("Tick", 3.0, 0.0, 5.0, 1.0,() -> mode.is("Telly") && placeDelayMode.is("Check Tick") && !rotationMode.is("Hypixel"));
    private final NumberSetting upTellyTick = new NumberSetting("UpTelly Tick", 1.0, 0.0, 5.0, 1.0,() -> mode.is("Telly") && placeDelayMode.is("Check Tick") && !rotationMode.is("Hypixel") && !keepY.getValue());

    private PlaceInfo currentPlace;
    private PlaceInfo lastPlaceInfo;
    private Rotation targetRotation;
    private Rotation lastHypixelRotation;
    private PlaceInfo.RotationHit currentHit;
    private int rotationTick = 0;
    private int stage = 0;
    private boolean canRotate = false;
    private int baseY = 0;
    private int rotateCount = 0;
    private int counter = 0;
    private int oldSlot;
    private boolean itemSpoofing;
    private int yLevel;
    private boolean reachable = true;
    private boolean noti = false;
    private boolean yawNormal;
    public TimerUtils timerUtils = new TimerUtils();

    private final TimerUtils placeTimer = new TimerUtils();

    static {
        blacklistedBlocks.add(Blocks.TNT);
    }

    @Override
    public void onEnable() {
        currentPlace = null;
        lastPlaceInfo = null;
        currentHit = null;
        targetRotation = null;
        lastHypixelRotation = null;
        rotationTick = 3;
        stage = 0;
        canRotate = false;
        reachable = true;
        rotateCount = 0;
        counter = 0;
        itemSpoofing = false;
        placeTimer.reset();
        if (mc.player != null) {
            oldSlot = mc.player.getInventory().selected;
            baseY = yLevel = (int) Math.floor(mc.player.getY() - 1.0D);
        }
    }

    @Override
    public void onDisable() {
        currentPlace = null;
        lastPlaceInfo = null;
        currentHit = null;
        targetRotation = null;
        lastHypixelRotation = null;
        rotationTick = 0;
        stage = 0;
        canRotate = false;
        reachable = true;
        rotateCount = 0;
        counter = 0;
        itemSpoofing = false;
        if (mc.player != null) {
            mc.player.getInventory().selected = (oldSlot);
        }
        mc.options.keyShift.setDown(false);
        timerUtils.reset();
    }

    @EventTarget
    public void onWorld(EventWorldLoad event) {
        currentPlace = null;
        lastPlaceInfo = null;
        currentHit = null;
        targetRotation = null;
        lastHypixelRotation = null;
        rotationTick = 0;
        stage = 0;
        canRotate = false;
        reachable = true;
        rotateCount = 0;
        counter = 0;
        itemSpoofing = false;
        if (mc.player != null) {
            oldSlot = mc.player.getInventory().selected;
        }
    }

    @EventTarget
    public void onUpdate(EventUpdate event) {
        if (!event.isPre() || mc.player == null || mc.level == null) {
            return;
        }
        if (!autoSneak.getValue()) {
            return;
        }

        Player player = mc.player;
        BlockPos below = player.blockPosition().below();
        boolean isAirBelow = mc.level.getBlockState(below).isAir();

        if (isAirBelow) {
            if (player.onGround()) {
                mc.options.keyShift.setDown(true);
            }
        } else if (player.onGround()) {
            mc.options.keyShift.setDown(false);
        }
    }

    @EventTarget
    public void onClick(EventClick event) {
        setSuffix(mode.getValue());
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            return;
        }

        Player player = mc.player;
        Level world = mc.level;
        if (player.onGround()) {
            reachable = true;
            rotateCount = 0;
        }
        float ny = Mth.wrapDegrees(mc.player.getYRot() - getHarCodedYaw());
        if (mc.player.onGround() && GameStateManager.onGroundTicks <= 1) {
            float normalizedYaw = (ny % 360.0F + 360.0F) % 360.0F;
            float quad = normalizedYaw % 90.0F;
            yawNormal = quad < 45.0F;
        }

        if (mode.is("Telly") && rotationMode.is("Hypixel")) {
            if (rotationTick > 0) {
                rotationTick--;
            }
            if (player.onGround()) {
                if (stage > 0) {
                    stage--;
                }
                if (stage < 0) {
                    stage++;
                }
                if (stage == 0 && !keepY.getValue() && !mc.options.keyJump.isDown()) {
                    stage = 1;
                }
                canRotate = false;
            }
        }

        yLevel = getPlaceY();

        if (mode.is("Telly") && !rotationMode.is("Hypixel")) {
            if (placeDelayMode.is("Check Height")) {
                double threshold = 1.0D + (mc.options.keyJump.isDown() && !keepY.getValue() ? upTellyHeight.getValue().doubleValue() : height.getValue().doubleValue());
                if (fallDist() == 0.0F && mc.player.getY() < yLevel + threshold && mc.player.hurtTime == 0) {
                    return;
                }
            } else if (placeDelayMode.is("Check Tick")) {
                int required = mc.options.keyJump.isDown() && !keepY.getValue() ? upTellyTick.getValue().intValue() : tick.getValue().intValue();
                if (GameStateManager.offGroundTicks < required) {
                    return;
                }
            }
        }

        BlockPos basePos = BlockPos.containing(player.getX(), yLevel, player.getZ());
        PlaceUtils.SearchMode search = searchMode.is("Hypixel") ? PlaceUtils.SearchMode.Hypixel : PlaceUtils.SearchMode.Normal;
        currentPlace = PlaceUtils.getPlaceInfo(basePos, search);
        if (mode.is("Normal") && currentPlace == null && lastPlaceInfo != null) {
            currentPlace = lastPlaceInfo;
        }

        if (currentPlace == null) {
            currentHit = null;
            return;
        }

        lastPlaceInfo = currentPlace;
        currentHit = currentPlace.getRotationAndHitVec(getRotatePoint(), getReferenceRotation());
        if (currentHit == null) {
            return;
        }

        Rotation appliedRot = currentHit.rotation();
        Vec3 hitVec = currentHit.hitVec();
        boolean canPlaceNow;

        if (rotationMode.is("Hypixel")) {
            if (lastHypixelRotation == null) {
                lastHypixelRotation = getReferenceRotation();
            }

            if (canRotate && (player.getDeltaMovement().y > 0.0D || player.getY() > yLevel + 1.0D)) {
                float deltaYaw = Mth.wrapDegrees(appliedRot.yaw() - lastHypixelRotation.yaw());
                int speed = slow.getValue()
                        ? (GameStateManager.offGroundTicks <= 1 ? 60 : 30)
                        : (GameStateManager.offGroundTicks <= 1 ? 150 : 90);
                BlockPos raycastBlock = RotationRaycast.rayCastBlock(4.5D);
                if (!currentPlace.getBlockPos().equals(raycastBlock)) {
                    if (Math.abs(deltaYaw) > speed) {
                        appliedRot.setYaw(updateRotation(lastHypixelRotation.yaw(), lastHypixelRotation.yaw() + deltaYaw, speed));
                        rotationTick = 0;
                    }
                }
            }

            boolean sb = Mth.wrapDegrees(lastHypixelRotation.yaw() - mc.player.getYRot() - getHarCodedYaw()) > 0.0F;
            if (player.onGround()
                    && (mc.options.keyUp.isDown() != mc.options.keyDown.isDown() || mc.options.keyLeft.isDown() != mc.options.keyRight.isDown())
                    && isAirAbove(player, world)) {
                if (stage > 0 || (!keepY.getValue() && mc.options.keyJump.isDown())) {
                    if (GameStateManager.onGroundTicks >= jumpDelayMin.getValue().intValue()) {
                        float yaw = mc.player.getYRot() - getHarCodedYaw()
                                + (isDiagonal() ? (yawNormal ? 157.0F : -157.0F) : (sb ? 157.0F : -157.0F));
                        appliedRot = new Rotation(yaw, 80.0F);
                        rotationTick = 0;
                        lastHypixelRotation = appliedRot;
                        canRotate = true;
                    }
                }
            }

            lastHypixelRotation = appliedRot;
            canPlaceNow = !raycast.is("Off") || rotationTick < 1;
        } else {
            canPlaceNow = true;
        }

        if (raycast.is("Off") && rotationTick >= 1) {
            return;
        }
        if (canPlaceNow && autoStuck.getValue() && currentPlace.getBlockPos().getY() + 1 < mc.player.getY()) {
            Rotation faceRotation = RotationUtils.getRotationBlock(currentPlace.getBlockPos(), currentPlace.getDirection());
            if ((int) mc.player.getEyePosition().distanceTo(Vec3.atCenterOf(currentPlace.getBlockPos())) < 7
                    && getSpeed() > 0.4D
                    && mc.player.tickCount % 20 <= 10) {
                reachable = false;
                appliedRot = faceRotation;
                if (rotateCount == 0) {
                    rotateCount = 10;
                }
                rotateCount--;
            } else {
                if (mc.player.getDeltaMovement().y < -0.05D) {
                    FallingPlayer fallingPlayer = new FallingPlayer(mc.player);
                    fallingPlayer.calculate(2);
                    if (currentPlace.getBlockPos().getY() > fallingPlayer.y) {
                        reachable = false;
                        appliedRot = faceRotation;
                    }
                }

                if (fallDist() <= 1.9F) {
                    reachable = true;
                }

                if (!reachable) {
                    if (rotateCount == 0) {
                        rotateCount = 19;
                    }
                    rotateCount--;
                }
            }
        }

        targetRotation = appliedRot.normalized();
        applyRotation(targetRotation);

        if (hitVec == null) {
            return;
        }

        if (!canPlaceNow || !PlaceUtils.canPlaceAt(BlockPos.containing(mc.player.getX(), yLevel, mc.player.getZ()))) {
            return;
        }

        int placeDelay = nextPlaceDelay();
        if (placeDelay != 0 && !placeTimer.hasTimeElapsed(placeDelay)) {
            return;
        }

        BlockHitResult hitResult = new BlockHitResult(hitVec, currentPlace.getDirection(), currentPlace.getBlockPos(), false);
        switch (raycast.getValue()) {
            case "Normal" -> {
                if (!currentPlace.getBlockPos().equals(RotationRaycast.rayCastBlock(4.5))) {
                    if (mc.level.noCollision(mc.player, mc.player.getBoundingBox().inflate(0.1D, 0.0D, 0.1D))) {
                        return;
                    }
                }
            }
            case "Medium" -> {
                if (!currentPlace.getBlockPos().equals(RotationRaycast.rayCastBlock(targetRotation != null ? targetRotation : getReferenceRotation(), 4.5D))) {
                    if (mc.level.noCollision(mc.player, mc.player.getBoundingBox().inflate(0.1D, 0.0D, 0.1D))) {
                        return;
                    }
                }
            }
        }

        if (!mode.is("Telly") || GameStateManager.offGroundTicks > 3 || mc.player.onGround() && GameStateManager.onGroundTicks < jumpDelayMin.getValue().intValue()) {
            if (mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hitResult) == InteractionResult.SUCCESS) {
                mc.player.swing(InteractionHand.MAIN_HAND);
                placeTimer.reset();
            }
        }
    }

    @EventTarget
    public void onTravel(EventTravel event) {
        if (!reachable && rotateCount > 0) {
            event.cancelEvent();
            if (mc.player != null && mc.player.getAbilities().mayfly && mc.options.keyJump.isDown()) {
                Vec3 velocity = mc.player.getDeltaMovement();
                mc.player.setDeltaMovement(velocity.x, 0.42D, velocity.z);
            }
        } else {
            noti = false;
        }
    }

    @EventTarget
    public void onTick(EventTick event) {
        if (mc.player == null || mc.level == null || !event.isPre()) {
            return;
        }

        if (mode.is("Telly") && MovementUtils.isMoving()) {
            if (rotationMode.is("Hypixel") && isNotAxisAligned(mc.player) && mc.options.keyJump.isDown() && !keepY.getValue()) {
                counter++;
            }
        }

        if (!autoSwitch.getValue()) {
            return;
        }

        int slotId = -1;
        for (int i = 0; i < 9; ++i) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() instanceof BlockItem && isValidStack(stack)) {
                slotId = i;
                break;
            }
        }
        if (slotId != -1 && mc.player.getInventory().selected != slotId) {
            if (itemSpoof.getValue()) {
                itemSpoofing = true;
            }
            mc.player.getInventory().selected = (slotId);
        }
    }

    public ItemStack getSpoofStack() {
        if (!itemSpoof.getValue() || !itemSpoofing || mc.player == null
                || oldSlot < 0 || oldSlot >= 9) {
            return null;
        }
        ItemStack stack = mc.player.getInventory().getItem(oldSlot);
        return stack.isEmpty() ? null : stack;
    }

    @EventTarget
    public void onMoveInput(EventMoveInput event) {
        if (mc.player == null || mc.level == null) {
            return;
        }

        if (mode.is("Telly") && MovementUtils.isMoving()) {
            int min = jumpDelayMin.getValue().intValue();
            int max = jumpDelayMax.getValue().intValue();
            if (max < min) {
                int temp = min;
                min = max;
                max = temp;
            }
            int jumpDelay = min == max ? min : (min + mc.player.getRandom().nextInt(max - min + 1));
            event.setJump(jumpDelay == 0 || GameStateManager.onGroundTicks >= jumpDelay);
        } else {
            counter = 0;
        }
    }

    private void applyRotation(Rotation rotation) {
        MovementFix movementFix = silentRotate.getValue() ? MovementFix.SILENT : MovementFix.NONE;
        boolean smooth = rotationMode.is("Smooth");
        Core.INSTANCE.getRotationManager().setRotations(
                rotation,
                movementFix,
                smooth,
                smooth ? SmoothMode.ADVANCED : SmoothMode.LINEAR,
                rotateSpeed.getValue().floatValue(),
                0,
                0,
                Priority.MEDIUM
        );

        if (!silentRotate.getValue()) {
            Rotation visibleRotation = Core.INSTANCE.getRotationManager().getTargetRotation() != null
                    ? Core.INSTANCE.getRotationManager().getTargetRotation()
                    : rotation;
            mc.player.setYRot(visibleRotation.yaw());
            mc.player.setXRot(visibleRotation.pitch());
            mc.player.setYHeadRot(visibleRotation.yaw());
            mc.player.setYBodyRot(visibleRotation.yaw());
        }
    }

    private Rotation getReferenceRotation() {
        Rotation rotation = Core.INSTANCE.getRotationManager().getServerRotation();
        if (rotation != null) {
            return rotation;
        }
        return getPlayerRotation();
    }

    private Rotation getPlayerRotation() {
        return mc.player == null ? Rotation.ZERO : new Rotation(mc.player.getYRot(), mc.player.getXRot());
    }

    private PlaceInfo.RotatePoint getRotatePoint() {
        return switch (rotatePoint.getValue()) {
            case "Corner" -> PlaceInfo.RotatePoint.Corner;
            case "Nearest" -> PlaceInfo.RotatePoint.Nearest;
            default -> PlaceInfo.RotatePoint.Normal;
        };
    }

    private boolean isDiagonal() {
        return mc.player != null
                && Math.abs(mc.player.getDeltaMovement().x) > 0.07D
                && Math.abs(mc.player.getDeltaMovement().z) > 0.07D;
    }



    public static float getHarCodedYaw() {
        if (mc.screen != null) {
            return 180.0F;
        }

        float simpleYaw = 0.0F;
        long window = mc.getWindow().getWindow();
        boolean w = GLFW.glfwGetKey(window, mc.options.keyUp.getDefaultKey().getValue()) == GLFW.GLFW_PRESS;
        boolean s = GLFW.glfwGetKey(window, mc.options.keyDown.getDefaultKey().getValue()) == GLFW.GLFW_PRESS;
        boolean a = GLFW.glfwGetKey(window, mc.options.keyLeft.getDefaultKey().getValue()) == GLFW.GLFW_PRESS;
        boolean d = GLFW.glfwGetKey(window, mc.options.keyRight.getDefaultKey().getValue()) == GLFW.GLFW_PRESS;

        boolean dupe = a & d;

        if (w) {
            simpleYaw -= 180.0F;
            if (!dupe) {
                if (a) simpleYaw += 45.0F;
                if (d) simpleYaw -= 45.0F;
            }
        } else if (!s) {
            simpleYaw -= 180.0F;
            if (!dupe) {
                if (a) simpleYaw += 90.0F;
                if (d) simpleYaw -= 90.0F;
            }
        } else {
            if (!dupe) {
                if (a) simpleYaw -= 45.0F;
                if (d) simpleYaw += 45.0F;
            }
        }
        return simpleYaw;
    }

    private boolean isNotAxisAligned(Player player) {
        float yaw = Mth.wrapDegrees(player.getYRot());
        float nearestAxis = Math.round(yaw / 90.0F) * 90.0F;
        return Math.abs(Mth.wrapDegrees(yaw - nearestAxis)) > 20.0F;
    }

    private boolean isAirAbove(Player player, Level world) {
        BlockPos above = player.blockPosition().above();
        return world.getBlockState(above).isAir();
    }

    private double getSpeed() {
        if (mc.player == null) {
            return 0.0D;
        }
        Vec3 velocity = mc.player.getDeltaMovement();
        return Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
    }

    public static float clampYaw(float yaw, float maxYaw) {
        maxYaw = Mth.clamp(maxYaw, 0.0F, 180.0F);
        if (yaw > maxYaw) {
            yaw = maxYaw;
        } else if (yaw < -maxYaw) {
            yaw = -maxYaw;
        }
        return yaw;
    }

    private int getPlaceY() {
        if (mc.player == null) {
            return 0;
        }

        if (mc.options.keyJump.isDown() && !keepY.getValue()) {
            return baseY = (int) Math.floor(mc.player.getY() - 1.0D);
        } else {
            return (baseY > mc.player.getY() - 1.0D || mc.player.onGround())
                    ? (baseY = (int) Math.floor(mc.player.getY() - 1.0D))
                    : baseY;
        }
    }

    private int nextPlaceDelay() {
        if (mc.player == null) {
            return 0;
        }

        int min = Math.min(placeDelayMin.getValue().intValue(), placeDelayMax.getValue().intValue());
        int max = Math.max(placeDelayMin.getValue().intValue(), placeDelayMax.getValue().intValue());
        return min == max ? min : min + mc.player.getRandom().nextInt(max - min + 1);
    }

    private float updateRotation(float current, float calc, float maxDelta) {
        float delta = Mth.wrapDegrees(calc - current);
        if (delta > maxDelta) {
            delta = maxDelta;
        }
        if (delta < -maxDelta) {
            delta = -maxDelta;
        }
        return current + delta;
    }

    private float fallDist() {
        return mc.player == null ? 0.0F : mc.player.fallDistance;
    }

    public static boolean isValidStack(ItemStack stack) {
        if (stack != null && stack.getItem() instanceof BlockItem && stack.getCount() > 0) {
            if (!InventoryUtils.isItemValid(stack)) {
                return false;
            } else {
                String string = stack.getHoverName().getString();
                if (!string.contains("Click") && !string.contains("点击")) {
                    {
                        Block block = ((BlockItem) stack.getItem()).getBlock();
                        if (block instanceof FlowerBlock) {
                            return false;
                        } else if (block instanceof BushBlock) {
                            return false;
                        } else if (block instanceof FungusBlock) {
                            return false;
                        } else if (block instanceof CropBlock) {
                            return false;
                        } else if (block instanceof SlabBlock) {
                            return false;
                        } else {
                            return !blacklistedBlocks.contains(block);
                        }
                    }
                } else {
                    return false;
                }
            }
        } else {
            return false;
        }
    }
}
