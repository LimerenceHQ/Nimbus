package io.github.humbleui.skija;

import java.util.*;
import org.jetbrains.annotations.*;

/**
 * <p>Describes pixel and encoding. ImageInfo can be created from ColorInfo by
 * providing dimensions.</p>
 *
 * <p>It encodes how pixel bits describe alpha, transparency; color components red, blue,
 * and green; and ColorSpace, the range and linearity of colors.</p>
 */
public class ColorInfo {
    @NotNull
    public final ColorType _colorType;
    @NotNull
    public final ColorAlphaType _alphaType;
    @Nullable
    public final ColorSpace _colorSpace;

    public ColorInfo(@NotNull ColorType _colorType, @NotNull ColorAlphaType _alphaType, @Nullable ColorSpace _colorSpace) {
        this._colorType = _colorType;
        this._alphaType = _alphaType;
        this._colorSpace = _colorSpace;
    }

    public ColorType getColorType() {
        return this._colorType;
    }

    public ColorAlphaType getAlphaType() {
        return this._alphaType;
    }

    public ColorSpace getColorSpace() {
        return this._colorSpace;
    }

    public ColorInfo withColorType(@NotNull ColorType _colorType) {
        return this._colorType == _colorType ? this : new ColorInfo(_colorType, this._alphaType, this._colorSpace);
    }

    public ColorInfo withAlphaType(@NotNull ColorAlphaType _alphaType) {
        return this._alphaType == _alphaType ? this : new ColorInfo(this._colorType, _alphaType, this._colorSpace);
    }

    public ColorInfo withColorSpace(@Nullable ColorSpace _colorSpace) {
        return this._colorSpace == _colorSpace ? this : new ColorInfo(this._colorType, this._alphaType, _colorSpace);
    }

    /**
     * Creates an ColorInfo with {@link ColorType#UNKNOWN}, {@link ColorAlphaType#UNKNOWN},
     * and no ColorSpace.
     */
    public static final ColorInfo DEFAULT = new ColorInfo(ColorType.UNKNOWN, ColorAlphaType.UNKNOWN, null);

    public boolean isOpaque() {
        return _alphaType == ColorAlphaType.OPAQUE || _colorType.isAlwaysOpaque();
    }

    /**
     * Returns number of bytes per pixel required by ColorType.
     * Returns zero if getColorType() is {@link ColorType#UNKNOWN}.
     *
     * @return  bytes in pixel
     *
     * @see <a href="https://fiddle.skia.org/c/@ImageInfo_bytesPerPixel">https://fiddle.skia.org/c/@ImageInfo_bytesPerPixel</a>
     */
    public int getBytesPerPixel() {
        return _colorType.getBytesPerPixel();
    }

    /**
     * Returns bit shift converting row bytes to row pixels.
     * Returns zero for {@link ColorType#UNKNOWN}.
     *
     * @return  one of: 0, 1, 2, 3, 4; left shift to convert pixels to bytes
     *
     * @see <a href="https://fiddle.skia.org/c/@ImageInfo_shiftPerPixel">https://fiddle.skia.org/c/@ImageInfo_shiftPerPixel</a>
     */
    public int getShiftPerPixel() {
        return _colorType.getShiftPerPixel();
    }

    public boolean isGammaCloseToSRGB() {
        return _colorSpace != null && _colorSpace.isGammaCloseToSRGB();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ColorInfo colorInfo = (ColorInfo) o;
        return Objects.equals(_colorType, colorInfo._colorType) &&
                Objects.equals(_alphaType, colorInfo._alphaType) &&
                Objects.equals(_colorSpace, colorInfo._colorSpace);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_colorType, _alphaType, _colorSpace);
    }

    @Override
    public String toString() {
        return "ColorInfo{" +
                "_colorType=" + _colorType +
                ", _alphaType=" + _alphaType +
                ", _colorSpace=" + _colorSpace +
                '}';
    }
}