package io.github.humbleui.skija;

import java.util.*;

public class Color4f {
    public final float _r;
    public final float _g;
    public final float _b;
    public final float _a;

    public Color4f(float r, float g, float b, float a) {
        this._r = r;
        this._g = g;
        this._b = b;
        this._a = a;
    }

    public Color4f(float r, float g, float b) {
        this(r, g, b, 1f);
    }

    public Color4f(float[] rgba) {
        this(rgba[0], rgba[1], rgba[2], rgba[3]);
    }

    public Color4f(int c) {
        this((c >> 16 & 0xFF) / 255f,
                (c >> 8 & 0xFF) / 255f,
                (c & 0xFF) / 255f,
                (c >> 24 & 0xFF) / 255f);
    }

    public float getR() {
        return this._r;
    }

    public float getG() {
        return this._g;
    }

    public float getB() {
        return this._b;
    }

    public float getA() {
        return this._a;
    }

    public Color4f withR(float _r) {
        return this._r == _r ? this : new Color4f(_r, this._g, this._b, this._a);
    }

    public Color4f withG(float _g) {
        return this._g == _g ? this : new Color4f(this._r, _g, this._b, this._a);
    }

    public Color4f withB(float _b) {
        return this._b == _b ? this : new Color4f(this._r, this._g, _b, this._a);
    }

    public Color4f withA(float _a) {
        return this._a == _a ? this : new Color4f(this._r, this._g, this._b, _a);
    }

    public int toColor() {
        return ((Math.round(_a * 255f)) << 24)
                | ((Math.round(_r * 255f)) << 16)
                | ((Math.round(_g * 255f)) << 8)
                | (Math.round(_b * 255f));
    }

    public float[] flatten() {
        return new float[] {_r, _g, _b, _a};
    }

    public static float[] flattenArray(Color4f[] colors) {
        float[] arr = new float[colors.length * 4];
        for (int i = 0; i < colors.length; ++i) {
            arr[i * 4]     = colors[i]._r;
            arr[i * 4 + 1] = colors[i]._g;
            arr[i * 4 + 2] = colors[i]._b;
            arr[i * 4 + 3] = colors[i]._a;
        }
        return arr;
    }

    // TODO premultiply alpha
    public Color4f makeLerp(Color4f other, float weight) {
        return new Color4f(_r + (other._r - _r) * weight,
                _g + (other._g - _g) * weight,
                _b + (other._b - _b) * weight,
                _a + (other._a - _a) * weight);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Color4f color4f = (Color4f) o;
        return Float.compare(color4f._r, _r) == 0 &&
                Float.compare(color4f._g, _g) == 0 &&
                Float.compare(color4f._b, _b) == 0 &&
                Float.compare(color4f._a, _a) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_r, _g, _b, _a);
    }

    @Override
    public String toString() {
        return "Color4f{" +
                "_r=" + _r +
                ", _g=" + _g +
                ", _b=" + _b +
                ", _a=" + _a +
                '}';
    }
}