package io.github.humbleui.types;

import org.jetbrains.annotations.*;
import java.util.Objects;

public class IRange {
    @ApiStatus.Internal
    public final int _start;

    @ApiStatus.Internal
    public final int _end;

    public IRange(int _start, int _end) {
        this._start = _start;
        this._end = _end;
    }

    @ApiStatus.Internal
    public static IRange _makeFromLong(long l) {
        return new IRange((int) (l >>> 32), (int) (l & 0xFFFFFFFF));
    }

    public int getStart() {
        return this._start;
    }

    public int getEnd() {
        return this._end;
    }

    public IRange withStart(int _start) {
        return this._start == _start ? this : new IRange(_start, this._end);
    }

    public IRange withEnd(int _end) {
        return this._end == _end ? this : new IRange(this._start, _end);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IRange iRange = (IRange) o;
        return _start == iRange._start && _end == iRange._end;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_start, _end);
    }

    @Override
    public String toString() {
        return "IRange(" + _start + ", " + _end + ")";
    }
}