package io.github.humbleui.skija;

import java.util.*;
import org.jetbrains.annotations.*;

public class EncodeWEBPOptions {
    public static final EncodeWEBPOptions DEFAULT = new EncodeWEBPOptions(EncodeWEBPCompressionMode.LOSSY, 100f);

    @ApiStatus.Internal
    public final EncodeWEBPCompressionMode _compressionMode;

    @ApiStatus.Internal
    public final float _quality;

    @ApiStatus.Internal
    public EncodeWEBPOptions(EncodeWEBPCompressionMode compression, float quality) {
        _compressionMode = compression;
        _quality = quality;
    }

    public EncodeWEBPCompressionMode getCompressionMode() {
        return this._compressionMode;
    }

    public float getQuality() {
        return this._quality;
    }

    public EncodeWEBPOptions withCompressionMode(EncodeWEBPCompressionMode _compressionMode) {
        return this._compressionMode == _compressionMode ? this : new EncodeWEBPOptions(_compressionMode, this._quality);
    }

    public EncodeWEBPOptions withQuality(float _quality) {
        return this._quality == _quality ? this : new EncodeWEBPOptions(this._compressionMode, _quality);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EncodeWEBPOptions that = (EncodeWEBPOptions) o;
        return Float.compare(that._quality, _quality) == 0 &&
                _compressionMode == that._compressionMode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_compressionMode, _quality);
    }

    @Override
    public String toString() {
        return "EncodeWEBPOptions{" +
                "_compressionMode=" + _compressionMode +
                ", _quality=" + _quality +
                '}';
    }
}