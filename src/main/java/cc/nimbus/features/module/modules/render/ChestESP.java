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
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.entity.SmokerBlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@ModuleInfo(name = "ChestESP", description = "Highlights nearby containers.", category = ModuleCategory.Render)
public final class ChestESP extends BasicModule {
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
    private List<net.minecraft.core.BlockPos> cachedPositions = List.of();
    private Set<String> cachedContainers = Set.of();
    private int cachedCenterX;
    private int cachedCenterZ;
    private int cachedChunkRadius = -1;
    private long nextScanAt;

    @EventTarget
    private void onRender3D(EventRender3D event) {
        if (minecraft.level == null || minecraft.player == null) return;

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

        double maxDistance = (double) radius * radius;
        ClientSettings clientSettings = Core.INSTANCE.getConfigManager().clientSettings();
        List<Render3DUtils.ColoredBox> outlineBoxes = new ArrayList<>(cachedPositions.size());
        List<Render3DUtils.ColoredBox> filledBoxes = fill.getValue()
                ? new ArrayList<>(cachedPositions.size()) : List.of();
        int colorIndex = 0;
        long colorTime = now;
        Render3DUtils.DepthMode depth = throughWalls.getValue()
                ? Render3DUtils.DepthMode.BOTH : Render3DUtils.DepthMode.NORMAL;

        for (net.minecraft.core.BlockPos pos : cachedPositions) {
            BlockEntity blockEntity = minecraft.level.getBlockEntity(pos);
            if (blockEntity == null || !isSupported(blockEntity)) continue;
            double dx = pos.getX() + .5 - minecraft.player.getX();
            double dy = pos.getY() + .5 - minecraft.player.getY();
            double dz = pos.getZ() + .5 - minecraft.player.getZ();
            if (dx * dx + dy * dy + dz * dz > maxDistance) continue;
            int color = withAlpha(clientSettings.accentColor(colorIndex++, colorTime), 0xD0);
            AABB box = new AABB(pos);
            outlineBoxes.add(new Render3DUtils.ColoredBox(box, color));
            if (fill.getValue()) {
                filledBoxes.add(new Render3DUtils.ColoredBox(box, withAlpha(color, 0x36)));
            }
        }

        if (fill.getValue()) {
            Render3DUtils.drawColoredFilledBoxesWithDepth(event.getPoseStack(), filledBoxes, depth);
        }
        Render3DUtils.drawColoredBoxOutlinesWithDepth(event.getPoseStack(), outlineBoxes,
                lineWidth.getValue().floatValue(), depth);
    }

    private boolean needsScan(ClientLevel level, int centerX, int centerZ, int chunkRadius,
                              Set<String> selectedContainers, long now) {
        return cachedLevel != level || cachedCenterX != centerX || cachedCenterZ != centerZ
                || cachedChunkRadius != chunkRadius || !cachedContainers.equals(selectedContainers)
                || now >= nextScanAt;
    }

    private void scanLoadedChunks(ClientLevel level, int centerX, int centerZ, int chunkRadius,
                                  Set<String> selectedContainers) {
        List<net.minecraft.core.BlockPos> positions = new ArrayList<>();
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
        cachedPositions = List.copyOf(positions);
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
        cachedPositions = List.of();
        cachedContainers = Set.of();
        cachedChunkRadius = -1;
        nextScanAt = 0L;
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.clamp(alpha, 0, 255) << 24);
    }
}
