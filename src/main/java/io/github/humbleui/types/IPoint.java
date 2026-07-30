package io.github.humbleui.types;

import org.jetbrains.annotations.*;
import java.util.Objects;

public class IPoint {
    public static final IPoint ZERO = new IPoint(0, 0);

    @ApiStatus.Internal public final int _x;
    @ApiStatus.Internal public final int _y;

    public IPoint(int _x, int _y) {
        this._x = _x;
        this._y = _y;
    }

    @ApiStatus.Internal
    public static IPoint _makeFromLong(long l) {
        return new IPoint((int) (l >>> 32), (int) (l & 0xFFFFFFFF));
    }

    public int getX() {
        return this._x;
    }

    public int getY() {
        return this._y;
    }

    public IPoint withX(int _x) {
        return this._x == _x ? this : new IPoint(_x, this._y);
    }

    public IPoint withY(int _y) {
        return this._y == _y ? this : new IPoint(this._x, _y);
    }

    @NotNull
    public IPoint offset(int dx, int dy) {
        return dx == 0 && dy == 0 ? this : new IPoint(_x + dx, _y + dy);
    }

    @NotNull
    public IPoint offset(@NotNull IPoint vec) {
        assert vec != null : "IPoint::offset expected other != null";
        return offset(vec._x, vec._y);
    }

    @NotNull
    public IPoint scale(int scale) {
        return scale(scale, scale);
    }

    @NotNull
    public IPoint scale(int sx, int sy) {
        return (sx == 1 && sy == 1) || (_x == 0 && _y == 0) ? this : new IPoint(_x * sx, _y * sy);
    }

    @NotNull
    public IPoint inverse() {
        return scale(-1, -1);
    }

    public boolean isEmpty() {
        return _x <= 0 || _y <= 0;
    }

    @NotNull
    public Point toPoint() {
        return new Point(_x, _y);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IPoint iPoint = (IPoint) o;
        return _x == iPoint._x && _y == iPoint._y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_x, _y);
    }

    @Override
    public String toString() {
        return "IPoint(" + _x + ", " + _y + ")";
    }
}