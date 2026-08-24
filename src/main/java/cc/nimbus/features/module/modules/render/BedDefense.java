package cc.nimbus.features.module.modules.render;

import cc.nimbus.core.Core;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.render.EventRender2D;
import cc.nimbus.event.impl.render.EventRender3D;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.BooleanSetting;
import cc.nimbus.features.module.api.setting.impl.NumberSetting;
import cc.nimbus.utils.client.TimerUtils;
import cc.nimbus.utils.render.Render3DUtils;
import cc.nimbus.utils.render.font.NimbusFonts;
import cc.nimbus.utils.render.skija.api.SkijaFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ModuleInfo(
        name = "BedDefense",
        displayName = "Bed Defense",
        alias = {"BedShow"},
        description = "Shows the blocks protecting nearby beds.",
        category = ModuleCategory.Render
)
public final class BedDefense extends BasicModule {
    private static final String FONT = NimbusFonts.BODY_MEDIUM;
    private static final String TITLE_FONT = NimbusFonts.BODY_STRONG;
    private static final long REBUILD_INTERVAL_MS = 900L;
    private static final int MAX_BEDS = 24;
    private static final int MAX_BLOCK_TYPES = 10;

    public final NumberSetting range = new NumberSetting("Range", 64, 8, 128, 1);
    public final NumberSetting defenseRadius = new NumberSetting("Defense Radius", 2, 1, 4, 1);
    public final BooleanSetting icons = new BooleanSetting("Icons", true);

    private final TimerUtils rebuildTimer = new TimerUtils();
    private final List<BedOverlay> overlays = new ArrayList<>();
    private final Map<Block, String> blockNameCache = new HashMap<>();
    private volatile List<GuiItemEntry> pendingItems = List.of();
    private ClientLevel level;
    private BlockPos lastOrigin;
    private int lastRange = -1;
    private int lastDefenseRadius = -1;

