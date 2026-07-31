package cc.nimbus.utils.render.skija.internal.geometry.gn;

import java.util.Arrays;

import static cc.nimbus.utils.math.MathUtils.clamp;

public final class SmoothRectSpec {
    final float width;
    final float height;
    final float[] radius;
    final float[] softness;
    private final int widthKey;
    private final int heightKey;
    private final int[] radiusKeys;
    private final int[] softnessKeys;
    private final int hash;

    public SmoothRectSpec(float width, float height, float radius, float softness) {
        this(width, height, fill(radius), fill(softness));
    }

    public SmoothRectSpec(float width, float height, float[] radius, float[] softness) {
        this.width = width;
        this.height = height;
        this.radius = normalize(radius, "radius");
        this.softness = normalize(softness, "softness");
        this.widthKey = Math.round(width * 1000f);
        this.heightKey = Math.round(height * 1000f);
        this.radiusKeys = toKeys(this.radius);
        this.softnessKeys = toKeys(this.softness);
        this.hash = computeHash();
    }

    static boolean isEmpty(SmoothRectSpec spec) {
        return spec == null || spec.width <= 0f || spec.height <= 0f;
    }

    private static float[] fill(float value) {
        return new float[]{value, value, value, value};
    }

    private static float[] normalize(float[] values, String name) {
        if (values == null || values.length != 4) {
            throw new IllegalArgumentException(name + " must contain four values");
        }
        float[] normalized = values.clone();
        for (int index = 0; index < normalized.length; index++) {
            normalized[index] = clamp(normalized[index], 0f, 1f);
        }
        return normalized;
    }

    private int computeHash() {
        int result = widthKey;
        result = 31 * result + heightKey;
        result = 31 * result + Arrays.hashCode(radiusKeys);
        return 31 * result + Arrays.hashCode(softnessKeys);
    }

    private static int[] toKeys(float[] values) {
        int[] keys = new int[values.length];
        for (int index = 0; index < values.length; index++) {
            keys[index] = Math.round(values[index] * 1000f);
        }
        return keys;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SmoothRectSpec spec)) return false;
        return widthKey == spec.widthKey
                && heightKey == spec.heightKey
                && Arrays.equals(radiusKeys, spec.radiusKeys)
                && Arrays.equals(softnessKeys, spec.softnessKeys);
    }

    @Override
    public int hashCode() {
        return hash;
    }
}
