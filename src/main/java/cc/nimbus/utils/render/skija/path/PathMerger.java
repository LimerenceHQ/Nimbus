package cc.nimbus.utils.render.skija.path;

import cc.nimbus.utils.render.skija.math.RectMath;
import io.github.humbleui.skija.*;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;

import java.util.List;
import java.util.function.Consumer;

/* *
作者：ItzAg2o

* */
public class PathMerger implements AutoCloseable {
    private final Path buffer = new Path();
    private final Path temp = new Path();
    private int count = 0;

    private final RectMath.BoundsMap boundsMap = new RectMath.BoundsMap();

    public <T> void add(T element) {
        Rect elementBounds = null;

        if (element instanceof RRect rRect) {
            elementBounds = rRect;
            buffer.addRRect(rRect);
            count++;
        } else if (element instanceof Rect rect) {
            elementBounds = rect;
            buffer.addRect(rect);
            count++;
        } else if (element instanceof Path path) {
            elementBounds = path.getBounds();
            buffer.addPath(path);
            count++;
        }
        if (elementBounds != null) {
            boundsMap.add(elementBounds);
        }
    }

    public void build(Consumer<Path> consumer) {
        consumer.accept(buffer);
        this.clear();
    }

    public List<Rect> getBounds() {
        return boundsMap.getBounds();
    }

    public void clear() {
        if (count > 0) {
            buffer.rewind();
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
        if (!this.temp.isClosed()) {
            this.temp.close();
        }
        this.boundsMap.clear();
        this.count = 0;
    }
}
