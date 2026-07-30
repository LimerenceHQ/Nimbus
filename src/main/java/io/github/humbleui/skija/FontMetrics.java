package io.github.humbleui.skija;

import java.util.*;
import org.jetbrains.annotations.*;

public class FontMetrics {
    /**
     * greatest extent above origin of any glyph bounding box, typically negative; deprecated with variable fonts
     */
    public final float _top;

    /**
     * distance to reserve above baseline, typically negative
     */
    public final float _ascent;

    /**
     * distance to reserve below baseline, typically positive
     */
    public final float _descent;

    /**
     * greatest extent below origin of any glyph bounding box, typically positive; deprecated with variable fonts
     */
    public final float _bottom;

    /**
     * distance to add between lines, typically positive or zero
     */
    public final float _leading;

    /**
     * average character width, zero if unknown
     */
    public final float _avgCharWidth;

    /**
     * maximum character width, zero if unknown
     */
    public final float _maxCharWidth;

    /**
     * greatest extent to left of origin of any glyph bounding box, typically negative; deprecated with variable fonts
     */
    public final float _xMin;

    /**
     * greatest extent to right of origin of any glyph bounding box, typically positive; deprecated with variable fonts
     */
    public final float _xMax;

    /**
     * height of lower-case 'x', zero if unknown, typically negative
     */
    public final float _xHeight;

    /**
     * height of an upper-case letter, zero if unknown, typically negative
     */
    public final float _capHeight;

    /**
     * underline thickness
     */
    @Nullable
    public final Float _underlineThickness;

    /**
     * distance from baseline to top of stroke, typically positive
     */
    @Nullable
    public final Float _underlinePosition;

    /**
     * strikeout thickness
     */
    @Nullable
    public final Float _strikeoutThickness;

    /**
     * distance from baseline to bottom of stroke, typically negative
     */
    @Nullable
    public final Float _strikeoutPosition;

    public FontMetrics(float _top, float _ascent, float _descent, float _bottom, float _leading,
                       float _avgCharWidth, float _maxCharWidth, float _xMin, float _xMax,
                       float _xHeight, float _capHeight, @Nullable Float _underlineThickness,
                       @Nullable Float _underlinePosition, @Nullable Float _strikeoutThickness,
                       @Nullable Float _strikeoutPosition) {
        this._top = _top;
        this._ascent = _ascent;
        this._descent = _descent;
        this._bottom = _bottom;
        this._leading = _leading;
        this._avgCharWidth = _avgCharWidth;
        this._maxCharWidth = _maxCharWidth;
        this._xMin = _xMin;
        this._xMax = _xMax;
        this._xHeight = _xHeight;
        this._capHeight = _capHeight;
        this._underlineThickness = _underlineThickness;
        this._underlinePosition = _underlinePosition;
        this._strikeoutThickness = _strikeoutThickness;
        this._strikeoutPosition = _strikeoutPosition;
    }

    public float getTop() {
        return this._top;
    }

    public float getAscent() {
        return this._ascent;
    }

    public float getDescent() {
        return this._descent;
    }

    public float getBottom() {
        return this._bottom;
    }

    public float getLeading() {
        return this._leading;
    }

    public float getAvgCharWidth() {
        return this._avgCharWidth;
    }

    public float getMaxCharWidth() {
        return this._maxCharWidth;
    }

    public float getXMin() {
        return this._xMin;
    }

    public float getXMax() {
        return this._xMax;
    }

    public float getXHeight() {
        return this._xHeight;
    }

    public float getCapHeight() {
        return this._capHeight;
    }

    @Nullable
    public Float getUnderlineThickness() {
        return this._underlineThickness;
    }

    @Nullable
    public Float getUnderlinePosition() {
        return this._underlinePosition;
    }

    @Nullable
    public Float getStrikeoutThickness() {
        return this._strikeoutThickness;
    }

    @Nullable
    public Float getStrikeoutPosition() {
        return this._strikeoutPosition;
    }

    public float getHeight() {
        return _descent - _ascent;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FontMetrics that = (FontMetrics) o;
        return Float.compare(that._top, _top) == 0 &&
                Float.compare(that._ascent, _ascent) == 0 &&
                Float.compare(that._descent, _descent) == 0 &&
                Float.compare(that._bottom, _bottom) == 0 &&
                Float.compare(that._leading, _leading) == 0 &&
                Float.compare(that._avgCharWidth, _avgCharWidth) == 0 &&
                Float.compare(that._maxCharWidth, _maxCharWidth) == 0 &&
                Float.compare(that._xMin, _xMin) == 0 &&
                Float.compare(that._xMax, _xMax) == 0 &&
                Float.compare(that._xHeight, _xHeight) == 0 &&
                Float.compare(that._capHeight, _capHeight) == 0 &&
                Objects.equals(_underlineThickness, that._underlineThickness) &&
                Objects.equals(_underlinePosition, that._underlinePosition) &&
                Objects.equals(_strikeoutThickness, that._strikeoutThickness) &&
                Objects.equals(_strikeoutPosition, that._strikeoutPosition);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_top, _ascent, _descent, _bottom, _leading, _avgCharWidth, _maxCharWidth,
                _xMin, _xMax, _xHeight, _capHeight, _underlineThickness, _underlinePosition,
                _strikeoutThickness, _strikeoutPosition);
    }

    @Override
    public String toString() {
        return "FontMetrics{" +
                "_top=" + _top +
                ", _ascent=" + _ascent +
                ", _descent=" + _descent +
                ", _bottom=" + _bottom +
                ", _leading=" + _leading +
                ", _avgCharWidth=" + _avgCharWidth +
                ", _maxCharWidth=" + _maxCharWidth +
                ", _xMin=" + _xMin +
                ", _xMax=" + _xMax +
                ", _xHeight=" + _xHeight +
                ", _capHeight=" + _capHeight +
                ", _underlineThickness=" + _underlineThickness +
                ", _underlinePosition=" + _underlinePosition +
                ", _strikeoutThickness=" + _strikeoutThickness +
                ", _strikeoutPosition=" + _strikeoutPosition +
                '}';
    }
}