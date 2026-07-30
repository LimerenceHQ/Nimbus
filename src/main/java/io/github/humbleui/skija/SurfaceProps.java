package io.github.humbleui.skija;

import java.util.*;
import org.jetbrains.annotations.*;

public class SurfaceProps {
    @ApiStatus.Internal
    public final boolean _deviceIndependentFonts;

    @ApiStatus.Internal
    public final PixelGeometry _pixelGeometry;

    public SurfaceProps(boolean _deviceIndependentFonts, PixelGeometry _pixelGeometry) {
        this._deviceIndependentFonts = _deviceIndependentFonts;
        this._pixelGeometry = _pixelGeometry;
    }

    public boolean isDeviceIndependentFonts() {
        return this._deviceIndependentFonts;
    }

    public PixelGeometry getPixelGeometry() {
        return this._pixelGeometry;
    }

    public SurfaceProps withDeviceIndependentFonts(boolean _deviceIndependentFonts) {
        return this._deviceIndependentFonts == _deviceIndependentFonts ? this : new SurfaceProps(_deviceIndependentFonts, this._pixelGeometry);
    }

    public SurfaceProps withPixelGeometry(PixelGeometry _pixelGeometry) {
        return this._pixelGeometry == _pixelGeometry ? this : new SurfaceProps(this._deviceIndependentFonts, _pixelGeometry);
    }

    @ApiStatus.Internal
    public SurfaceProps(boolean deviceIndependentFonts, int pixelGeometry) {
        this(deviceIndependentFonts, PixelGeometry.values()[pixelGeometry]);
    }

    public SurfaceProps() {
        this(false, PixelGeometry.UNKNOWN);
    }

    public SurfaceProps(PixelGeometry geo) {
        this(false, geo);
    }

    @ApiStatus.Internal
    public int _getFlags() {
        return 0 | (_deviceIndependentFonts ? 1 : 0);
    }

    @ApiStatus.Internal
    public int _getPixelGeometryOrdinal() {
        return _pixelGeometry.ordinal();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SurfaceProps that = (SurfaceProps) o;
        return _deviceIndependentFonts == that._deviceIndependentFonts &&
                _pixelGeometry == that._pixelGeometry;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_deviceIndependentFonts, _pixelGeometry);
    }

    @Override
    public String toString() {
        return "SurfaceProps{" +
                "_deviceIndependentFonts=" + _deviceIndependentFonts +
                ", _pixelGeometry=" + _pixelGeometry +
                '}';
    }
}