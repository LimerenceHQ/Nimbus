package cc.nimbus.utils.render;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

public final class PlayerProjectionCache {
    private static Render3DUtils.WorldProjection projection;
    private static List<Entry> entries = List.of();

    private PlayerProjectionCache() {
    }

    public static List<Entry> entries(
            Minecraft minecraft,
            Render3DUtils.WorldProjection currentProjection
    ) {
        var currentLevel = minecraft.level;
        if (currentLevel == null || currentProjection == null) return List.of();
        if (projection == currentProjection) return entries;

        float partialTick = currentProjection.partialTick();
        List<Entry> next = new ArrayList<>(currentLevel.players().size());
        for (Player player : currentLevel.players()) {
            if (player.isSpectator() || player.isRemoved()) continue;
            double x = Mth.lerp(partialTick, player.xOld, player.getX());
            double y = Mth.lerp(partialTick, player.yOld, player.getY());
            double z = Mth.lerp(partialTick, player.zOld, player.getZ());
            AABB bounds = player.getBoundingBox().move(
                    x - player.getX(), y - player.getY(), z - player.getZ()
            ).inflate(0.04, 0.08, 0.04);
            Render3DUtils.ProjectedBox projected = Render3DUtils.projectAabb(bounds, currentProjection);
            if (projected != null && projected.width() > 0f && projected.height() > 0f) {
                next.add(new Entry(player.getId(), projected));
            }
        }
        projection = currentProjection;
        entries = List.copyOf(next);
        return entries;
    }

    public record Entry(int entityId, Render3DUtils.ProjectedBox bounds) {
    }
}
