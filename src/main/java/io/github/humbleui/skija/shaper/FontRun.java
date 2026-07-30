package io.github.humbleui.skija.shaper;

import java.util.*;
import org.jetbrains.annotations.*;
import io.github.humbleui.skija.*;
import io.github.humbleui.skija.impl.*;

public class FontRun {
    @ApiStatus.Internal public final int _end;
    @ApiStatus.Internal public final Font _font;

    public FontRun(int _end, Font _font) {
        this._end = _end;
        this._font = _font;
    }

    public int getEnd() {
        return this._end;
    }

    public Font getFont() {
        return this._font;
    }

    @ApiStatus.Internal
    public long _getFontPtr() {
        try {
            return Native.getPtr(_font);
        } finally {
            ReferenceUtil.reachabilityFence(_font);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FontRun fontRun = (FontRun) o;
        return _end == fontRun._end && Objects.equals(_font, fontRun._font);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_end, _font);
    }

    @Override
    public String toString() {
        return "FontRun{" +
                "_end=" + _end +
                ", _font=" + _font +
                '}';
    }
}