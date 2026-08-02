package cc.nimbus.utils.render.skija.internal.geometry;

import io.github.humbleui.skija.PathBuilder;
import io.github.humbleui.types.Point;

import static cc.nimbus.utils.render.skija.internal.geometry.PointMath.*;

public class PathMath {
    public static void cubicCorner(PathBuilder p, Point p0, Point p3, Point t0, Point t1, float k) {
        p.lineTo(p0._x, p0._y);

        Point p1 = add(p0, mul(t0, k));
        Point p2 = sub(p3, mul(t1, k));

        p.cubicTo(p1._x, p1._y, p2._x, p2._y, p3._x, p3._y);
    }
}
