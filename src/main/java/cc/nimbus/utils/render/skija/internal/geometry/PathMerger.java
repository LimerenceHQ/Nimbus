package cc.nimbus.utils.render.skija.internal.geometry;

import io.github.humbleui.skija.*;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;

import java.util.List;
import java.util.function.Consumer;

public class PathMerger implements AutoCloseable {
    private final PathBuilder buffer = new PathBuilder();
    private int count = 0;

    private final RectMath.BoundsMap boundsMap = new RectMath.BoundsMap();

    public void add(RRect rectangle) {
        buffer.addRRect(rectangle);
        added(rectangle);
    }

    public void add(Rect rectangle) {
        buffer.addRect(rectangle);
        added(rectangle);
    }

    public void add(Path path) {
        buffer.addPath(path);
        added(path.getBounds());
    }

    private void added(Rect bounds) {
        count++;
        boundsMap.add(bounds);
    }

    public void build(Consumer<Path> consumer) {
        try (Path path = buffer.snapshot()) {
            consumer.accept(path);
        } finally {
            clear();
        }
    }

    public List<Rect> getBounds() {
        return boundsMap.getBounds();
    }

    public void clear() {
        if (count > 0) {
            buffer.reset();
            count = 0;
        }
        boundsMap.clear();
    }

    public boolean isEmpty() {
        return count == 0;
    }

    @Override
    public void close() {
        if (!this.buffer.isClosed()) {
            this.buffer.close();
        }
        this.boundsMap.clear();
        this.count = 0;
    }
}
