package cc.nimbus.utils.render.skija.path.gn;

import org.jetbrains.annotations.ApiStatus;

import static cc.nimbus.utils.math.MathUtils.clamp;

@SuppressWarnings("unused")
public final class Gn {
    public final float width;
    public final float height;
    public final float[] radius = new float[4];
    public final float[] softness = new float[4];

    @ApiStatus.Internal
    private final int _w;
    @ApiStatus.Internal
    private final int _h;
    @ApiStatus.Internal
    private final int[] _r = new int[4];
    @ApiStatus.Internal
    private final int[] _s = new int[4];

    public Gn(float width, float height, float radius, float softness) {
        this(width, height, new float[] {radius, radius, radius, radius}, new float[] {softness, softness, softness, softness});
    }

    public Gn(float width, float height, float[] radius, float softness) {
        this(width, height, radius, new float[] {softness, softness, softness, softness});
    }

    public Gn(float width, float height, float radius, float[] softness) {
        this(width, height, new float[] {radius, radius, radius, radius}, softness);
    }

    public Gn(float width, float height, float[] radius, float[] softness) {
        this.width = width;
        this.height = height;
        this.radius[0] = clamp(radius[0], 0f, 1f);
        this.radius[1] = clamp(radius[1], 0f, 1f);
        this.radius[2] = clamp(radius[2], 0f, 1f);
        this.radius[3] = clamp(radius[3], 0f, 1f);
        this.softness[0] = clamp(softness[0], 0f, 1f);
        this.softness[1] = clamp(softness[1], 0f, 1f);
        this.softness[2] = clamp(softness[2], 0f, 1f);
        this.softness[3] = clamp(softness[3], 0f, 1f);

        this._w = floatToKey(this.width);
        this._h = floatToKey(this.height);
        // radius
        this._r[0] = floatToKey(this.radius[0]);
        this._r[1] = floatToKey(this.radius[1]);
        this._r[2] = floatToKey(this.radius[2]);
        this._r[3] = floatToKey(this.radius[3]);
        // softness
        this._s[0] = floatToKey(this.softness[0]);
        this._s[1] = floatToKey(this.softness[1]);
        this._s[2] = floatToKey(this.softness[2]);
        this._s[3] = floatToKey(this.softness[3]);
    }

    public static boolean isNullOrEmpty(Gn gn) {
        return gn == null || gn.width <= 0 || gn.height <= 0; // 操你妈，我才发现这玩意会崩
    }

    private static int floatToKey(float v) {
        return Math.round(v * 1000f);
    }

    @Override
    public int hashCode() {
        int hash = 0;
        // size
        hash = hash * 31 + this._w;
        hash = hash * 31 + this._h;
        // radius
        hash = hash * 31 + this._r[0];
        hash = hash * 31 + this._r[1];
        hash = hash * 31 + this._r[2];
        hash = hash * 31 + this._r[3];
        // softness
        hash = hash * 31 + this._s[0];
        hash = hash * 31 + this._s[1];
        hash = hash * 31 + this._s[2];
        hash = hash * 31 + this._s[3];
        return hash;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Gn k)) {
            return false;
        }
        return _w == k._w
               && _h == k._h
               && _r[0] == k._r[0]
               && _r[1] == k._r[1]
               && _r[2] == k._r[2]
               && _r[3] == k._r[3]
               && _s[0] == k._s[0]
               && _s[1] == k._s[1]
               && _s[2] == k._s[2]
               && _s[3] == k._s[3];
    }
}
