package cc.nimbus.utils.render.skija.math;

import io.github.humbleui.types.Rect;

import java.util.ArrayList;
import java.util.List;

public class RectMath {
    public static Rect merge(Rect a, Rect b) {
        return Rect.makeLTRB(
                Math.min(a.getLeft(), b.getLeft()),
                Math.min(a.getTop(), b.getTop()),
                Math.max(a.getRight(), b.getRight()),
                Math.max(a.getBottom(), b.getBottom())
        );
    }

    public static class BoundsMap {
        private final List<Rect> bounds = new ArrayList<>();

        public void add(Rect rect) {
            if (rect == null || rect.isEmpty()) return;

            Rect current = rect;
            boolean merged;

            do {
                merged = false;
                for (int i = 0; i < bounds.size(); i++) {
                    Rect existing = bounds.get(i);
                    if (current.intersect(existing) != null) {
                        current = merge(current, existing);
                        bounds.remove(i);
                        merged = true;
                        break;
                    }
                }
            } while (merged);

            bounds.add(current);
        }

        public List<Rect> getBounds() {
            return bounds;
        }

        public void clear() {
            bounds.clear();
        }
    }
}
