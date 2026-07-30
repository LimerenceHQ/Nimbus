package io.github.humbleui.skija.paragraph;

import java.util.*;
import io.github.humbleui.skija.*;
import io.github.humbleui.types.*;

public class Shadow {
    public final int    _color;
    public final float  _offsetX;
    public final float  _offsetY;
    public final double _blurSigma;

    public Shadow(int _color, float _offsetX, float _offsetY, double _blurSigma) {
        this._color = _color;
        this._offsetX = _offsetX;
        this._offsetY = _offsetY;
        this._blurSigma = _blurSigma;
    }

    public Shadow(int color, Point offset, double blurSigma) {
        this(color, offset.getX(), offset.getY(), blurSigma);
    }

    public int getColor() {
        return this._color;
    }

    public float getOffsetX() {
        return this._offsetX;
    }

    public float getOffsetY() {
        return this._offsetY;
    }

    public double getBlurSigma() {
        return this._blurSigma;
    }

    public Point getOffset() {
        return new Point(_offsetX, _offsetY);
    }

    public Shadow withColor(int _color) {
        return this._color == _color ? this : new Shadow(_color, this._offsetX, this._offsetY, this._blurSigma);
    }

    public Shadow withOffsetX(float _offsetX) {
        return this._offsetX == _offsetX ? this : new Shadow(this._color, _offsetX, this._offsetY, this._blurSigma);
    }

    public Shadow withOffsetY(float _offsetY) {
        return this._offsetY == _offsetY ? this : new Shadow(this._color, this._offsetX, _offsetY, this._blurSigma);
    }

    public Shadow withBlurSigma(double _blurSigma) {
        return this._blurSigma == _blurSigma ? this : new Shadow(this._color, this._offsetX, this._offsetY, _blurSigma);
    }

    public Shadow withOffset(Point offset) {
        return this._offsetX == offset.getX() && this._offsetY == offset.getY() ? this : new Shadow(this._color, offset.getX(), offset.getY(), this._blurSigma);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Shadow shadow = (Shadow) o;
        return _color == shadow._color &&
                Float.compare(shadow._offsetX, _offsetX) == 0 &&
                Float.compare(shadow._offsetY, _offsetY) == 0 &&
                Double.compare(shadow._blurSigma, _blurSigma) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_color, _offsetX, _offsetY, _blurSigma);
    }

    @Override
    public String toString() {
        return "Shadow{" +
                "_color=" + _color +
                ", _offsetX=" + _offsetX +
                ", _offsetY=" + _offsetY +
                ", _blurSigma=" + _blurSigma +
                '}';
    }
}