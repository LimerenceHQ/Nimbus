package io.github.humbleui.skija;

import java.util.*;
import org.jetbrains.annotations.*;

public class GradientStyle {
    @ApiStatus.Internal public static final int _INTERPOLATE_PREMUL = 1;
    public static GradientStyle DEFAULT = new GradientStyle(FilterTileMode.CLAMP, true, null);
    public static GradientStyle REPEAT = new GradientStyle(FilterTileMode.REPEAT, true, null);

    @ApiStatus.Internal public final FilterTileMode _tileMode;
    @ApiStatus.Internal public final boolean _premul;
    @ApiStatus.Internal public final Matrix33 _localMatrix;

    public GradientStyle(FilterTileMode _tileMode, boolean _premul, Matrix33 _localMatrix) {
        this._tileMode = _tileMode;
        this._premul = _premul;
        this._localMatrix = _localMatrix;
    }

    public FilterTileMode getTileMode() {
        return this._tileMode;
    }

    public boolean isPremul() {
        return this._premul;
    }

    public Matrix33 getLocalMatrix() {
        return this._localMatrix;
    }

    public GradientStyle withTileMode(FilterTileMode _tileMode) {
        return this._tileMode == _tileMode ? this : new GradientStyle(_tileMode, this._premul, this._localMatrix);
    }

    public GradientStyle withPremul(boolean _premul) {
        return this._premul == _premul ? this : new GradientStyle(this._tileMode, _premul, this._localMatrix);
    }

    public GradientStyle withLocalMatrix(Matrix33 _localMatrix) {
        return this._localMatrix == _localMatrix ? this : new GradientStyle(this._tileMode, this._premul, _localMatrix);
    }

    @ApiStatus.Internal
    public int _getFlags() {
        return 0 | (_premul ? _INTERPOLATE_PREMUL : 0);
    }

    @ApiStatus.Internal
    public float[] _getMatrixArray() {
        return _localMatrix == null ? null : _localMatrix.getMat();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GradientStyle that = (GradientStyle) o;
        return _premul == that._premul &&
                Objects.equals(_tileMode, that._tileMode) &&
                Objects.equals(_localMatrix, that._localMatrix);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_tileMode, _premul, _localMatrix);
    }

    @Override
    public String toString() {
        return "GradientStyle{" +
                "_tileMode=" + _tileMode +
                ", _premul=" + _premul +
                ", _localMatrix=" + _localMatrix +
                '}';
    }
}