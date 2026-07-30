package io.github.humbleui.skija;

import java.util.*;
import org.jetbrains.annotations.*;

public class FontVariationAxis {
    @ApiStatus.Internal public final int _tag;
    @ApiStatus.Internal public final float _minValue;
    @ApiStatus.Internal public final float _defaultValue;
    @ApiStatus.Internal public final float _maxValue;
    @ApiStatus.Internal public final boolean _hidden;

    public FontVariationAxis(int _tag, float _minValue, float _defaultValue, float _maxValue, boolean _hidden) {
        this._tag = _tag;
        this._minValue = _minValue;
        this._defaultValue = _defaultValue;
        this._maxValue = _maxValue;
        this._hidden = _hidden;
    }

    public String getTag() {
        return FourByteTag.toString(_tag);
    }

    public float getMinValue() {
        return this._minValue;
    }

    public float getDefaultValue() {
        return this._defaultValue;
    }

    public float getMaxValue() {
        return this._maxValue;
    }

    public boolean isHidden() {
        return this._hidden;
    }

    public FontVariationAxis(String tag, float min, float def, float max, boolean hidden) {
        this(FourByteTag.fromString(tag), min, def, max, hidden);
    }

    public FontVariationAxis(String tag, float min, float def, float max) {
        this(FourByteTag.fromString(tag), min, def, max, false);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FontVariationAxis that = (FontVariationAxis) o;
        return _tag == that._tag &&
                Float.compare(that._minValue, _minValue) == 0 &&
                Float.compare(that._defaultValue, _defaultValue) == 0 &&
                Float.compare(that._maxValue, _maxValue) == 0 &&
                _hidden == that._hidden;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_tag, _minValue, _defaultValue, _maxValue, _hidden);
    }

    @Override
    public String toString() {
        return "FontVariationAxis{" +
                "tag='" + getTag() + '\'' +
                ", minValue=" + _minValue +
                ", defaultValue=" + _defaultValue +
                ", maxValue=" + _maxValue +
                ", hidden=" + _hidden +
                '}';
    }
}