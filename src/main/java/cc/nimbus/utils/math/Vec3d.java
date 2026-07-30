//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package cc.nimbus.utils.math;

import com.mojang.serialization.Codec;
import java.util.EnumSet;
import java.util.List;

import net.minecraft.util.Util;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec2;
import org.joml.Vector3f;

public class Vec3d {
    public static final Codec<Vec3d> CODEC;
    public static final Vec3d ZERO;
    public double x;
    public double y;
    public double z;

    public static Vec3d unpackRgb(int rgb) {
        double d = (double)(rgb >> 16 & 255) / (double)255.0F;
        double e = (double)(rgb >> 8 & 255) / (double)255.0F;
        double f = (double)(rgb & 255) / (double)255.0F;
        return new Vec3d(d, e, f);
    }

    public static Vec3d of(Vec3i vec) {
        return new Vec3d((double)vec.getX(), (double)vec.getY(), (double)vec.getZ());
    }

    public static Vec3d add(Vec3i vec, double deltaX, double deltaY, double deltaZ) {
        return new Vec3d((double)vec.getX() + deltaX, (double)vec.getY() + deltaY, (double)vec.getZ() + deltaZ);
    }

    public static Vec3d ofCenter(Vec3i vec) {
        return add(vec, (double)0.5F, (double)0.5F, (double)0.5F);
    }

    public static Vec3d ofBottomCenter(Vec3i vec) {
        return add(vec, (double)0.5F, (double)0.0F, (double)0.5F);
    }

    public static Vec3d ofCenter(Vec3i vec, double deltaY) {
        return add(vec, (double)0.5F, deltaY, (double)0.5F);
    }

