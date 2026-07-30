package io.github.humbleui.skija.svg;

import java.util.*;
import org.jetbrains.annotations.*;

public class SVGLength {
    @ApiStatus.Internal public final float _value;
    @ApiStatus.Internal public final SVGLengthUnit _unit;

    public SVGLength(float _value, SVGLengthUnit _unit) {
        this._value = _value;
        this._unit = _unit;
    }

    @ApiStatus.Internal
    public SVGLength(float value, int unit) {
        this(value, SVGLengthUnit._values[unit]);
    }

    public SVGLength(float value) {
        this(value, SVGLengthUnit.NUMBER);
    }

    public float getValue() {
        return this._value;
    }

    public SVGLengthUnit getUnit() {
        return this._unit;
    }

    public SVGLength withValue(float _value) {
        return this._value == _value ? this : new SVGLength(_value, this._unit);
    }

    public SVGLength withUnit(SVGLengthUnit _unit) {
        return this._unit == _unit ? this : new SVGLength(this._value, _unit);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SVGLength svgLength = (SVGLength) o;
        return Float.compare(svgLength._value, _value) == 0 && _unit == svgLength._unit;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_value, _unit);
    }

    @Override
    public String toString() {
        return "SVGLength{" +
                "_value=" + _value +
                ", _unit=" + _unit +
                '}';
    }
}