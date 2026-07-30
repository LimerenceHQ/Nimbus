package io.github.humbleui.skija.paragraph;

import java.util.*;
import org.jetbrains.annotations.*;

public class DecorationStyle {
    public static final DecorationStyle NONE = new DecorationStyle(false, false, false, true, 0xFF000000, DecorationLineStyle.SOLID, 1f);

    public final boolean _underline;
    public final boolean _overline;
    public final boolean _lineThrough;
    public final boolean _gaps;
    public final int _color;
    public final DecorationLineStyle _lineStyle;
    public final float _thicknessMultiplier;

    public DecorationStyle(boolean _underline, boolean _overline, boolean _lineThrough, boolean _gaps, int _color, DecorationLineStyle _lineStyle, float _thicknessMultiplier) {
        this._underline = _underline;
        this._overline = _overline;
        this._lineThrough = _lineThrough;
        this._gaps = _gaps;
        this._color = _color;
        this._lineStyle = _lineStyle;
        this._thicknessMultiplier = _thicknessMultiplier;
    }

    @ApiStatus.Internal
    public DecorationStyle(boolean underline, boolean overline, boolean lineThrough, boolean gaps, int color, int lineStyle, float thicknessMultiplier) {
        this(underline, overline, lineThrough, gaps, color, DecorationLineStyle._values[lineStyle], thicknessMultiplier);
    }

    public boolean hasUnderline() {
        return _underline;
    }

    public boolean hasOverline() {
        return _overline;
    }

    public boolean hasLineThrough() {
        return _lineThrough;
    }

    public boolean hasGaps() {
        return _gaps;
    }

    public int getColor() {
        return this._color;
    }

    public DecorationLineStyle getLineStyle() {
        return this._lineStyle;
    }

    public float getThicknessMultiplier() {
        return this._thicknessMultiplier;
    }

    public DecorationStyle withUnderline(boolean _underline) {
        return this._underline == _underline ? this : new DecorationStyle(_underline, this._overline, this._lineThrough, this._gaps, this._color, this._lineStyle, this._thicknessMultiplier);
    }

    public DecorationStyle withOverline(boolean _overline) {
        return this._overline == _overline ? this : new DecorationStyle(this._underline, _overline, this._lineThrough, this._gaps, this._color, this._lineStyle, this._thicknessMultiplier);
    }

    public DecorationStyle withLineThrough(boolean _lineThrough) {
        return this._lineThrough == _lineThrough ? this : new DecorationStyle(this._underline, this._overline, _lineThrough, this._gaps, this._color, this._lineStyle, this._thicknessMultiplier);
    }

    public DecorationStyle withGaps(boolean _gaps) {
        return this._gaps == _gaps ? this : new DecorationStyle(this._underline, this._overline, this._lineThrough, _gaps, this._color, this._lineStyle, this._thicknessMultiplier);
    }

    public DecorationStyle withColor(int _color) {
        return this._color == _color ? this : new DecorationStyle(this._underline, this._overline, this._lineThrough, this._gaps, _color, this._lineStyle, this._thicknessMultiplier);
    }

    public DecorationStyle withLineStyle(DecorationLineStyle _lineStyle) {
        return this._lineStyle == _lineStyle ? this : new DecorationStyle(this._underline, this._overline, this._lineThrough, this._gaps, this._color, _lineStyle, this._thicknessMultiplier);
    }

    public DecorationStyle withThicknessMultiplier(float _thicknessMultiplier) {
        return this._thicknessMultiplier == _thicknessMultiplier ? this : new DecorationStyle(this._underline, this._overline, this._lineThrough, this._gaps, this._color, this._lineStyle, _thicknessMultiplier);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DecorationStyle that = (DecorationStyle) o;
        return _underline == that._underline &&
                _overline == that._overline &&
                _lineThrough == that._lineThrough &&
                _gaps == that._gaps &&
                _color == that._color &&
                Float.compare(that._thicknessMultiplier, _thicknessMultiplier) == 0 &&
                _lineStyle == that._lineStyle;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_underline, _overline, _lineThrough, _gaps, _color, _lineStyle, _thicknessMultiplier);
    }

    @Override
    public String toString() {
        return "DecorationStyle{" +
                "_underline=" + _underline +
                ", _overline=" + _overline +
                ", _lineThrough=" + _lineThrough +
                ", _gaps=" + _gaps +
                ", _color=" + _color +
                ", _lineStyle=" + _lineStyle +
                ", _thicknessMultiplier=" + _thicknessMultiplier +
                '}';
    }
}