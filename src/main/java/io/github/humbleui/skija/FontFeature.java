package io.github.humbleui.skija;

import java.util.*;
import java.util.regex.*;

public class FontFeature {
    public final int _tag;
    public final int _value;
    public final long _start;
    public final long _end;

    public FontFeature(int _tag, int _value, long _start, long _end) {
        this._tag = _tag;
        this._value = _value;
        this._start = _start;
        this._end = _end;
    }

    public FontFeature(String feature, int value, long start, long end) {
        this(FourByteTag.fromString(feature), value, start, end);
    }

    public FontFeature(String feature, int value) {
        this(FourByteTag.fromString(feature), value, GLOBAL_START, GLOBAL_END);
    }

    public FontFeature(String feature, boolean value) {
        this(FourByteTag.fromString(feature), value ? 1 : 0, GLOBAL_START, GLOBAL_END);
    }

    public FontFeature(String feature) {
        this(FourByteTag.fromString(feature), 1, GLOBAL_START, GLOBAL_END);
    }

    public int getValue() {
        return this._value;
    }

    public long getStart() {
        return this._start;
    }

    public long getEnd() {
        return this._end;
    }

    public String getTag() {
        return FourByteTag.toString(_tag);
    }

    @Override
    public String toString() {
        String range = "";
        if (_start > 0 || _end < Long.MAX_VALUE) {
            range = "[" + (_start > 0 ? _start : "") + ":" + (_end < Long.MAX_VALUE ? _end : "") + "]";
        }
        String valuePrefix = "";
        String valueSuffix = "";
        if (_value == 0)
            valuePrefix = "-";
        else if (_value == 1)
            valuePrefix = "+";
        else
            valueSuffix = "=" + _value;
        return "FontFeature(" + valuePrefix + getTag() + range + valueSuffix + ")";
    }

    public static final long GLOBAL_START = 0;
    public static final long GLOBAL_END = Long.MAX_VALUE;
    public static final FontFeature[] EMPTY = new FontFeature[0];

    public static final Pattern _splitPattern = Pattern.compile("\\s+");
    public static final Pattern _featurePattern = Pattern.compile("(?<sign>[-+])?(?<tag>[a-z0-9]{4})(?:\\[(?<start>\\d+)?:(?<end>\\d+)?\\])?(?:=(?<value>\\d+))?");

    public static FontFeature parseOne(String s) {
        Matcher m = _featurePattern.matcher(s);
        if (!m.matches())
            throw new IllegalArgumentException("Can't parse FontFeature: " + s);
        int value = m.group("value") != null ? Integer.parseInt(m.group("value"))
                : m.group("sign") == null ? 1
                : "-".equals(m.group("sign")) ? 0
                : 1;
        long start = m.group("start") == null ? 0 : Long.parseLong(m.group("start"));
        long end = m.group("end") == null ? Long.MAX_VALUE : Long.parseLong(m.group("end"));
        return new FontFeature(m.group("tag"), value, start, end);
    }

    public static FontFeature[] parse(String s) {
        return _splitPattern.splitAsStream(s).map(FontFeature::parseOne).toArray(FontFeature[]::new);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FontFeature that = (FontFeature) o;
        return _tag == that._tag &&
                _value == that._value &&
                _start == that._start &&
                _end == that._end;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_tag, _value, _start, _end);
    }
}