    @EventTarget
    private void onRender3D(EventRender3D event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null
                || !(minecraft.screen == null || minecraft.screen instanceof net.minecraft.client.gui.screens.ChatScreen)) return;
        if (needsRebuild(minecraft)) {
            rebuildTimer.reset();
            rebuild(minecraft);
        }
        if (overlays.isEmpty()) return;
        Render3DUtils.WorldProjection projection = Render3DUtils.worldProjection();
        if (projection == null) return;
        float partialTick = event.getPartialTick();
        for (BedOverlay overlay : overlays) {
            overlay.point = Render3DUtils.projectPoint(new Vec3(
                    overlay.head.getX() + .5, overlay.head.getY() + 1.15, overlay.head.getZ() + .5), projection);
        }
    }

    @EventTarget
    private void onRender2D(EventRender2D event) {
        if (event.isPost()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || overlays.isEmpty()
                || !(minecraft.screen == null || minecraft.screen instanceof net.minecraft.client.gui.screens.ChatScreen)) {
            pendingItems = List.of();
            return;
        }
        List<BedEntry> entries = new ArrayList<>();
        for (BedOverlay overlay : overlays) {
            if (overlay.point == null || !overlay.point.inFront() || overlay.entries.isEmpty()) continue;
            entries.add(new BedEntry(overlay.point, overlay.distance, List.copyOf(overlay.entries)));
        }
        if (entries.isEmpty()) {
            pendingItems = List.of();
            return;
        }
        boolean showIcons = icons.getValue();
        List<BedEntry> snapshot = List.copyOf(entries);
        pendingItems = showIcons ? itemPlacements(snapshot, Core.INSTANCE.getRenderManager().skija().width(),
                Core.INSTANCE.getRenderManager().skija().height()) : List.of();
        Core.INSTANCE.getRenderManager().skija().hud().submit(frame -> draw(frame, snapshot, showIcons));
    }

    @EventTarget
    private void onRender2DPost(EventRender2D event) {
        if (!event.isPost()) return;
        List<GuiItemEntry> items = pendingItems;
        if (items.isEmpty()) return;
        GuiGraphics graphics = event.getGraphics();
        float scaleX = Core.INSTANCE.getRenderManager().skija().width()
                / (float) Math.max(1, graphics.guiWidth());
        float scaleY = Core.INSTANCE.getRenderManager().skija().height()
                / (float) Math.max(1, graphics.guiHeight());
        for (GuiItemEntry item : items) {
            var pose = graphics.pose();
            pose.pushPose();
            try {
                pose.translate(item.x() / scaleX, item.y() / scaleY, 0f);
                pose.scale(24f / (16f * scaleX), 24f / (16f * scaleY), 1f);
                graphics.renderItem(item.stack(), 0, 0, item.seed());
            } finally {
                pose.popPose();
            }
        }
    }

    private static void draw(SkijaFrame frame, List<BedEntry> entries, boolean showIcons) {
        for (BedEntry entry : entries) {
            float pointX = screenX(entry.point(), frame.width());
            float pointY = screenY(entry.point(), frame.height());
            float titleSize = 11f;
            float countSize = 8f;
            float padding = 6f;
            float gap = 4f;
            float iconSize = 24f;
            String title = distanceText(entry.distance());
            int visibleEntries = Math.min(8, entry.entries().size());
            float contentWidth = visibleEntries * iconSize + Math.max(0, visibleEntries - 1) * gap;
            float titleWidth = frame.text().width(TITLE_FONT, titleSize, title);
            float width = Math.max(titleWidth, contentWidth) + padding * 2f;
            float height = padding + 12f + gap + iconSize + padding;
            float left = Math.clamp(pointX - width * .5f, 3f, Math.max(3f, frame.width() - width - 3f));
            float top = Math.clamp(pointY - height - 7f, 3f, Math.max(3f, frame.height() - height - 3f));
            frame.text().center(TITLE_FONT, titleSize, title, left + width * .5f, top + 5f, 0xFFF2F4F5);
            float x = left + (width - contentWidth) * .5f;
            float y = top + padding + 12f + gap;
            for (int index = 0; index < visibleEntries; index++) {
                DefenseEntry defense = entry.entries().get(index);
                if (!showIcons || defense.stack().isEmpty()) {
                    frame.text().center(FONT, countSize, defense.name(), x + iconSize * .5f,
                            y + 7f, 0xFFD7DDE1);
                }
                String count = "x" + defense.count();
                float countWidth = frame.text().width(TITLE_FONT, countSize, count);
                frame.text().draw(TITLE_FONT, countSize, count,
                        x + iconSize - countWidth + 1f, y + iconSize - 3f, 0xFFFFFFFF);
                x += iconSize + gap;
            }
        }
    }

    private static List<GuiItemEntry> itemPlacements(List<BedEntry> entries, float frameWidth, float frameHeight) {
        List<GuiItemEntry> result = new ArrayList<>();
        float titleSize = 11f;
        float padding = 6f;
        float gap = 4f;
        float iconSize = 24f;
        for (BedEntry entry : entries) {
            String title = distanceText(entry.distance());
            int visibleEntries = Math.min(8, entry.entries().size());
            float contentWidth = visibleEntries * iconSize + Math.max(0, visibleEntries - 1) * gap;
            float titleWidth = Core.INSTANCE.getRenderManager().skija().text()
                    .width(TITLE_FONT, titleSize, title);
            float width = Math.max(titleWidth, contentWidth) + padding * 2f;
            float height = padding + 12f + gap + iconSize + padding;
            float pointX = screenX(entry.point(), frameWidth);
            float pointY = screenY(entry.point(), frameHeight);
            float left = Math.clamp(pointX - width * .5f, 3f, Math.max(3f, frameWidth - width - 3f));
            float top = Math.clamp(pointY - height - 7f, 3f, Math.max(3f, frameHeight - height - 3f));
            float x = left + (width - contentWidth) * .5f;
            float y = top + padding + 12f + gap;
            for (int index = 0; index < visibleEntries; index++) {
                DefenseEntry defense = entry.entries().get(index);
                if (!defense.stack().isEmpty()) {
                    result.add(new GuiItemEntry(defense.stack(), x, y, entry.point().hashCode() + index));
                }
                x += iconSize + gap;
            }
        }
        return List.copyOf(result);
    }

    private static String distanceText(float distance) {
        return Core.INSTANCE.getI18nManager().get(
                "nimbus.module.bed_defense.overlay.distance", "%.0fm", distance);
    }

    private boolean needsRebuild(Minecraft minecraft) {
        BlockPos origin = minecraft.player.blockPosition();
        int currentRange = range.getValue().intValue();
        int currentRadius = defenseRadius.getValue().intValue();
        return level != minecraft.level || lastOrigin == null
                || currentRange != lastRange || currentRadius != lastDefenseRadius
                || origin.distSqr(lastOrigin) >= 16
                || rebuildTimer.hasTimeElapsed(REBUILD_INTERVAL_MS);
    }

    private void rebuild(Minecraft minecraft) {
        overlays.clear();
        level = minecraft.level;
        lastOrigin = minecraft.player.blockPosition().immutable();
        lastRange = range.getValue().intValue();
        lastDefenseRadius = defenseRadius.getValue().intValue();
        int chunkRadius = (lastRange + 15) >> 4;
        Set<Long> seenHeads = new HashSet<>();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        double rangeSquared = lastRange * (double) lastRange;
        for (int chunkX = (lastOrigin.getX() >> 4) - chunkRadius;
             chunkX <= (lastOrigin.getX() >> 4) + chunkRadius; chunkX++) {
            for (int chunkZ = (lastOrigin.getZ() >> 4) - chunkRadius;
                 chunkZ <= (lastOrigin.getZ() >> 4) + chunkRadius; chunkZ++) {
                ChunkAccess chunk = minecraft.level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                if (!(chunk instanceof net.minecraft.world.level.chunk.LevelChunk)) continue;
                int minX = Math.max(lastOrigin.getX() - lastRange, chunkX << 4);
                int maxX = Math.min(lastOrigin.getX() + lastRange, (chunkX << 4) + 15);
                int minZ = Math.max(lastOrigin.getZ() - lastRange, chunkZ << 4);
                int maxZ = Math.min(lastOrigin.getZ() + lastRange, (chunkZ << 4) + 15);
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        if ((x - lastOrigin.getX()) * (double) (x - lastOrigin.getX())
                                + (z - lastOrigin.getZ()) * (double) (z - lastOrigin.getZ()) > rangeSquared) continue;
                        for (int y = lastOrigin.getY() - 6; y <= lastOrigin.getY() + 6; y++) {
                            mutable.set(x, y, z);
                            BlockState state = minecraft.level.getBlockState(mutable);
                            if (!(state.getBlock() instanceof BedBlock)
                                    || state.getValue(BedBlock.PART) != BedPart.HEAD
                                    || !seenHeads.add(mutable.asLong())) continue;
                            BedOverlay overlay = buildOverlay(mutable.immutable(), state);
                            if (overlay != null) overlays.add(overlay);
                        }
                    }
                }
            }
        }
        overlays.sort(Comparator.comparingDouble(o -> o.distance));
        if (overlays.size() > MAX_BEDS) overlays.subList(MAX_BEDS, overlays.size()).clear();
    }

    private BedOverlay buildOverlay(BlockPos head, BlockState state) {
        Direction facing = state.getValue(BedBlock.FACING);
        BlockPos foot = head.relative(facing.getOpposite());
        Map<Block, Integer> counts = new HashMap<>();
        Set<Long> counted = new HashSet<>();
        accumulate(counts, counted, head, head, foot);
        accumulate(counts, counted, foot, head, foot);
        if (counts.isEmpty()) return null;
        List<Map.Entry<Block, Integer>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort(Comparator.<Map.Entry<Block, Integer>>comparingInt(entry -> -entry.getValue())
                .thenComparing(entry -> blockName(entry.getKey()), String.CASE_INSENSITIVE_ORDER));
        List<DefenseEntry> entries = new ArrayList<>();
        for (Map.Entry<Block, Integer> entry : sorted.subList(0, Math.min(MAX_BLOCK_TYPES, sorted.size()))) {
            String name = blockName(entry.getKey());
            if (!name.isEmpty()) entries.add(new DefenseEntry(name, entry.getValue(),
                    new ItemStack(entry.getKey().asItem())));
        }
        if (entries.isEmpty()) return null;
        Minecraft minecraft = Minecraft.getInstance();
        return new BedOverlay(head, entries, (float) Math.sqrt(minecraft.player.distanceToSqr(
                new Vec3(head.getX() + .5, head.getY() + .5, head.getZ() + .5))));
    }

    private void accumulate(Map<Block, Integer> counts, Set<Long> counted,
                            BlockPos base, BlockPos head, BlockPos foot) {
        int radius = lastDefenseRadius;
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int dy = 0; dy <= 1; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dz == 0 && dy == 0) continue;
                    mutable.set(base.getX() + dx, base.getY() + dy, base.getZ() + dz);
                    if (!counted.add(mutable.asLong()) || mutable.equals(head) || mutable.equals(foot)) continue;
                    BlockState state = Minecraft.getInstance().level.getBlockState(mutable);
                    if (state.isAir() || !state.getFluidState().isEmpty()
                            || state.getCollisionShape(Minecraft.getInstance().level, mutable).isEmpty()
                            || state.hasBlockEntity() || state.getBlock() instanceof BedBlock) continue;
                    Block block = state.getBlock();
                    if (!block.asItem().getDefaultInstance().isEmpty()) counts.merge(block, 1, Integer::sum);
                }
            }
        }
    }

    private String blockName(Block block) {
        return blockNameCache.computeIfAbsent(block, key -> {
            ItemStack stack = new ItemStack(key.asItem());
            return stack.isEmpty() ? "" : stack.getHoverName().getString();
        });
    }

    private static float screenX(Render3DUtils.ProjectedPoint point, float width) {
        return (point.x() + 1f) * .5f * width;
    }

    private static float screenY(Render3DUtils.ProjectedPoint point, float height) {
        return (point.y() + 1f) * .5f * height;
    }

    @Override
    protected void onDisable() {
        overlays.clear();
        level = null;
        lastOrigin = null;
        blockNameCache.clear();
        pendingItems = List.of();
    }

    private static final class BedOverlay {
        private final BlockPos head;
        private final List<DefenseEntry> entries;
        private final float distance;
        private Render3DUtils.ProjectedPoint point;

        private BedOverlay(BlockPos head, List<DefenseEntry> entries, float distance) {
            this.head = head;
            this.entries = entries;
            this.distance = distance;
        }
    }

    private record DefenseEntry(String name, int count, ItemStack stack) { }
    private record BedEntry(Render3DUtils.ProjectedPoint point, float distance, List<DefenseEntry> entries) { }
    private record GuiItemEntry(ItemStack stack, float x, float y, int seed) { }
}
