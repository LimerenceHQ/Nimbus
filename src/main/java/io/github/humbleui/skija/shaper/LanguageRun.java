package io.github.humbleui.skija.shaper;

import java.util.*;
import org.jetbrains.annotations.*;

public class LanguageRun {
    @ApiStatus.Internal public final int _end;

    /** Should be BCP-47, c locale names may also work. */
    @ApiStatus.Internal public final String _language;

    public LanguageRun(int _end, String _language) {
        this._end = _end;
        this._language = _language;
    }

    public int getEnd() {
        return this._end;
    }

    public String getLanguage() {
        return this._language;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LanguageRun that = (LanguageRun) o;
        return _end == that._end && Objects.equals(_language, that._language);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_end, _language);
    }

    @Override
    public String toString() {
        return "LanguageRun{" +
                "_end=" + _end +
                ", _language='" + _language + '\'' +
                '}';
    }
}