package io.github.humbleui.skija;

import java.util.Arrays;

public class ColorMatrix {
    public final float[] _mat;

    public ColorMatrix(float... mat) {
        assert mat.length == 20 : "Expected 20 elements, got " + mat == null ? null : mat.length;
        _mat = mat;
    }

    public float[] getMat() {
        return this._mat;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ColorMatrix that = (ColorMatrix) o;
        return Arrays.equals(_mat, that._mat);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(_mat);
    }

    @Override
    public String toString() {
        return "ColorMatrix{" +
                "_mat=" + Arrays.toString(_mat) +
                '}';
    }
}