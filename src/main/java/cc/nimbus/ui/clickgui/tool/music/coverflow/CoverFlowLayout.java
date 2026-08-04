package cc.nimbus.ui.clickgui.tool.music.coverflow;

import cc.nimbus.ui.clickgui.component.UiRect;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class CoverFlowLayout {
    private static final float VISIBLE_DISTANCE = 4.25f;

    private CoverFlowLayout() {
    }

    public static Result calculate(UiRect bounds, int itemCount, float position) {
        float coverSize = Math.min(300f, Math.min(bounds.height() * .58f, bounds.width() * .34f));
        float spacing = Math.min(coverSize * .24f, bounds.width() / 9f);
        float centerX = bounds.x() + bounds.width() * .5f;
        float centerY = bounds.y() + bounds.height() * .39f;
        List<Slot> slots = new ArrayList<>();
        int first = Math.max(0, (int) Math.floor(position - VISIBLE_DISTANCE));
        int last = Math.min(itemCount - 1, (int) Math.ceil(position + VISIBLE_DISTANCE));
        for (int index = first; index <= last; index++) {
            float relative = index - position;
            float distance = Math.abs(relative);
            if (distance > VISIBLE_DISTANCE) continue;
            float prominence = 1f - Math.clamp(distance / VISIBLE_DISTANCE, 0f, 1f);
            float heightScale = Math.max(.66f, 1f - distance * .08f);
            float height = coverSize * heightScale;
            float widthScale = distance < 1f ? 1f - distance * .38f : .62f;
            float width = coverSize * heightScale * widthScale;
            float sideOffset = distance < 1f
                    ? relative * coverSize * .55f
                    : Math.signum(relative) * (coverSize * .55f + (distance - 1f) * spacing);
            float x = centerX + sideOffset - width * .5f;
            float y = centerY - height * .5f + distance * 8f;
            slots.add(new Slot(index, relative, prominence,
                    new UiRect(x, y, width, height), Math.round(82f + prominence * 173f)));
        }
        slots.sort(Comparator.comparingDouble(Slot::prominence));
        return new Result(List.copyOf(slots), spacing, coverSize);
    }

    public record Result(List<Slot> slots, float itemSpacing, float coverSize) { }
    public record Slot(int index, float relative, float prominence, UiRect bounds, int alpha) { }
}
