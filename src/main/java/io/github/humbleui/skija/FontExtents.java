package io.github.humbleui.skija;

import java.util.*;

public class FontExtents {
    public final float _ascender;
    public final float _descender;
    public final float _lineGap;

    public FontExtents(float _ascender, float _descender, float _lineGap) {
        this._ascender = _ascender;
        this._descender = _descender;
        this._lineGap = _lineGap;
    }

    public float getAscender() {
        return this._ascender;
    }

    public float getDescender() {
        return this._descender;
    }

    public float getLineGap() {
        return this._lineGap;
    }

    public float getAscenderAbs() {
        return Math.abs(_ascender);
    }

    public float getLineHeight() {
        return -_ascender + _descender + _lineGap;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FontExtents that = (FontExtents) o;
        return Float.compare(that._ascender, _ascender) == 0 &&
                Float.compare(that._descender, _descender) == 0 &&
                Float.compare(that._lineGap, _lineGap) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_ascender, _descender, _lineGap);
    }

    @Override
    public String toString() {
        return "FontExtents{" +
                "_ascender=" + _ascender +
                ", _descender=" + _descender +
                ", _lineGap=" + _lineGap +
                '}';
    }
}