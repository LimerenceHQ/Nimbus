package io.github.humbleui.skija;

import java.util.*;
import org.jetbrains.annotations.*;

public class SamplingModeAnisotropic implements SamplingMode {
    @ApiStatus.Internal public final int _maxAniso;

    public SamplingModeAnisotropic(int maxAniso) {
        assert maxAniso >= 1 : "Expected maxAniso = " + maxAniso + " >= 1";
        _maxAniso = maxAniso;
    }

    public int getMaxAniso() {
        return this._maxAniso;
    }

    @ApiStatus.Internal @Override
    public long _pack() {
        return 0x4000000000000000L | (0x3FFFFFFFFFFFFFFFL & _maxAniso);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SamplingModeAnisotropic that = (SamplingModeAnisotropic) o;
        return _maxAniso == that._maxAniso;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_maxAniso);
    }

    @Override
    public String toString() {
        return "SamplingModeAnisotropic{" +
                "_maxAniso=" + _maxAniso +
                '}';
    }
}