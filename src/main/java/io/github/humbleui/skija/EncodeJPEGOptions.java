package io.github.humbleui.skija;

import java.util.*;
import org.jetbrains.annotations.*;

public class EncodeJPEGOptions {
    public static final EncodeJPEGOptions DEFAULT = new EncodeJPEGOptions(100, EncodeJPEGDownsampleMode.DS_420, EncodeJPEGAlphaMode.IGNORE);

    @ApiStatus.Internal
    public final int _quality;

    @ApiStatus.Internal
    public final EncodeJPEGDownsampleMode _downsampleMode;

    @ApiStatus.Internal
    public final EncodeJPEGAlphaMode _alphaMode;

    public EncodeJPEGOptions(int _quality, EncodeJPEGDownsampleMode _downsampleMode, EncodeJPEGAlphaMode _alphaMode) {
        this._quality = _quality;
        this._downsampleMode = _downsampleMode;
        this._alphaMode = _alphaMode;
    }

    public int getQuality() {
        return this._quality;
    }

    public EncodeJPEGDownsampleMode getDownsampleMode() {
        return this._downsampleMode;
    }

    public EncodeJPEGAlphaMode getAlphaMode() {
        return this._alphaMode;
    }

    public EncodeJPEGOptions withQuality(int _quality) {
        return this._quality == _quality ? this : new EncodeJPEGOptions(_quality, this._downsampleMode, this._alphaMode);
    }

    public EncodeJPEGOptions withDownsampleMode(EncodeJPEGDownsampleMode _downsampleMode) {
        return this._downsampleMode == _downsampleMode ? this : new EncodeJPEGOptions(this._quality, _downsampleMode, this._alphaMode);
    }

    public EncodeJPEGOptions withAlphaMode(EncodeJPEGAlphaMode _alphaMode) {
        return this._alphaMode == _alphaMode ? this : new EncodeJPEGOptions(this._quality, this._downsampleMode, _alphaMode);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EncodeJPEGOptions that = (EncodeJPEGOptions) o;
        return _quality == that._quality &&
                _downsampleMode == that._downsampleMode &&
                _alphaMode == that._alphaMode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_quality, _downsampleMode, _alphaMode);
    }

    @Override
    public String toString() {
        return "EncodeJPEGOptions{" +
                "_quality=" + _quality +
                ", _downsampleMode=" + _downsampleMode +
                ", _alphaMode=" + _alphaMode +
                '}';
    }
}