package io.github.humbleui.skija;

import java.util.*;
import org.jetbrains.annotations.*;

/**
 * <p>Specify B and C (each between 0...1) to create a shader that applies the corresponding
 * cubic reconstruction filter to the image.</p>
 *
 * <p>Example values:</p>
 * <dl>
 * <dt>B = 1/3, C = 1/3</dt><dd>"Mitchell" filter</dd>
 * <dt>B = 0,   C = 1/2</dt><dd>"Catmull-Rom" filter</dd>
 * </dl>
 *
 * <p>See</p>
 * <ul>
 * <li>"Reconstruction Filters in Computer Graphics" Don P. Mitchell, Arun N. Netravali, 1988
 * <a href="https://www.cs.utexas.edu/~fussell/courses/cs384g-fall2013/lectures/mitchell/Mitchell.pdf">https://www.cs.utexas.edu/~fussell/courses/cs384g-fall2013/lectures/mitchell/Mitchell.pdf</a></li>
 * <li>Desmos worksheet <a href="https://www.desmos.com/calculator/aghdpicrvr">https://www.desmos.com/calculator/aghdpicrvr</a></li>
 * <li>Nice overview <a href="https://entropymate.com/imageworsener/bicubic/">https://entropymate.com/imageworsener/bicubic/</a></li>
 * </ul>
 */
public class CubicResampler implements SamplingMode {
    @ApiStatus.Internal public final float _B;
    @ApiStatus.Internal public final float _C;

    public CubicResampler(float _B, float _C) {
        this._B = _B;
        this._C = _C;
    }

    public float getB() {
        return this._B;
    }

    public float getC() {
        return this._C;
    }

    @ApiStatus.Internal @Override
    public long _pack() {
        return 0x8000000000000000L | (0x3FFFFFFFFFFFFFFFL & (((long) Float.floatToIntBits(_B) << 32) | (long) Float.floatToIntBits(_C)));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CubicResampler that = (CubicResampler) o;
        return Float.compare(that._B, _B) == 0 && Float.compare(that._C, _C) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_B, _C);
    }

    @Override
    public String toString() {
        return "CubicResampler{" +
                "_B=" + _B +
                ", _C=" + _C +
                '}';
    }
}