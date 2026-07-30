package io.github.humbleui.skija.svg;

import java.util.*;
import org.jetbrains.annotations.*;

public class SVGPreserveAspectRatio {
    @ApiStatus.Internal public final SVGPreserveAspectRatioAlign _align;
    @ApiStatus.Internal public final SVGPreserveAspectRatioScale _scale;

    public SVGPreserveAspectRatio(SVGPreserveAspectRatioAlign _align, SVGPreserveAspectRatioScale _scale) {
        this._align = _align;
        this._scale = _scale;
    }

    @ApiStatus.Internal
    public SVGPreserveAspectRatio(int align, int scale) {
        this(SVGPreserveAspectRatioAlign.valueOf(align), SVGPreserveAspectRatioScale._values[scale]);
    }

    public SVGPreserveAspectRatio() {
        this(SVGPreserveAspectRatioAlign.XMID_YMID, SVGPreserveAspectRatioScale.MEET);
    }

    public SVGPreserveAspectRatio(SVGPreserveAspectRatioAlign align) {
        this(align, SVGPreserveAspectRatioScale.MEET);
    }

    public SVGPreserveAspectRatio(SVGPreserveAspectRatioScale scale) {
        this(SVGPreserveAspectRatioAlign.XMID_YMID, scale);
    }

    public SVGPreserveAspectRatioAlign getAlign() {
        return this._align;
    }

    public SVGPreserveAspectRatioScale getScale() {
        return this._scale;
    }

    public SVGPreserveAspectRatio withAlign(SVGPreserveAspectRatioAlign _align) {
        return this._align == _align ? this : new SVGPreserveAspectRatio(_align, this._scale);
    }

    public SVGPreserveAspectRatio withScale(SVGPreserveAspectRatioScale _scale) {
        return this._scale == _scale ? this : new SVGPreserveAspectRatio(this._align, _scale);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SVGPreserveAspectRatio that = (SVGPreserveAspectRatio) o;
        return _align == that._align && _scale == that._scale;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_align, _scale);
    }

    @Override
    public String toString() {
        return "SVGPreserveAspectRatio{" +
                "_align=" + _align +
                ", _scale=" + _scale +
                '}';
    }
}