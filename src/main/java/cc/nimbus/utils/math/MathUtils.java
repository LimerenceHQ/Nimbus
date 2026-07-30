package cc.nimbus.utils.math;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class MathUtils {
    public static final Random random = new Random();

    public static final float PI_F = (float) Math.PI;
    public static final float TWO_PI_F = (float) (Math.PI * 2);
    public static final float HALF_PI_F = (float) (Math.PI / 2);
    public static final float DEG_TO_RAD = (float) (Math.PI / 180.0);
    public static final float RAD_TO_DEG = (float) (180.0 / Math.PI);

    public static double getRandomDoubleInRange(double minDouble, double maxDouble) {
        return minDouble >= maxDouble ? minDouble : ThreadLocalRandom.current().nextDouble(minDouble, maxDouble);
    }

    public static float getRandomFloatInRange(float min, float max) {
        return min >= max ? min : min + ThreadLocalRandom.current().nextFloat() * (max - min);
    }

    public static int getRandomIntInRange(int min, int max) {
        return min >= max ? min : ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    public static double interpolate(double delta, double start, double end) {
        return start + delta * (end - start);
    }

    public static float interpolate(float delta, float start, float end) {
        return start + delta * (end - start);
    }

    public static float smoothStep(float edge0, float edge1, float x) {
        float t = clamp((x - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    public static float clampPitch_To90(float pitch) {
        return pitch > 90.0F ? 90.0F : (pitch < -90.0F ? -90.0F : pitch);
    }

    public static float roundToFloat(double d) {
        return (float) (Math.round(d * 1.0E8) / 1.0E8);
    }

    public static double clamp(double num, double min, double max) {
        return num < min ? min : (num > max ? max : num);
    }

    public static float clamp(float num, float min, float max) {
        return num < min ? min : (num > max ? max : num);
    }

    public static double incValue(double val, double inc) {
        double one = 1.0 / inc;
        return Math.round(val * one) / one;
    }

    public static int clamp_int(int num, int min, int max) {
        return num < min ? min : (num > max ? max : num);
    }

    public static float fastInvSqrt(float x) {
        float xhalf = 0.5f * x;
        int i = Float.floatToIntBits(x);
        i = 0x5f3759df - (i >> 1);
        x = Float.intBitsToFloat(i);
        x *= (1.5f - xhalf * x * x);
        return x;
    }

    public static float wrapDegrees(float value) {
        value = value % 360.0F;
        if (value >= 180.0F) {
            value -= 360.0F;
        }
        if (value < -180.0F) {
            value += 360.0F;
        }
        return value;
    }

    public static float angleDifference(float angle1, float angle2) {
        float diff = wrapDegrees(angle2 - angle1);
        return Math.abs(diff);
    }

    public static double distanceSq(double x1, double y1, double z1, double x2, double y2, double z2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        return dx * dx + dy * dy + dz * dz;
    }

    public static double distanceSq2D(double x1, double z1, double x2, double z2) {
        double dx = x2 - x1;
        double dz = z2 - z1;
        return dx * dx + dz * dz;
    }
}
