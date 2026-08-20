package cc.nimbus.features.module.modules.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import cc.nimbus.core.Core;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.render.EventRender2D;
import cc.nimbus.event.impl.render.EventRender3D;
import cc.nimbus.features.client.ClientSettings;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.BooleanSetting;
import cc.nimbus.features.module.api.setting.impl.submodule.ModeValue;
import cc.nimbus.features.module.api.setting.impl.NumberSetting;
import cc.nimbus.utils.render.Render2DUtils;
import cc.nimbus.utils.render.Render3DUtils;
import cc.nimbus.utils.render.PlayerProjectionCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ModuleInfo(name = "ESP", description = "Highlights other players.", category = ModuleCategory.Render)
public final class ESP extends BasicModule {
    public final BooleanSetting box = new BooleanSetting("Box", true);
    public final ModeValue boxMode = new ModeValue(
            "Box Mode", new String[]{"Box", "Corner A", "Corner B", "Split"}, "Box", box::getValue
    );
    public final BooleanSetting boxFill = new BooleanSetting("Box Fill", false, box::getValue);
    public final NumberSetting lineWidth = new NumberSetting("Line Width", 1.4, 0.5, 3, 0.1, box::getValue);
    public final BooleanSetting healthBar = new BooleanSetting("Health Bar", true);
    public final BooleanSetting armorBar = new BooleanSetting("Armor Bar", true);
    public final BooleanSetting equipment = new BooleanSetting("Equipment", true);
    public final BooleanSetting potionEffects = new BooleanSetting("Potion Effects", true);
    public final BooleanSetting skeleton = new BooleanSetting("Skeleton", false);
    public final BooleanSetting showSelf = new BooleanSetting("Show Self", false);

    private final Map<Integer, SkeletonPose> skeletonPoses = new HashMap<>();
    private final Map<Integer, CachedDetails> detailsCache = new HashMap<>();
    private ClientLevel skeletonLevel;
    private ClientLevel detailsLevel;

    @EventTarget
    private void onRender2D(EventRender2D event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.screen != null) return;

        Render3DUtils.WorldProjection projection = Render3DUtils.worldProjection();
        if (projection == null) return;
        float guiScale = Math.max(1, minecraft.getWindow().getGuiScale());
        long now = System.currentTimeMillis();
        ClientSettings settings = Core.INSTANCE.getConfigManager().clientSettings();
        List<Entry> entries = new ArrayList<>();
        if (detailsLevel != minecraft.level) {
            detailsCache.clear();
            detailsLevel = minecraft.level;
        }
        long gameTime = minecraft.level.getGameTime();
        boolean drawEquipment = equipment.getValue();
        boolean drawEffects = potionEffects.getValue();
        if (gameTime % 20L == 0L) {
            detailsCache.entrySet().removeIf(entry -> gameTime - entry.getValue().tick() > 20L);
        }
        int colorIndex = 0;
        for (PlayerProjectionCache.Entry projectedPlayer : PlayerProjectionCache.entries(minecraft, projection)) {
            if (!(minecraft.level.getEntity(projectedPlayer.entityId()) instanceof Player player)) continue;
            boolean self = player == minecraft.player;
            if (self && (!showSelf.getValue() || minecraft.options.getCameraType().isFirstPerson())) continue;
            Render3DUtils.ProjectedBox projected = projectedPlayer.bounds();

            float health = player.getMaxHealth() <= 0f ? 0f : player.getHealth() / player.getMaxHealth();
            float armor = player.getArmorValue() / 20f;
            Render2DUtils.EspDetails details = details(player, gameTime, drawEquipment, drawEffects);
            entries.add(new Entry(projected, Math.clamp(armor, 0f, 1f),
                    Math.clamp(health, 0f, 1f), settings.accentColor(colorIndex++, now), details));
            Render2DUtils.drawEspEquipment(event.getGraphics(), player.getId(), projected,
                    details.equipment());
        }
        if (entries.isEmpty()) return;

