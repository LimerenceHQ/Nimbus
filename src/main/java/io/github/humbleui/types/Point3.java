package io.github.humbleui.types;

import org.jetbrains.annotations.*;
import java.util.Objects;

public class Point3 {
    public static final Point3 ZERO = new Point3(0, 0, 0);

    @ApiStatus.Internal public final float _x;
    @ApiStatus.Internal public final float _y;
    @ApiStatus.Internal public final float _z;

    public Point3(float _x, float _y, float _z) {
        this._x = _x;
        this._y = _y;
        this._z = _z;
    }

    public float getX() {
        return this._x;
    }

    public float getY() {
        return this._y;
    }

    public float getZ() {
        return this._z;
    }

    public Point3 withX(float _x) {
        return this._x == _x ? this : new Point3(_x, this._y, this._z);
    }

    public Point3 withY(float _y) {
        return this._y == _y ? this : new Point3(this._x, _y, this._z);
    }

    public Point3 withZ(float _z) {
        return this._z == _z ? this : new Point3(this._x, this._y, _z);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Point3 point3 = (Point3) o;
        return Float.compare(point3._x, _x) == 0 &&
                Float.compare(point3._y, _y) == 0 &&
                Float.compare(point3._z, _z) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_x, _y, _z);
    }

    @Override
    public String toString() {
        return "Point3(" + _x + ", " + _y + ", " + _z + ")";
    }
}