package io.github.humbleui.skija.shaper;

import java.util.*;
import org.jetbrains.annotations.*;
import io.github.humbleui.skija.*;

public class ShapingOptions {
    public static final ShapingOptions DEFAULT = new ShapingOptions(null, null, true, true, true);

    @ApiStatus.Internal @Nullable
    public final FontMgr _fontMgr;

    @ApiStatus.Internal @Nullable
    public final FontFeature[] _features;

    @ApiStatus.Internal
    public final boolean _leftToRight;

    /**
     * If enabled, fallback font runs will not be broken by whitespace from original font
     */
    @ApiStatus.Internal
    public final boolean _approximateSpaces;

    /**
     * If enabled, fallback font runs will not be broken by punctuation from original font
     */
    @ApiStatus.Internal
    public final boolean _approximatePunctuation;

    public ShapingOptions(@Nullable FontMgr _fontMgr, @Nullable FontFeature[] _features, boolean _leftToRight, boolean _approximateSpaces, boolean _approximatePunctuation) {
        this._fontMgr = _fontMgr;
        this._features = _features;
        this._leftToRight = _leftToRight;
        this._approximateSpaces = _approximateSpaces;
        this._approximatePunctuation = _approximatePunctuation;
    }

    @Nullable
    public FontMgr getFontMgr() {
        return this._fontMgr;
    }

    @Nullable
    public FontFeature[] getFeatures() {
        return this._features;
    }

    public boolean isLeftToRight() {
        return this._leftToRight;
    }

    public boolean isApproximateSpaces() {
        return this._approximateSpaces;
    }

    public boolean isApproximatePunctuation() {
        return this._approximatePunctuation;
    }

    public ShapingOptions withFontMgr(@Nullable FontMgr _fontMgr) {
        return this._fontMgr == _fontMgr ? this : new ShapingOptions(_fontMgr, this._features, this._leftToRight, this._approximateSpaces, this._approximatePunctuation);
    }

    public ShapingOptions withFeatures(@Nullable FontFeature[] _features) {
        return this._features == _features ? this : new ShapingOptions(this._fontMgr, _features, this._leftToRight, this._approximateSpaces, this._approximatePunctuation);
    }

    public ShapingOptions withLeftToRight(boolean _leftToRight) {
        return this._leftToRight == _leftToRight ? this : new ShapingOptions(this._fontMgr, this._features, _leftToRight, this._approximateSpaces, this._approximatePunctuation);
    }

    public ShapingOptions withApproximateSpaces(boolean _approximateSpaces) {
        return this._approximateSpaces == _approximateSpaces ? this : new ShapingOptions(this._fontMgr, this._features, this._leftToRight, _approximateSpaces, this._approximatePunctuation);
    }

    public ShapingOptions withApproximatePunctuation(boolean _approximatePunctuation) {
        return this._approximatePunctuation == _approximatePunctuation ? this : new ShapingOptions(this._fontMgr, this._features, this._leftToRight, this._approximateSpaces, _approximatePunctuation);
    }

    @NotNull
    public ShapingOptions withFeatures(@Nullable String featuresString) {
        return featuresString == null ? withFeatures((FontFeature[]) null) : withFeatures(FontFeature.parse(featuresString));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ShapingOptions that = (ShapingOptions) o;
        return _leftToRight == that._leftToRight &&
                _approximateSpaces == that._approximateSpaces &&
                _approximatePunctuation == that._approximatePunctuation &&
                Objects.equals(_fontMgr, that._fontMgr) &&
                Arrays.equals(_features, that._features);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(_fontMgr, _leftToRight, _approximateSpaces, _approximatePunctuation);
        result = 31 * result + Arrays.hashCode(_features);
        return result;
    }

    @Override
    public String toString() {
        return "ShapingOptions{" +
                "_fontMgr=" + _fontMgr +
                ", _features=" + Arrays.toString(_features) +
                ", _leftToRight=" + _leftToRight +
                ", _approximateSpaces=" + _approximateSpaces +
                ", _approximatePunctuation=" + _approximatePunctuation +
                '}';
    }
}