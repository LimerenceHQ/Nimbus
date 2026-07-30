package io.github.humbleui.skija;

import java.util.*;
import org.jetbrains.annotations.*;
import io.github.humbleui.skija.impl.*;
import io.github.humbleui.types.*;

/**
 * <p>Describes pixel dimensions and encoding. Bitmap, Image, Pixmap, and Surface
 * can be created from ImageInfo. ImageInfo can be retrieved from Bitmap and
 * Pixmap, but not from Image and Surface. For example, Image and Surface
 * implementations may defer pixel depth, so may not completely specify ImageInfo.</p>
 *
 * <p>ImageInfo contains dimensions, the pixel integral width and height. It encodes
 * how pixel bits describe alpha, transparency; color components red, blue,
 * and green; and ColorSpace, the range and linearity of colors.</p>
 */
public class ImageInfo {
    public final ColorInfo _colorInfo;
    public final int _width;
    public final int _height;

    public ImageInfo(ColorInfo _colorInfo, int _width, int _height) {
        this._colorInfo = _colorInfo;
        this._width = _width;
        this._height = _height;
    }

    public ColorInfo getColorInfo() {
        return this._colorInfo;
    }

    public int getWidth() {
        return this._width;
    }

    public int getHeight() {
        return this._height;
    }

    public ImageInfo withColorInfo(ColorInfo _colorInfo) {
        return this._colorInfo == _colorInfo ? this : new ImageInfo(_colorInfo, this._width, this._height);
    }

    public ImageInfo withWidth(int _width) {
        return this._width == _width ? this : new ImageInfo(this._colorInfo, _width, this._height);
    }

    public ImageInfo withHeight(int _height) {
        return this._height == _height ? this : new ImageInfo(this._colorInfo, this._width, _height);
    }

    public static final ImageInfo DEFAULT = new ImageInfo(ColorInfo.DEFAULT, 0, 0);

    public ImageInfo(int width, int height, @NotNull ColorType colorType, @NotNull ColorAlphaType alphaType) {
        this(new ColorInfo(colorType, alphaType, null), width, height);
    }

    public ImageInfo(int width, int height, @NotNull ColorType colorType, @NotNull ColorAlphaType alphaType, @Nullable ColorSpace colorSpace) {
        this(new ColorInfo(colorType, alphaType, colorSpace), width, height);
    }

    @ApiStatus.Internal
    public ImageInfo(int width, int height, int colorType, int alphaType, long colorSpace) {
        this(width, height, ColorType._values[colorType], ColorAlphaType._values[alphaType], colorSpace == 0 ? null : new ColorSpace(colorSpace));
    }

    /**
     * @return  ImageInfo with {@link ColorType#N32}
     */
    @NotNull
    public static ImageInfo makeN32(int width, int height, @NotNull ColorAlphaType alphaType) {
        return new ImageInfo(new ColorInfo(ColorType.N32, alphaType, null), width, height);
    }

    /**
     * @return  ImageInfo with {@link ColorType#N32}
     */
    @NotNull
    public static ImageInfo makeN32(int width, int height, @NotNull ColorAlphaType alphaType, @Nullable ColorSpace colorSpace) {
        return new ImageInfo(new ColorInfo(ColorType.N32, alphaType, colorSpace), width, height);
    }

    /**
     * @return  ImageInfo with {@link ColorType#N32} and {@link ColorSpace#getSRGB()}
     *
     * @see     <a href="https://fiddle.skia.org/c/@ImageInfo_MakeS32">https://fiddle.skia.org/c/@ImageInfo_MakeS32</a>
     */
    @NotNull
    public static ImageInfo makeS32(int width, int height, @NotNull ColorAlphaType alphaType) {
        return new ImageInfo(new ColorInfo(ColorType.N32, alphaType, ColorSpace.getSRGB()), width, height);
    }

    /**
     * @return  ImageInfo with {@link ColorType#N32} and {@link ColorAlphaType#PREMUL}
     */
    @NotNull
    public static ImageInfo makeN32Premul(int width, int height) {
        return new ImageInfo(new ColorInfo(ColorType.N32, ColorAlphaType.PREMUL, null), width, height);
    }

    /**
     * @return  ImageInfo with {@link ColorType#N32} and {@link ColorAlphaType#PREMUL}
     */
    @NotNull
    public static ImageInfo makeN32Premul(int width, int height, @Nullable ColorSpace colorSpace) {
        return new ImageInfo(new ColorInfo(ColorType.N32, ColorAlphaType.PREMUL, colorSpace), width, height);
    }

    /**
     * @return  ImageInfo with {@link ColorType#ALPHA_8} and {@link ColorAlphaType#PREMUL}
     */
    @NotNull
    public static ImageInfo makeA8(int width, int height) {
        return new ImageInfo(new ColorInfo(ColorType.ALPHA_8, ColorAlphaType.PREMUL, null), width, height);
    }

    /**
     * @return  ImageInfo with {@link ColorType#UNKNOWN} and {@link ColorAlphaType#UNKNOWN}
     */
    @NotNull
    public static ImageInfo makeUnknown(int width, int height) {
        return new ImageInfo(new ColorInfo(ColorType.UNKNOWN, ColorAlphaType.UNKNOWN, null), width, height);
    }

    @NotNull
    public ColorType getColorType() {
        return _colorInfo.getColorType();
    }

    @NotNull
    public ImageInfo withColorType(@NotNull ColorType colorType) {
        return withColorInfo(_colorInfo.withColorType(colorType));
    }