        boolean drawBox = box.getValue();
        String mode = boxMode.getValue();
        boolean fill = boxFill.getValue();
        float strokeWidth = lineWidth.getValue().floatValue();
        boolean drawArmor = armorBar.getValue();
        boolean drawHealth = healthBar.getValue();
        List<Entry> snapshot = List.copyOf(entries);
        Core.INSTANCE.getRenderManager().skija().hud().submit(frame -> {
            for (Entry entry : snapshot) {
                Render2DUtils.drawEspFrame(frame, entry.bounds(), drawBox, mode, fill, strokeWidth,
                        entry.color(), drawArmor, entry.armor(), drawHealth, entry.health());
                Render2DUtils.drawEspEffects(frame, entry.bounds(), entry.details().effects(),
                        !entry.details().equipment().isEmpty(), guiScale);
            }
        });
    }

    @EventTarget
    private void onRender3D(EventRender3D event) {
        if (!skeleton.getValue()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.screen != null) return;
        if (skeletonLevel != minecraft.level) {
            skeletonPoses.clear();
            skeletonLevel = minecraft.level;
        }

        float partialTick = event.getPartialTick();
        long now = System.currentTimeMillis();
        ClientSettings settings = Core.INSTANCE.getConfigManager().clientSettings();
        List<Render3DUtils.LineSegment> segments = new ArrayList<>();
        int colorIndex = 0;
        for (Player player : minecraft.level.players()) {
            boolean self = player == minecraft.player;
            if (self && (!showSelf.getValue() || minecraft.options.getCameraType().isFirstPerson())) continue;
            if (player.isSpectator() || player.isRemoved()) continue;
            SkeletonPose pose = skeletonPoses.get(player.getId());
            if (pose == null) continue;
            int color = 0xE6000000 | (settings.accentColor(colorIndex++, now) & 0x00FFFFFF);
            appendSkeleton(segments, player, pose, partialTick, color);
        }
        if (!segments.isEmpty()) {
            Render3DUtils.drawLineSegmentsWithDepth(
                    event.getPoseStack(), segments, 1.5f, Render3DUtils.DepthMode.BOTH
            );
        }
    }

    public void captureSkeletonPose(int entityId, PlayerModel model) {
        if (!skeleton.getValue()) return;
        skeletonPoses.put(entityId, new SkeletonPose(
                rotation(model.head), rotation(model.rightArm), rotation(model.leftArm),
                rotation(model.rightLeg), rotation(model.leftLeg)
        ));
    }

    @Override
    protected void onDisable() {
        skeletonPoses.clear();
        detailsCache.clear();
        skeletonLevel = null;
        detailsLevel = null;
    }

    private Render2DUtils.EspDetails details(Player player, long tick,
                                             boolean equipment, boolean effects) {
        CachedDetails cached = detailsCache.get(player.getId());
        if (cached != null && cached.tick() == tick
                && cached.equipment() == equipment && cached.effects() == effects) {
            return cached.details();
        }
        Render2DUtils.EspDetails details = Render2DUtils.collectEspDetails(player, equipment, effects);
        detailsCache.put(player.getId(), new CachedDetails(tick, equipment, effects, details));
        return details;
    }

    private static void appendSkeleton(List<Render3DUtils.LineSegment> segments, Player player,
                                       SkeletonPose pose, float partialTick, int color) {
        double x = Mth.lerp(partialTick, player.xOld, player.getX());
        double y = Mth.lerp(partialTick, player.yOld, player.getY());
        double z = Mth.lerp(partialTick, player.zOld, player.getZ());
        float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        float headYaw = Mth.rotLerp(partialTick, player.yHeadRotO, player.yHeadRot);
        boolean crouching = player.isCrouching();
        float yOffset = crouching ? 0.6f : 0.75f;

        PoseStack stack = new PoseStack();
        stack.translate(x, y, z);
        stack.mulPose(Axis.YN.rotationDegrees(bodyYaw));
        stack.scale(player.getScale(), player.getScale(), player.getScale());
        if (crouching) stack.translate(0f, 0f, -0.235f);

        appendRotatedBone(segments, stack, -0.125f, yOffset, 0f,
                0f, -yOffset, 0f, pose.rightLeg, color);
        appendRotatedBone(segments, stack, 0.125f, yOffset, 0f,
                0f, -yOffset, 0f, pose.leftLeg, color);

        if (crouching) stack.translate(0f, 0f, 0.25f);
        stack.pushPose();
        if (crouching) stack.translate(0f, -0.05f, -0.01725f);
        appendRotatedBone(segments, stack, -0.375f, yOffset + 0.55f, 0f,
                0f, -0.5f, 0f, pose.rightArm, color);
        appendRotatedBone(segments, stack, 0.375f, yOffset + 0.55f, 0f,
                0f, -0.5f, 0f, pose.leftArm, color);

        stack.pushPose();
        stack.mulPose(Axis.YP.rotationDegrees(bodyYaw - headYaw));
        stack.translate(0f, yOffset + 0.55f, 0f);
        stack.mulPose(Axis.XP.rotation(pose.head.x));
        appendSegment(segments, stack, 0f, 0f, 0f, 0f, 0.3f, 0f, color);
        stack.popPose();
        stack.popPose();

        if (crouching) {
            stack.mulPose(Axis.XP.rotationDegrees(25f));
            stack.translate(0f, -0.16175f, -0.48025f);
        }
        appendSegment(segments, stack, -0.125f, yOffset, 0f,
                0.125f, yOffset, 0f, color);
        appendSegment(segments, stack, 0f, yOffset, 0f,
                0f, yOffset + 0.55f, 0f, color);
        appendSegment(segments, stack, -0.375f, yOffset + 0.55f, 0f,
                0.375f, yOffset + 0.55f, 0f, color);
    }

    private static void appendRotatedBone(List<Render3DUtils.LineSegment> segments, PoseStack stack,
                                          float x, float y, float z, float endX, float endY, float endZ,
                                          BoneRotation rotation, int color) {
        stack.pushPose();
        stack.translate(x, y, z);
        stack.mulPose(Axis.XP.rotation(rotation.x));
        stack.mulPose(Axis.YP.rotation(rotation.y));
        stack.mulPose(Axis.ZP.rotation(rotation.z));
        appendSegment(segments, stack, 0f, 0f, 0f, endX, endY, endZ, color);
        stack.popPose();
    }

    private static void appendSegment(List<Render3DUtils.LineSegment> segments, PoseStack stack,
                                      float x1, float y1, float z1, float x2, float y2, float z2,
                                      int color) {
        Vector3f start = stack.last().pose().transformPosition(new Vector3f(x1, y1, z1));
        Vector3f end = stack.last().pose().transformPosition(new Vector3f(x2, y2, z2));
        segments.add(new Render3DUtils.LineSegment(
                new Vec3(start.x, start.y, start.z), new Vec3(end.x, end.y, end.z), color
        ));
    }

    private static BoneRotation rotation(net.minecraft.client.model.geom.ModelPart part) {
        return new BoneRotation(part.xRot, part.yRot, part.zRot);
    }

    private record Entry(Render3DUtils.ProjectedBox bounds, float armor, float health, int color,
                         Render2DUtils.EspDetails details) { }
    private record BoneRotation(float x, float y, float z) { }
    private record SkeletonPose(BoneRotation head, BoneRotation rightArm, BoneRotation leftArm,
                                BoneRotation rightLeg, BoneRotation leftLeg) { }
    private record CachedDetails(long tick, boolean equipment, boolean effects,
                                 Render2DUtils.EspDetails details) { }
}
