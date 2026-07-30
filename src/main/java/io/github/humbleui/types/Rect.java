package io.github.humbleui.types;

import org.jetbrains.annotations.*;
import java.util.Objects;

public class Rect {
    public final float _left;
    public final float _top;
    public final float _right;
    public final float _bottom;

    @ApiStatus.Internal
    public Rect(float l, float t, float r, float b) {
        this._left = l;
        this._top = t;
        this._right = r;
        this._bottom = b;
    }

    public float getLeft() {
        return this._left;
    }

    public float getTop() {
        return this._top;
    }

    public float getRight() {
        return this._right;
    }

    public float getBottom() {
        return this._bottom;
    }

    public Rect withLeft(float _left) {
        return this._left == _left ? this : new Rect(_left, this._top, this._right, this._bottom);
    }

    public Rect withTop(float _top) {
        return this._top == _top ? this : new Rect(this._left, _top, this._right, this._bottom);
    }

    public Rect withRight(float _right) {
        return this._right == _right ? this : new Rect(this._left, this._top, _right, this._bottom);
    }

    public Rect withBottom(float _bottom) {
        return this._bottom == _bottom ? this : new Rect(this._left, this._top, this._right, _bottom);
    }

    public float getWidth() {
        return _right - _left;
    }

    public float getHeight() {
        return _bottom - _top;
    }

    @NotNull
    public Rect withWidth(float width) {
        return new Rect(_left, _top, _left + width, _bottom);
    }

    @NotNull
    public Rect withHeight(float height) {
        return new Rect(_left, _top, _right, _top + height);
    }

    @NotNull
    public static Rect makeLTRB(float l, float t, float r, float b) {
        if (l > r)
            throw new IllegalArgumentException("Rect::makeLTRB expected l <= r, got " + l + " > " + r);
        if (t > b)
            throw new IllegalArgumentException("Rect::makeLTRB expected t <= b, got " + t + " > " + b);
        return new Rect(l, t, r, b);
    }

    @NotNull
    public static Rect makeWH(float w, float h) {
        if (w < 0)
            throw new IllegalArgumentException("Rect::makeWH expected w >= 0, got: " + w);
        if (h < 0)
            throw new IllegalArgumentException("Rect::makeWH expected h >= 0, got: " + h);
        return new Rect(0, 0, w, h);
    }

    @NotNull
    public static Rect makeWH(@NotNull Point size) {
        assert size != null : "Rect::makeWH expected size != null";
        return makeWH(size._x, size._y);
    }

    @NotNull
    public static Rect makeXYWH(float l, float t, float w, float h) {
        if (w < 0)
            throw new IllegalArgumentException("Rect::makeXYWH expected w >= 0, got: " + w);
        if (h < 0)
            throw new IllegalArgumentException("Rect::makeXYWH expected h >= 0, got: " + h);
        return new Rect(l, t, l + w, t + h);
    }

    @Nullable
    public Rect intersect(@NotNull Rect other) {
        assert other != null : "Rect::intersect expected other != null";
        if (_right <= other._left || other._right <= _left || _bottom <= other._top || other._bottom <= _top)
            return null;
        return new Rect(Math.max(_left, other._left), Math.max(_top, other._top), Math.min(_right, other._right), Math.min(_bottom, other._bottom));
    }

    @NotNull
    public Rect scale(float scale) {
        return scale(scale, scale);
    }

    @NotNull
    public Rect scale(float sx, float sy) {
        return sx == 1 && sy == 1 ? this : new Rect(_left * sx, _top * sy, _right * sx, _bottom * sy);
    }

    @NotNull
    public Rect offset(float dx, float dy) {
        return dx == 0 && dy == 0 ? this : new Rect(_left + dx, _top + dy, _right + dx, _bottom + dy);
    }

    @NotNull
    public Rect offset(@NotNull Point vec) {
        assert vec != null : "Rect::offset expected vec != null";
        return offset(vec._x, vec._y);
    }

    @NotNull
    public IRect toIRect() {
        return new IRect((int) _left, (int) _top, (int) _right, (int) _bottom);
    }

    @NotNull
    public RRect withRadii(float radius) {
        return RRect.makeLTRB(_left, _top, _right, _bottom, radius);
    }

    @NotNull
    public RRect withRadii(float xRad, float yRad) {
        return RRect.makeLTRB(_left, _top, _right, _bottom, xRad, yRad);
    }

    @NotNull
    public RRect withRadii(float tlRad, float trRad, float brRad, float blRad) {
        return RRect.makeLTRB(_left, _top, _right, _bottom, tlRad, trRad, brRad, blRad);
    }

    @NotNull
    public RRect withRadii(float[] radii) {
        return RRect.makeComplexLTRB(_left, _top, _right, _bottom, radii);
    }

    @NotNull
    public Rect inflate(float spread) {
        if (spread <= 0)
            return Rect.makeLTRB(_left - spread,
                    _top - spread,
                    Math.max(_left - spread, _right + spread),
                    Math.max(_top - spread, _bottom + spread));
        else
            return RRect.makeLTRB(_left - spread,
                    _top - spread,
                    Math.max(_left - spread, _right + spread),
                    Math.max(_top - spread, _bottom + spread),
                    spread);
    }

    public boolean isEmpty() {
        return _right == _left || _top == _bottom;
    }

    public boolean contains(float x, float y) {
        return _left <= x && x <= _right && _top <= y && y <= _bottom;
    }

    public boolean contains(@NotNull Point vec) {
        assert vec != null : "Rect::contains expected vec != null";
        return _left <= vec._x && vec._x <= _right && _top <= vec._y && vec._y <= _bottom;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Rect rect = (Rect) o;
        return Float.compare(rect._left, _left) == 0 &&
                Float.compare(rect._top, _top) == 0 &&
                Float.compare(rect._right, _right) == 0 &&
                Float.compare(rect._bottom, _bottom) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_left, _top, _right, _bottom);
    }

    @Override
    public String toString() {
        return "Rect(" + _left + ", " + _top + ", " + _right + ", " + _bottom + ")";
    }
}