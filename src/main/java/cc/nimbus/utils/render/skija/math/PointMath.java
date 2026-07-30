package cc.nimbus.utils.render.skija.math;

import io.github.humbleui.types.Point;

public final class PointMath {
    public static Point add(Point a, Point b) {
        return new Point(a._x + b._x, a._y + b._y);
    }

    public static Point sub(Point a, Point b) {
        return new Point(a._x - b._x, a._y - b._y);
    }

    public static Point mul(Point p, float s) {
        return new Point(p._x * s, p._y * s);
    }

    public static Point neg(Point p) {
        return new Point(-p._x, -p._y);
    }

    public static float len(Point p) {
        return (float) Math.sqrt(p._x * p._x + p._y * p._y);
    }

    public static Point normalize(Point p) {
        float l = len(p);
        if (l == 0f) return Point.ZERO;
        return new Point(p._x / l, p._y / l);
    }

    public static float dot(Point a, Point b) {
        return a._x * b._x + a._y * b._y;
    }

    public static Point lerp(Point a, Point b, float t) {
        return new Point(
            a._x + (b._x - a._x) * t,
            a._y + (b._y - a._y) * t
        );
    }

    public static Point bisector(Point in, Point out) {
        return normalize(add(neg(in), out));
    }
}
