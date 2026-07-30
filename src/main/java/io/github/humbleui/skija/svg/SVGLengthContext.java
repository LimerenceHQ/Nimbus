package io.github.humbleui.skija.svg;

import java.util.*;
import org.jetbrains.annotations.*;
import io.github.humbleui.skija.*;
import io.github.humbleui.types.*;

public class SVGLengthContext {
    @ApiStatus.Internal public final float _width;
    @ApiStatus.Internal public final float _height;
    @ApiStatus.Internal public final float _dpi;

    public SVGLengthContext(float _width, float _height, float _dpi) {
        this._width = _width;
        this._height = _height;
        this._dpi = _dpi;
    }

    public SVGLengthContext(float width, float height) {
        this(width, height, 90);
    }

    public SVGLengthContext(@NotNull Point size) {
        this(size._x, size._y, 90);
    }

    public float getWidth() {
        return this._width;
    }

    public float getHeight() {
        return this._height;
    }

    public float getDpi() {
        return this._dpi;
    }

    public SVGLengthContext withWidth(float _width) {
        return this._width == _width ? this : new SVGLengthContext(_width, this._height, this._dpi);
    }

    public SVGLengthContext withHeight(float _height) {
        return this._height == _height ? this : new SVGLengthContext(this._width, _height, this._dpi);
    }

    public SVGLengthContext withDpi(float _dpi) {
        return this._dpi == _dpi ? this : new SVGLengthContext(this._width, this._height, _dpi);
    }

    public float resolve(@NotNull SVGLength length, @NotNull SVGLengthType type) {
        switch (length.getUnit()) {
            case NUMBER:
                return length.getValue();
            case PX:
                return length.getValue();
            case PERCENTAGE:
                switch (type) {
                    case HORIZONTAL:
                        return length.getValue() * _width / 100f;
                    case VERTICAL:
                        return length.getValue() * _height / 100f;
                    case OTHER:
                        // https://www.w3.org/TR/SVG11/coords.html#Units_viewport_percentage
                        return (float) (length.getValue() * Math.hypot(_width, _height) / Math.sqrt(2.0) / 100.0);
                }
            case CM:
                return length.getValue() * _dpi / 2.54f;
            case MM:
                return length.getValue() * _dpi / 25.4f;
            case IN:
                return length.getValue() * _dpi;
            case PT:
                return length.getValue() * _dpi / 72.272f;
            case PC:
                return length.getValue() * _dpi * 12f / 72.272f;
            default:
                throw new IllegalArgumentException("Unknown SVGLengthUnit: " + length.getUnit());
        }
    }

    @NotNull
    public Rect resolveRect(@NotNull SVGLength x, @NotNull SVGLength y, @NotNull SVGLength width, @NotNull SVGLength height) {
        return Rect.makeXYWH(resolve(x, SVGLengthType.HORIZONTAL),
                resolve(y, SVGLengthType.VERTICAL),
                resolve(width, SVGLengthType.HORIZONTAL),
                resolve(height, SVGLengthType.VERTICAL));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SVGLengthContext that = (SVGLengthContext) o;
        return Float.compare(that._width, _width) == 0 &&
                Float.compare(that._height, _height) == 0 &&
                Float.compare(that._dpi, _dpi) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_width, _height, _dpi);
    }

    @Override
    public String toString() {
        return "SVGLengthContext{" +
                "_width=" + _width +
                ", _height=" + _height +
                ", _dpi=" + _dpi +
                '}';
    }
}