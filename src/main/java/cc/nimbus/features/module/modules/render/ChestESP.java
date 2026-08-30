package cc.nimbus.features.module.modules.render;

import cc.nimbus.core.Core;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.render.EventRender3D;
import cc.nimbus.features.client.ClientSettings;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.BooleanSetting;
import cc.nimbus.features.module.api.setting.impl.MultiSelectSetting;
import cc.nimbus.features.module.api.setting.impl.NumberSetting;
import cc.nimbus.features.module.api.setting.impl.modes.Modes;
import cc.nimbus.utils.render.Render3DUtils;
import cc.nimbus.utils.render.skija.api.SkijaFrame;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintStrokeCap;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.PathBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.entity.SmokerBlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ModuleInfo(name = "ChestESP", description = "Highlights nearby containers.", category = ModuleCategory.Render)
public final class ChestESP extends BasicModule {
    private static final int RENDER_BATCH_SIZE = 256;
    private static final int[][] EDGES = {
            {0, 1}, {1, 3}, {3, 2}, {2, 0},
            {4, 5}, {5, 7}, {7, 6}, {6, 4},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}
    };
    private static final int[][] FACES = {
            {0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1},
            {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3}
    };

    public final MultiSelectSetting containers = new MultiSelectSetting(
            "Containers", new Modes("Chest", "Furnace", "Blast Furnace", "Smoker", "Brewing Stand"),
            Set.of("Chest")
    );
    public final NumberSetting range = new NumberSetting("Range", 120, 16, 360, 8);
    public final NumberSetting lineWidth = new NumberSetting("Line Width", 1.4, 0.5, 4, 0.1);
    public final BooleanSetting fill = new BooleanSetting("Fill", false);
    public final BooleanSetting throughWalls = new BooleanSetting("Through Walls", true);

    private final Minecraft minecraft = Minecraft.getInstance();
    private ClientLevel cachedLevel;
    private List<CachedBlock> cachedBlocks = List.of();
    private Set<String> cachedContainers = Set.of();
    private int cachedCenterX;
    private int cachedCenterZ;
    private int cachedChunkRadius = -1;
    private long nextScanAt;
    private final Map<BlockPos, Boolean> visibilityCache = new HashMap<>();
    private ClientLevel visibilityLevel;
    private long visibilityCacheUntil;

    @EventTarget
    private void onRender3D(EventRender3D event) {
        if (minecraft.level == null || minecraft.player == null
                || !(minecraft.screen == null || minecraft.screen instanceof ChatScreen)) return;

        int radius = range.getValue().intValue();
        int chunkRadius = (radius + 15) >> 4;
        int playerChunkX = minecraft.player.blockPosition().getX() >> 4;
        int playerChunkZ = minecraft.player.blockPosition().getZ() >> 4;
        Set<String> selectedContainers = Set.copyOf(containers.getValue());
        long now = System.currentTimeMillis();
        if (needsScan(minecraft.level, playerChunkX, playerChunkZ, chunkRadius, selectedContainers, now)) {
            scanLoadedChunks(minecraft.level, playerChunkX, playerChunkZ, chunkRadius, selectedContainers);
            nextScanAt = now + 250L;
        }

        Render3DUtils.WorldProjection projection = Render3DUtils.worldProjection();
        if (projection == null) return;

        double maxDistance = (double) radius * radius;
        ClientSettings clientSettings = Core.INSTANCE.getConfigManager().clientSettings();
        List<ProjectedChest> projected = new ArrayList<>(Math.min(cachedBlocks.size(), RENDER_BATCH_SIZE));
        int colorIndex = 0;
        long colorTime = now;

        for (CachedBlock cachedBlock : cachedBlocks) {
            BlockPos pos = cachedBlock.position();
            BlockEntity blockEntity = minecraft.level.getBlockEntity(pos);
            if (blockEntity == null || !isSupported(blockEntity)) continue;
            if (!throughWalls.getValue() && !isVisible(pos, now)) continue;
            double dx = pos.getX() + .5 - minecraft.player.getX();
            double dy = pos.getY() + .5 - minecraft.player.getY();
            double dz = pos.getZ() + .5 - minecraft.player.getZ();
            if (dx * dx + dy * dy + dz * dz > maxDistance) continue;
            int color = withAlpha(clientSettings.accentColor(colorIndex++, colorTime), 0xD0);
            ProjectedChest chest = project(cachedBlock.bounds(), projection, color);
            if (chest != null) projected.add(chest);
        }

        if (projected.isEmpty()) return;
        List<ProjectedChest> snapshot = List.copyOf(projected);
        boolean drawFill = fill.getValue();
        float width = lineWidth.getValue().floatValue();
        Core.INSTANCE.getRenderManager().skija().hud().submit(frame -> {
            for (ProjectedChest chest : snapshot) draw(frame, chest, drawFill, width);
        });
    }

    private boolean needsScan(ClientLevel level, int centerX, int centerZ, int chunkRadius,
                              Set<String> selectedContainers, long now) {
        return cachedLevel != level || cachedCenterX != centerX || cachedCenterZ != centerZ
                || cachedChunkRadius != chunkRadius || !cachedContainers.equals(selectedContainers)
                || now >= nextScanAt;
    }

    private void scanLoadedChunks(ClientLevel level, int centerX, int centerZ, int chunkRadius,
                                  Set<String> selectedContainers) {
        Set<net.minecraft.core.BlockPos> positions = new LinkedHashSet<>();
        for (int chunkX = centerX - chunkRadius; chunkX <= centerX + chunkRadius; chunkX++) {
            for (int chunkZ = centerZ - chunkRadius; chunkZ <= centerZ + chunkRadius; chunkZ++) {
                ChunkAccess chunk = level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                if (!(chunk instanceof net.minecraft.world.level.chunk.LevelChunk levelChunk)) continue;
                for (BlockEntity blockEntity : levelChunk.getBlockEntities().values()) {
                    if (isSupported(blockEntity, selectedContainers)) {
                        positions.add(blockEntity.getBlockPos().immutable());
                    }
                }
            }
        }
        cachedLevel = level;
        cachedCenterX = centerX;
        cachedCenterZ = centerZ;
        cachedChunkRadius = chunkRadius;
        cachedContainers = selectedContainers;
        List<CachedBlock> blocks = new ArrayList<>(positions.size());
        for (BlockPos position : positions) {
            blocks.add(new CachedBlock(position, new AABB(position)));
        }
        cachedBlocks = List.copyOf(blocks);
    }

    private boolean isSupported(BlockEntity blockEntity) {
        return isSupported(blockEntity, containers.getValue());
    }

    private static boolean isSupported(BlockEntity blockEntity, Set<String> selected) {
        if (blockEntity instanceof ChestBlockEntity) return selected.contains("Chest");
        if (blockEntity instanceof BlastFurnaceBlockEntity) return selected.contains("Blast Furnace");
        if (blockEntity instanceof SmokerBlockEntity) return selected.contains("Smoker");
        if (blockEntity instanceof FurnaceBlockEntity) return selected.contains("Furnace");
        return blockEntity instanceof BrewingStandBlockEntity && selected.contains("Brewing Stand");
    }

    @Override
    protected void onDisable() {
        cachedLevel = null;
        cachedBlocks = List.of();
        cachedContainers = Set.of();
        cachedChunkRadius = -1;
        nextScanAt = 0L;
        visibilityCache.clear();
        visibilityLevel = null;
        visibilityCacheUntil = 0L;
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.clamp(alpha, 0, 255) << 24);
    }

    private boolean isVisible(BlockPos position, long now) {
        if (visibilityLevel != minecraft.level || now >= visibilityCacheUntil) {
            visibilityCache.clear();
            visibilityLevel = minecraft.level;
            visibilityCacheUntil = now + 100L;
        }
        return visibilityCache.computeIfAbsent(position, ignored -> {
            Vec3 start = minecraft.player.getEyePosition();
            Vec3 end = Vec3.atCenterOf(position);
            var result = minecraft.level.clip(new ClipContext(
                    start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, minecraft.player));
            return result.getType() == HitResult.Type.MISS
                    || result.getBlockPos().equals(position);
        });
    }

    private static ProjectedChest project(AABB box, Render3DUtils.WorldProjection projection, int color) {
        Vec3[] corners = {
                new Vec3(box.minX, box.minY, box.minZ), new Vec3(box.maxX, box.minY, box.minZ),
                new Vec3(box.minX, box.maxY, box.minZ), new Vec3(box.maxX, box.maxY, box.minZ),
                new Vec3(box.minX, box.minY, box.maxZ), new Vec3(box.maxX, box.minY, box.maxZ),
                new Vec3(box.minX, box.maxY, box.maxZ), new Vec3(box.maxX, box.maxY, box.maxZ)
        };
        Render3DUtils.ProjectedPoint[] points = new Render3DUtils.ProjectedPoint[8];
        for (int index = 0; index < corners.length; index++) {
            points[index] = Render3DUtils.projectPoint(corners[index], projection);
            if (points[index] == null || !points[index].inFront()) return null;
        }
        return new ProjectedChest(points, color);
    }

    private static void draw(SkijaFrame frame, ProjectedChest chest, boolean fill, float lineWidth) {
        Render3DUtils.ProjectedPoint[] points = chest.points();
        if (fill) {
            try (Paint paint = new Paint().setAntiAlias(true).setColor(withAlpha(chest.color(), 0x28))) {
                for (int[] face : FACES) {
                    try (PathBuilder builder = new PathBuilder()) {
                        move(builder, points[face[0]], frame);
                        for (int index = 1; index < face.length; index++) {
                            lineTo(builder, points[face[index]], frame);
                        }
                        builder.closePath();
                        try (Path path = builder.detach()) {
                            frame.canvas().drawPath(path, paint);
                        }
                    }
                }
            }
        }
        try (PathBuilder builder = new PathBuilder();
             Paint paint = new Paint().setAntiAlias(true).setColor(chest.color())
                     .setStroke(true).setStrokeWidth(lineWidth).setStrokeCap(PaintStrokeCap.ROUND)) {
            for (int[] edge : EDGES) {
                Render3DUtils.ProjectedPoint start = points[edge[0]];
                Render3DUtils.ProjectedPoint end = points[edge[1]];
                builder.moveTo(screenX(start, frame.width()), screenY(start, frame.height()));
                builder.lineTo(screenX(end, frame.width()), screenY(end, frame.height()));
            }
            try (Path path = builder.detach()) {
                frame.canvas().drawPath(path, paint);
            }
        }
    }

    private static void move(PathBuilder builder, Render3DUtils.ProjectedPoint point, SkijaFrame frame) {
        builder.moveTo(screenX(point, frame.width()), screenY(point, frame.height()));
    }

    private static void lineTo(PathBuilder builder, Render3DUtils.ProjectedPoint point, SkijaFrame frame) {
        builder.lineTo(screenX(point, frame.width()), screenY(point, frame.height()));
    }

    private static float screenX(Render3DUtils.ProjectedPoint point, float width) {
        return (point.x() + 1f) * .5f * width;
    }

    private static float screenY(Render3DUtils.ProjectedPoint point, float height) {
        return (point.y() + 1f) * .5f * height;
    }

    private record ProjectedChest(Render3DUtils.ProjectedPoint[] points, int color) { }
    private record CachedBlock(BlockPos position, AABB bounds) { }
}
