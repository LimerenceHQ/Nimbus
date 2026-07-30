package io.github.humbleui.skija.shaper;

import java.util.*;
import org.jetbrains.annotations.*;
import io.github.humbleui.skija.*;

public class ScriptRun {
    @ApiStatus.Internal public final int _end;
    @ApiStatus.Internal public final int _scriptTag;

    public ScriptRun(int _end, int _scriptTag) {
        this._end = _end;
        this._scriptTag = _scriptTag;
    }

    public ScriptRun(int end, String script) {
        this(end, FourByteTag.fromString(script));
    }

    public int getEnd() {
        return this._end;
    }

    public int getScriptTag() {
        return this._scriptTag;
    }

    /** Should be iso15924 codes. */
    public String getScript() {
        return FourByteTag.toString(_scriptTag);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScriptRun scriptRun = (ScriptRun) o;
        return _end == scriptRun._end && _scriptTag == scriptRun._scriptTag;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_end, _scriptTag);
    }

    @Override
    public String toString() {
        return "ScriptRun{" +
                "_end=" + _end +
                ", _scriptTag=" + _scriptTag +
                ", _script='" + getScript() + '\'' +
                '}';
    }
}