    public Vec3d(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vec3d(Vector3f vec) {
        this((double)vec.x(), (double)vec.y(), (double)vec.z());
    }

    public Vec3d relativize(Vec3d vec) {
        return new Vec3d(vec.x - this.x, vec.y - this.y, vec.z - this.z);
    }

    public Vec3d normalize() {
        double d = Math.sqrt(this.x * this.x + this.y * this.y + this.z * this.z);
        return d < 1.0E-4 ? ZERO : new Vec3d(this.x / d, this.y / d, this.z / d);
    }

    public double dotProduct(Vec3d vec) {
        return this.x * vec.x + this.y * vec.y + this.z * vec.z;
    }

    public Vec3d crossProduct(Vec3d vec) {
        return new Vec3d(this.y * vec.z - this.z * vec.y, this.z * vec.x - this.x * vec.z, this.x * vec.y - this.y * vec.x);
    }

    public Vec3d subtract(Vec3d vec) {
        return this.subtract(vec.x, vec.y, vec.z);
    }

    public Vec3d subtract(double x, double y, double z) {
        return this.add(-x, -y, -z);
    }

    public Vec3d add(Vec3d vec) {
        return this.add(vec.x, vec.y, vec.z);
    }

    public Vec3d add(double x, double y, double z) {
        return new Vec3d(this.x + x, this.y + y, this.z + z);
    }

    public boolean isInRange(Position pos, double radius) {
        return this.squaredDistanceTo(pos.x(), pos.y(), pos.z()) < radius * radius;
    }

    public double distanceTo(Vec3d vec) {
        double d = vec.x - this.x;
        double e = vec.y - this.y;
        double f = vec.z - this.z;
        return Math.sqrt(d * d + e * e + f * f);
    }

    public double squaredDistanceTo(Vec3d vec) {
        double d = vec.x - this.x;
        double e = vec.y - this.y;
        double f = vec.z - this.z;
        return d * d + e * e + f * f;
    }

    public double squaredDistanceTo(double x, double y, double z) {
        double d = x - this.x;
        double e = y - this.y;
        double f = z - this.z;
        return d * d + e * e + f * f;
    }

    public boolean isWithinRangeOf(Vec3d vec, double horizontalRange, double verticalRange) {
        double d = vec.getX() - this.x;
        double e = vec.getY() - this.y;
        double f = vec.getZ() - this.z;
        return Mth.lengthSquared(d, f) < Mth.square(horizontalRange) && Math.abs(e) < verticalRange;
    }

    public Vec3d multiply(double value) {
        return this.multiply(value, value, value);
    }

    public Vec3d negate() {
        return this.multiply((double)-1.0F);
    }

    public Vec3d multiply(Vec3d vec) {
        return this.multiply(vec.x, vec.y, vec.z);
    }

    public Vec3d multiply(double x, double y, double z) {
        return new Vec3d(this.x * x, this.y * y, this.z * z);
    }

    public Vec3d addRandom(RandomSource random, float multiplier) {
        return this.add((double)((random.nextFloat() - 0.5F) * multiplier), (double)((random.nextFloat() - 0.5F) * multiplier), (double)((random.nextFloat() - 0.5F) * multiplier));
    }

    public double length() {
        return Math.sqrt(this.x * this.x + this.y * this.y + this.z * this.z);
    }

    public double lengthSquared() {
        return this.x * this.x + this.y * this.y + this.z * this.z;
    }

    public double horizontalLength() {
        return Math.sqrt(this.x * this.x + this.z * this.z);
    }

    public double horizontalLengthSquared() {
        return this.x * this.x + this.z * this.z;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (!(o instanceof Vec3d)) {
            return false;
        } else {
            Vec3d vec3d = (Vec3d)o;
            if (Double.compare(vec3d.x, this.x) != 0) {
                return false;
            } else if (Double.compare(vec3d.y, this.y) != 0) {
                return false;
            } else {
                return Double.compare(vec3d.z, this.z) == 0;
            }
        }
    }

    public int hashCode() {
        long l = Double.doubleToLongBits(this.x);
        int i = (int)(l ^ l >>> 32);
        l = Double.doubleToLongBits(this.y);
        i = 31 * i + (int)(l ^ l >>> 32);
        l = Double.doubleToLongBits(this.z);
        i = 31 * i + (int)(l ^ l >>> 32);
        return i;
    }

    public String toString() {
        return "(" + this.x + ", " + this.y + ", " + this.z + ")";
    }

    public Vec3d lerp(Vec3d to, double delta) {
        return new Vec3d(Mth.lerp(delta, this.x, to.x), Mth.lerp(delta, this.y, to.y), Mth.lerp(delta, this.z, to.z));
    }

    public Vec3d rotateX(float angle) {
        float f = Mth.cos(angle);
        float g = Mth.sin(angle);
        double d = this.x;
        double e = this.y * (double)f + this.z * (double)g;
        double h = this.z * (double)f - this.y * (double)g;
        return new Vec3d(d, e, h);
    }

    public Vec3d rotateY(float angle) {
        float f = Mth.cos(angle);
        float g = Mth.sin(angle);
        double d = this.x * (double)f + this.z * (double)g;
        double e = this.y;
        double h = this.z * (double)f - this.x * (double)g;
        return new Vec3d(d, e, h);
    }

    public Vec3d rotateZ(float angle) {
        float f = Mth.cos(angle);
        float g = Mth.sin(angle);
        double d = this.x * (double)f + this.y * (double)g;
        double e = this.y * (double)f - this.x * (double)g;
        double h = this.z;
        return new Vec3d(d, e, h);
    }

    public static Vec3d fromPolar(Vec2 polar) {
        return fromPolar(polar.x, polar.y);
    }

    public static Vec3d fromPolar(float pitch, float yaw) {
        float f = Mth.cos(-yaw * ((float)Math.PI / 180F) - (float)Math.PI);
        float g = Mth.sin(-yaw * ((float)Math.PI / 180F) - (float)Math.PI);
        float h = -Mth.cos(-pitch * ((float)Math.PI / 180F));
        float i = Mth.sin(-pitch * ((float)Math.PI / 180F));
        return new Vec3d((double)(g * h), (double)i, (double)(f * h));
    }

    public Vec3d floorAlongAxes(EnumSet<Direction.Axis> axes) {
        double d = axes.contains(Direction.Axis.X) ? (double)Mth.floor(this.x) : this.x;
        double e = axes.contains(Direction.Axis.Y) ? (double)Mth.floor(this.y) : this.y;
        double f = axes.contains(Direction.Axis.Z) ? (double)Mth.floor(this.z) : this.z;
        return new Vec3d(d, e, f);
    }

    public double getComponentAlongAxis(Direction.Axis axis) {
        return axis.choose(this.x, this.y, this.z);
    }

    public Vec3d withAxis(Direction.Axis axis, double value) {
        double d = axis == Direction.Axis.X ? value : this.x;
        double e = axis == Direction.Axis.Y ? value : this.y;
        double f = axis == Direction.Axis.Z ? value : this.z;
        return new Vec3d(d, e, f);
    }

    public Vec3d offset(Direction direction, double value) {
        Vec3i vec3i = direction.getUnitVec3i();
        return new Vec3d(this.x + value * (double)vec3i.getX(), this.y + value * (double)vec3i.getY(), this.z + value * (double)vec3i.getZ());
    }

    public final double getX() {
        return this.x;
    }

    public final double getY() {
        return this.y;
    }

    public final double getZ() {
        return this.z;
    }

    public Vector3f toVector3f() {
        return new Vector3f((float)this.x, (float)this.y, (float)this.z);
    }

    static {
        CODEC = Codec.DOUBLE.listOf().comapFlatMap((coordinates) -> Util.fixedSize(coordinates, 3).map((coords) -> new Vec3d((Double)coords.get(0), (Double)coords.get(1), (Double)coords.get(2))), (vec) -> List.of(vec.getX(), vec.getY(), vec.getZ()));
        ZERO = new Vec3d((double)0.0F, (double)0.0F, (double)0.0F);
    }
}

