package io.github.humbleui.types;

import org.jetbrains.annotations.*;
import java.util.Objects;

public class Point {
    public static final Point ZERO = new Point(0, 0);

    @ApiStatus.Internal public final float _x;
    @ApiStatus.Internal public final float _y;

    public Point(float _x, float _y) {
        this._x = _x;
        this._y = _y;
    }

    @Contract("null -> null; !null -> new")
    public static @Nullable float[] flattenArray(@Nullable Point[] pts) {
        if (pts == null) return null;
        float[] arr = new float[pts.length * 2];
        for (int i = 0; i < pts.length; ++i) {
            arr[i * 2] = pts[i]._x;
            arr[i * 2 + 1] = pts[i]._y;
        }
        return arr;
    }

    @Contract("null -> null; !null -> new")
    public static @Nullable Point[] fromArray(@Nullable float[] pts) {
        if (pts == null) return null;
        assert pts.length % 2 == 0 : "Expected " + pts.length + " % 2 == 0";
        Point[] arr = new Point[pts.length / 2];
        for (int i = 0; i < pts.length / 2; ++i)
            arr[i] = new Point(pts[i * 2], pts[i * 2 + 1]);
        return arr;
    }

    public float getX() {
        return this._x;
    }

    public float getY() {
        return this._y;
    }

    public Point withX(float _x) {
        return this._x == _x ? this : new Point(_x, this._y);
    }

    public Point withY(float _y) {
        return this._y == _y ? this : new Point(this._x, _y);
    }

    @NotNull
    public Point offset(float dx, float dy) {
        return dx == 0 && dy == 0 ? this : new Point(_x + dx, _y + dy);
    }

    @NotNull
    public Point offset(@NotNull Point vec) {
        assert vec != null : "Point::offset expected other != null";
        return offset(vec._x, vec._y);
    }

    @NotNull
    public Point scale(float scale) {
        return scale(scale, scale);
    }

    @NotNull
    public Point scale(float sx, float sy) {
        return (sx == 1 && sy == 1) || (_x == 0 && _y == 0) ? this : new Point(_x * sx, _y * sy);
    }

    @NotNull
    public Point inverse() {
        return scale(-1, -1);
    }

    public boolean isEmpty() {
        return _x <= 0 || _y <= 0;
    }

    @NotNull
    public IPoint toIPoint() {
        return new IPoint((int) _x, (int) _y);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Point point = (Point) o;
        return Float.compare(point._x, _x) == 0 && Float.compare(point._y, _y) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_x, _y);
    }

    @Override
    public String toString() {
        return "Point(" + _x + ", " + _y + ")";
    }
}