    @NotNull
    public ColorAlphaType getColorAlphaType() {
        return _colorInfo.getAlphaType();
    }

    @NotNull
    public ImageInfo withColorAlphaType(@NotNull ColorAlphaType alphaType) {
        return withColorInfo(_colorInfo.withAlphaType(alphaType));
    }

    @Nullable
    public ColorSpace getColorSpace() {
        return _colorInfo.getColorSpace();
    }

    @NotNull
    public ImageInfo withColorSpace(@NotNull ColorSpace colorSpace) {
        return withColorInfo(_colorInfo.withColorSpace(colorSpace));
    }

    /**
     * @return  true if either dimension is zero or smaller
     */
    public boolean isEmpty() {
        return _width <= 0 || _height <= 0;
    }

    /**
     * <p>Returns true if ColorAlphaType is set to hint that all pixels are opaque; their
     * alpha value is implicitly or explicitly 1.0. If true, and all pixels are
     * not opaque, Skia may draw incorrectly.</p>
     *
     * <p>Does not check if ColorType allows alpha, or if any pixel value has
     * transparency.</p>
     *
     * @return  true if alphaType is {@link ColorAlphaType#OPAQUE}
     */
    public boolean isOpaque() {
        return _colorInfo.isOpaque();
    }

    /**
     * @return  integral rectangle from (0, 0) to (getWidth(), getHeight())
     */
    @NotNull
    public IRect getBounds() {
        return IRect.makeXYWH(0, 0, _width, _height);
    }

    /**
     * @return  true if associated ColorSpace is not null, and ColorSpace gamma
     *          is approximately the same as sRGB.
     */
    public boolean isGammaCloseToSRGB() {
        return _colorInfo.isGammaCloseToSRGB();
    }

    @NotNull
    public ImageInfo withWidthHeight(int width, int height) {
        return new ImageInfo(_colorInfo, width, height);
    }

    /**
     * Returns number of bytes per pixel required by ColorType.
     * Returns zero if {@link #getColorType()} is {@link ColorType#UNKNOWN}.
     *
     * @return  bytes in pixel
     */
    public int getBytesPerPixel() {
        return _colorInfo.getBytesPerPixel();
    }

    /**
     * Returns bit shift converting row bytes to row pixels.
     * Returns zero for {@link ColorType#UNKNOWN}.
     *
     * @return  one of: 0, 1, 2, 3, 4; left shift to convert pixels to bytes
     */
    public int getShiftPerPixel() {
        return _colorInfo.getShiftPerPixel();
    }

    /**
     * Returns minimum bytes per row, computed from pixel getWidth() and ColorType, which
     * specifies getBytesPerPixel(). Bitmap maximum value for row bytes must fit
     * in 31 bits.
     */
    public long getMinRowBytes() {
        return _width * getBytesPerPixel();
    }

    /**
     * <p>Returns byte offset of pixel from pixel base address.</p>
     *
     * <p>Asserts in debug build if x or y is outside of bounds. Does not assert if
     * rowBytes is smaller than {@link #getMinRowBytes()}, even though result may be incorrect.</p>
     *
     * @param x         column index, zero or greater, and less than getWidth()
     * @param y         row index, zero or greater, and less than getHeight()
     * @param rowBytes  size of pixel row or larger
     * @return          offset within pixel array
     *
     * @see  <a href="https://fiddle.skia.org/c/@ImageInfo_computeOffset">https://fiddle.skia.org/c/@ImageInfo_computeOffset</a>
     */
    public long computeOffset(int x, int y, long rowBytes) {
        return _colorInfo._colorType.computeOffset(x, y, rowBytes);
    }

    /**
     * <p>Returns storage required by pixel array, given ImageInfo dimensions, ColorType,
     * and rowBytes. rowBytes is assumed to be at least as large as {@link #getMinRowBytes()}.</p>
     *
     * <p>Returns zero if height is zero.</p>
     *
     * @param rowBytes  size of pixel row or larger
     * @return          memory required by pixel buffer
     *
     * @see <a href="https://fiddle.skia.org/c/@ImageInfo_computeByteSize">https://fiddle.skia.org/c/@ImageInfo_computeByteSize</a>
     */
    public long computeByteSize(long rowBytes) {
        if (0 == _height) return 0;
        return (_height - 1) * rowBytes + _width * getBytesPerPixel();
    }

    /**
     * <p>Returns storage required by pixel array, given ImageInfo dimensions, and
     * ColorType. Uses {@link #getMinRowBytes()} to compute bytes for pixel row.</p>
     *
     * Returns zero if height is zero.
     *
     * @return  least memory required by pixel buffer
     */
    public long computeMinByteSize() {
        return computeByteSize(getMinRowBytes());
    }

    /**
     * Returns true if rowBytes is valid for this ImageInfo.
     *
     * @param rowBytes  size of pixel row including padding
     * @return          true if rowBytes is large enough to contain pixel row and is properly aligned
     */
    public boolean isRowBytesValid(long rowBytes) {
        if (rowBytes < getMinRowBytes())
            return false;
        int shift = getShiftPerPixel();
        return rowBytes >> shift << shift == rowBytes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ImageInfo imageInfo = (ImageInfo) o;
        return _width == imageInfo._width &&
                _height == imageInfo._height &&
                Objects.equals(_colorInfo, imageInfo._colorInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_colorInfo, _width, _height);
    }

    @Override
    public String toString() {
        return "ImageInfo{" +
                "_colorInfo=" + _colorInfo +
                ", _width=" + _width +
                ", _height=" + _height +
                '}';
    }
}