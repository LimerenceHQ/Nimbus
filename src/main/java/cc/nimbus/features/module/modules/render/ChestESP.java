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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.entity.SmokerBlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
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

    @EventTarget
    private void onRender3D(EventRender3D event) {
        if (minecraft.level == null || minecraft.player == null) return;

        int radius = range.getValue().intValue();
        int chunkRadius = (radius + 15) >> 4;
        int playerChunkX = minecraft.player.blockPosition().getX() >> 4;
        int playerChunkZ = minecraft.player.blockPosition().getZ() >> 4;
        double maxDistance = (double) radius * radius;
        Set<BlockEntity> drawn = new HashSet<>();
        ClientSettings clientSettings = Core.INSTANCE.getConfigManager().clientSettings();
        int colorIndex = 0;
        long colorTime = System.currentTimeMillis();
        Render3DUtils.DepthMode depth = throughWalls.getValue()
                ? Render3DUtils.DepthMode.BOTH : Render3DUtils.DepthMode.NORMAL;

        for (int chunkX = playerChunkX - chunkRadius; chunkX <= playerChunkX + chunkRadius; chunkX++) {
            for (int chunkZ = playerChunkZ - chunkRadius; chunkZ <= playerChunkZ + chunkRadius; chunkZ++) {
                ChunkAccess chunk = minecraft.level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                if (!(chunk instanceof net.minecraft.world.level.chunk.LevelChunk levelChunk)) continue;
                for (BlockEntity blockEntity : levelChunk.getBlockEntities().values()) {
                    if (!drawn.add(blockEntity) || !isSupported(blockEntity)) continue;
                    var pos = blockEntity.getBlockPos();
                    double dx = pos.getX() + .5 - minecraft.player.getX();
                    double dy = pos.getY() + .5 - minecraft.player.getY();
                    double dz = pos.getZ() + .5 - minecraft.player.getZ();
                    if (dx * dx + dy * dy + dz * dz > maxDistance) continue;
                    int color = withAlpha(clientSettings.accentColor(colorIndex++, colorTime), 0xD0);
                    AABB box = new AABB(pos);
                    if (fill.getValue()) {
                        Render3DUtils.drawFilledBoxWithDepth(event.getPoseStack(), box,
                                withAlpha(color, 0x36), depth);
                    }
                    Render3DUtils.drawBoxOutlineWithDepth(event.getPoseStack(), box, color,
                            lineWidth.getValue().floatValue(), depth);
                }
            }
        }
    }

    private boolean isSupported(BlockEntity blockEntity) {
        if (blockEntity instanceof ChestBlockEntity) return containers.isSelected("Chest");
        if (blockEntity instanceof BlastFurnaceBlockEntity) return containers.isSelected("Blast Furnace");
        if (blockEntity instanceof SmokerBlockEntity) return containers.isSelected("Smoker");
        if (blockEntity instanceof FurnaceBlockEntity) return containers.isSelected("Furnace");
        return blockEntity instanceof BrewingStandBlockEntity && containers.isSelected("Brewing Stand");
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.clamp(alpha, 0, 255) << 24);
    }
}
