package io.github.humbleui.skija.paragraph;

import java.util.*;

public class PlaceholderStyle {
    public final float _width;
    public final float _height;
    public final PlaceholderAlignment _alignment;
    public final BaselineMode _baselineMode;

    /**
     * <p>Distance from the top edge of the rect to the baseline position. This
     * baseline will be aligned against the alphabetic baseline of the surrounding
     * text.</p>
     *
     * <p>Positive values drop the baseline lower (positions the rect higher) and
     * small or negative values will cause the rect to be positioned underneath
     * the line. When baseline == height, the bottom edge of the rect will rest on
     * the alphabetic baseline.</p>
     */
    public final float _baseline;

    public PlaceholderStyle(float _width, float _height, PlaceholderAlignment _alignment, BaselineMode _baselineMode, float _baseline) {
        this._width = _width;
        this._height = _height;
        this._alignment = _alignment;
        this._baselineMode = _baselineMode;
        this._baseline = _baseline;
    }

    public float getWidth() {
        return this._width;
    }

    public float getHeight() {
        return this._height;
    }

    public PlaceholderAlignment getAlignment() {
        return this._alignment;
    }

    public BaselineMode getBaselineMode() {
        return this._baselineMode;
    }

    public float getBaseline() {
        return this._baseline;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlaceholderStyle that = (PlaceholderStyle) o;
        return Float.compare(that._width, _width) == 0 &&
                Float.compare(that._height, _height) == 0 &&
                Float.compare(that._baseline, _baseline) == 0 &&
                _alignment == that._alignment &&
                _baselineMode == that._baselineMode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_width, _height, _alignment, _baselineMode, _baseline);
    }

    @Override
    public String toString() {
        return "PlaceholderStyle{" +
                "_width=" + _width +
                ", _height=" + _height +
                ", _alignment=" + _alignment +
                ", _baselineMode=" + _baselineMode +
                ", _baseline=" + _baseline +
                '}';
    }
}