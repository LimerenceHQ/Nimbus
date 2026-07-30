package io.github.humbleui.skija;

import java.util.*;
import org.jetbrains.annotations.*;

/**
 *  <p>A compressed form of a rotation+scale matrix.</p>
 *
 *  <pre>[ fSCos     -fSSin    fTx ]
 *  [ fSSin      fSCos    fTy ]
 *  [     0          0      1 ]</pre>
 */
public class RSXform {
    @ApiStatus.Internal
    public final float _scos, _ssin, _tx, _ty;

    public RSXform(float _scos, float _ssin, float _tx, float _ty) {
        this._scos = _scos;
        this._ssin = _ssin;
        this._tx = _tx;
        this._ty = _ty;
    }

    public float getScos() {
        return this._scos;
    }

    public float getSsin() {
        return this._ssin;
    }

    public float getTx() {
        return this._tx;
    }

    public float getTy() {
        return this._ty;
    }

    /**
     * Initialize a new xform based on the scale, rotation (in radians), final tx,ty location
     * and anchor-point ax,ay within the src quad.
     *
     * Note: the anchor point is not normalized (e.g. 0...1) but is in pixels of the src image.
     */
    public static RSXform makeFromRadians(float scale, float radians, float tx, float ty, float ax, float ay) {
        float s = (float) Math.sin(radians) * scale;
        float c = (float) Math.cos(radians) * scale;
        return new RSXform(c, s, tx + -c * ax + s * ay, ty + -s * ax - c * ay);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RSXform rsxForm = (RSXform) o;
        return Float.compare(rsxForm._scos, _scos) == 0 &&
                Float.compare(rsxForm._ssin, _ssin) == 0 &&
                Float.compare(rsxForm._tx, _tx) == 0 &&
                Float.compare(rsxForm._ty, _ty) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_scos, _ssin, _tx, _ty);
    }

    @Override
    public String toString() {
        return "RSXform{" +
                "_scos=" + _scos +
                ", _ssin=" + _ssin +
                ", _tx=" + _tx +
                ", _ty=" + _ty +
                '}';
    }
}