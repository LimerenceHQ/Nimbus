package io.github.humbleui.skija.shaper;

import java.util.*;
import io.github.humbleui.skija.*;
import io.github.humbleui.types.*;

public class RunInfo {
    public       long  _fontPtr;
    public final int   _bidiLevel;
    public final float _advanceX;
    public final float _advanceY;
    public final long  _glyphCount;
    /** WARN does not work in Shaper.makeCoreText https://bugs.chromium.org/p/skia/issues/detail?id=10899 */
    public final int  _rangeBegin;
    /** WARN does not work in Shaper.makeCoreText https://bugs.chromium.org/p/skia/issues/detail?id=10899 */
    public final int  _rangeSize;

    public RunInfo(long fontPtr, int biDiLevel, float advanceX, float advanceY, long glyphCount, int rangeBegin, int rangeSize) {
        _fontPtr = fontPtr;
        _bidiLevel = biDiLevel;
        _advanceX = advanceX;
        _advanceY = advanceY;
        _glyphCount = glyphCount;
        _rangeBegin = rangeBegin;
        _rangeSize = rangeSize;
    }

    public int getBidiLevel() {
        return this._bidiLevel;
    }

    public float getAdvanceX() {
        return this._advanceX;
    }

    public float getAdvanceY() {
        return this._advanceY;
    }

    public long getGlyphCount() {
        return this._glyphCount;
    }

    public int getRangeBegin() {
        return this._rangeBegin;
    }

    public int getRangeSize() {
        return this._rangeSize;
    }

    public Point getAdvance() {
        return new Point(_advanceX, _advanceY);
    }

    /** WARN does not work in Shaper.makeCoreText https://bugs.chromium.org/p/skia/issues/detail?id=10899 */
    public int getRangeEnd() {
        return _rangeBegin + _rangeSize;
    }

    public Font getFont() {
        if (_fontPtr == 0)
            throw new IllegalStateException("getFont() is only valid inside RunHandler callbacks");
        return Font.makeClone(_fontPtr);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RunInfo runInfo = (RunInfo) o;
        return _bidiLevel == runInfo._bidiLevel &&
                Float.compare(runInfo._advanceX, _advanceX) == 0 &&
                Float.compare(runInfo._advanceY, _advanceY) == 0 &&
                _glyphCount == runInfo._glyphCount &&
                _rangeBegin == runInfo._rangeBegin &&
                _rangeSize == runInfo._rangeSize;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_bidiLevel, _advanceX, _advanceY, _glyphCount, _rangeBegin, _rangeSize);
    }

    @Override
    public String toString() {
        return "RunInfo{" +
                "_bidiLevel=" + _bidiLevel +
                ", _advanceX=" + _advanceX +
                ", _advanceY=" + _advanceY +
                ", _glyphCount=" + _glyphCount +
                ", _rangeBegin=" + _rangeBegin +
                ", _rangeSize=" + _rangeSize +
                '}';
    }
}