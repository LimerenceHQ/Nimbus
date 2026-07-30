package io.github.humbleui.skija;

import java.util.*;
import org.jetbrains.annotations.*;

public class FilterMipmap implements SamplingMode {
    @ApiStatus.Internal public final FilterMode _filterMode;
    @ApiStatus.Internal public final MipmapMode _mipmapMode;

    public FilterMipmap(FilterMode _filterMode, MipmapMode _mipmapMode) {
        this._filterMode = _filterMode;
        this._mipmapMode = _mipmapMode;
    }

    public FilterMipmap(FilterMode filterMode) {
        this(filterMode, MipmapMode.NONE);
    }

    public FilterMode getFilterMode() {
        return this._filterMode;
    }

    public MipmapMode getMipmapMode() {
        return this._mipmapMode;
    }

    @ApiStatus.Internal @Override
    public long _pack() {
        return 0x3FFFFFFFFFFFFFFFL & (((long) _filterMode.ordinal() << 32) | (long) _mipmapMode.ordinal());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FilterMipmap that = (FilterMipmap) o;
        return _filterMode == that._filterMode && _mipmapMode == that._mipmapMode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_filterMode, _mipmapMode);
    }

    @Override
    public String toString() {
        return "FilterMipmap{" +
                "_filterMode=" + _filterMode +
                ", _mipmapMode=" + _mipmapMode +
                '}';
    }
}