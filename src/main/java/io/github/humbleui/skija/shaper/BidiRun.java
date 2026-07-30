package io.github.humbleui.skija.shaper;

import java.util.*;
import org.jetbrains.annotations.*;

public class BidiRun {
    @ApiStatus.Internal public final int _end;

    /** The unicode bidi embedding level (even ltr, odd rtl) */
    @ApiStatus.Internal public final int _level;

    public BidiRun(int _end, int _level) {
        this._end = _end;
        this._level = _level;
    }

    public int getEnd() {
        return this._end;
    }

    public int getLevel() {
        return this._level;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BidiRun bidiRun = (BidiRun) o;
        return _end == bidiRun._end && _level == bidiRun._level;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_end, _level);
    }

    @Override
    public String toString() {
        return "BidiRun{" +
                "_end=" + _end +
                ", _level=" + _level +
                '}';
    }
}