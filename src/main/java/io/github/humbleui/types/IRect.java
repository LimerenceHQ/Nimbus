package io.github.humbleui.types;

import org.jetbrains.annotations.*;
import java.util.Objects;

public class IRect {
    public final int _left;
    public final int _top;
    public final int _right;
    public final int _bottom;

    @ApiStatus.Internal
    public IRect(int l, int t, int r, int b) {
        _left = l;
        _top = t;
        _right = r;
        _bottom = b;
    }

    public int getLeft() {
        return this._left;
    }

    public int getTop() {
        return this._top;
    }

    public int getRight() {
        return this._right;
    }

    public int getBottom() {
        return this._bottom;
    }

    public IRect withLeft(int _left) {
        return this._left == _left ? this : new IRect(_left, this._top, this._right, this._bottom);
    }

    public IRect withTop(int _top) {
        return this._top == _top ? this : new IRect(this._left, _top, this._right, this._bottom);
    }

    public IRect withRight(int _right) {
        return this._right == _right ? this : new IRect(this._left, this._top, _right, this._bottom);
    }

    public IRect withBottom(int _bottom) {
        return this._bottom == _bottom ? this : new IRect(this._left, this._top, this._right, _bottom);
    }

    public int getWidth() {
        return _right - _left;
    }

    public int getHeight() {
        return _bottom - _top;
    }

    @NotNull
    public IRect withWidth(int width) {
        return new IRect(_left, _top, _left + width, _bottom);
    }

    @NotNull
    public IRect withHeight(int height) {
        return new IRect(_left, _top, _right, _top + height);
    }

    @NotNull
    public static IRect makeLTRB(int l, int t, int r, int b) {
        if (l > r)
            throw new IllegalArgumentException("IRect::makeLTRB expected l <= r, got " + l + " > " + r);
        if (t > b)
            throw new IllegalArgumentException("IRect::makeLTRB expected t <= b, got " + t + " > " + b);
        return new IRect(l, t, r, b);
    }

    @NotNull
    public static IRect makeXYWH(int l, int t, int w, int h) {
        if (w < 0)
            throw new IllegalArgumentException("IRect::makeXYWH expected w >= 0, got: " + w);
        if (h < 0)
            throw new IllegalArgumentException("IRect::makeXYWH expected h >= 0, got: " + h);
        return w >= 0 && h >= 0 ? new IRect(l, t, l + w, t + h) : null;
    }

    @NotNull
    public static IRect makeWH(int w, int h) {
        if (w < 0)
            throw new IllegalArgumentException("IRect::makeWH expected w >= 0, got: " + w);
        if (h < 0)
            throw new IllegalArgumentException("IRect::makeWH expected h >= 0, got: " + h);
        return w >= 0 && h >= 0 ? new IRect(0, 0, w, h) : null;
    }

    @Nullable
    public IRect intersect(@NotNull IRect other) {
        assert other != null : "IRect::intersect expected other != null";
        if (_right <= other._left || other._right <= _left || _bottom <= other._top || other._bottom <= _top)
            return null;
        return new IRect(Math.max(_left, other._left), Math.max(_top, other._top), Math.min(_right, other._right), Math.min(_bottom, other._bottom));
    }

    @NotNull
    public IRect scale(int scale) {
        return scale(scale, scale);
    }

    @NotNull
    public IRect scale(int sx, int sy) {
        return sx == 1 && sy == 1 ? this : new IRect(_left * sx, _top * sy, _right * sx, _bottom * sy);
    }

    @NotNull
    public IRect offset(int dx, int dy) {
        return dx == 0 && dy == 0 ? this : new IRect(_left + dx, _top + dy, _right + dx, _bottom + dy);
    }

    @NotNull
    public IRect offset(@NotNull IPoint vec) {
        assert vec != null : "IRect::offset expected vec != null";
        return offset(vec._x, vec._y);
    }

    @NotNull
    public Rect toRect() {
        return new Rect(_left, _top, _right, _bottom);
    }

    public boolean isEmpty() {
        return _right == _left || _top == _bottom;
    }

    public boolean contains(int x, int y) {
        return _left <= x && x <= _right && _top <= y && y <= _bottom;
    }

    public boolean contains(@NotNull IPoint vec) {
        assert vec != null : "IRect::contains expected vec != null";
        return _left <= vec._x && vec._x <= _right && _top <= vec._y && vec._y <= _bottom;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IRect iRect = (IRect) o;
        return _left == iRect._left && _top == iRect._top && _right == iRect._right && _bottom == iRect._bottom;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_left, _top, _right, _bottom);
    }

    @Override
    public String toString() {
        return "IRect(" + _left + ", " + _top + ", " + _right + ", " + _bottom + ")";
    }
}