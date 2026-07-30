package io.github.humbleui.skija;

import java.util.*;
import java.util.regex.*;
import org.jetbrains.annotations.*;

public class FontVariation {
    public static final FontVariation[] EMPTY = new FontVariation[0];

    public final int _tag;
    public final float _value;

    public FontVariation(int _tag, float _value) {
        this._tag = _tag;
        this._value = _value;
    }

    public FontVariation(String feature, float value) {
        this(FourByteTag.fromString(feature), value);
    }

    public int getTag() {
        return this._tag;
    }

    public float getValue() {
        return this._value;
    }

    public String getTagString() {
        return FourByteTag.toString(_tag);
    }

    @Override
    public String toString() {
        return getTagString() + "=" + _value;
    }

    @ApiStatus.Internal
    public static final Pattern _splitPattern = Pattern.compile("\\s+");

    @ApiStatus.Internal
    public static final Pattern _variationPattern = Pattern.compile("(?<tag>[a-z0-9]{4})=(?<value>\\d+)");

    public static FontVariation parseOne(String s) {
        Matcher m = _variationPattern.matcher(s);
        if (!m.matches())
            throw new IllegalArgumentException("Can't parse FontVariation: " + s);
        float value = Float.parseFloat(m.group("value"));
        return new FontVariation(m.group("tag"), value);
    }

    public static FontVariation[] parse(String s) {
        return _splitPattern.splitAsStream(s).map(FontVariation::parseOne).toArray(FontVariation[]::new);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FontVariation that = (FontVariation) o;
        return _tag == that._tag && Float.compare(that._value, _value) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_tag, _value);
    }
}