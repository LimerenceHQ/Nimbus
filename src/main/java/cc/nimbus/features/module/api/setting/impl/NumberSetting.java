package cc.nimbus.features.module.api.setting.impl;

import cc.nimbus.features.module.api.setting.Setting;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.function.Supplier;

public class NumberSetting extends Setting<Number> {
    private final double minimum;
    private final double maximum;
    private final double increment;
    public NumberSetting(String name, Number value, Number min, Number max, Number increment) { this(name, value, min, max, increment, () -> true); }
    public NumberSetting(String name, Number value, Number min, Number max, Number increment, Supplier<Boolean> visibility) {
        super(name, initial(value, min, max, increment), visibility);
        if (min.doubleValue() > max.doubleValue() || increment.doubleValue() <= 0) throw new IllegalArgumentException("invalid numeric range");
        this.minimum = min.doubleValue(); this.maximum = max.doubleValue(); this.increment = increment.doubleValue();
    }
    private static double initial(Number value, Number min, Number max, Number increment) {
        double lo = min.doubleValue(), hi = max.doubleValue(), step = increment.doubleValue();
        if (lo > hi || step <= 0) throw new IllegalArgumentException("invalid numeric range");
        double clamped = Math.max(lo, Math.min(hi, value.doubleValue()));
        return lo + Math.round((clamped - lo) / step) * step;
    }
    @Override protected Number normalize(Number value) {
        if (value == null || !Double.isFinite(value.doubleValue())) return getValue();
        double clamped = Math.max(minimum, Math.min(maximum, value.doubleValue()));
        double steps = Math.round((clamped - minimum) / increment);
        double aligned = minimum + steps * increment;
        int scale = Math.max(BigDecimal.valueOf(increment).stripTrailingZeros().scale(), 0);
        return BigDecimal.valueOf(Math.max(minimum, Math.min(maximum, aligned))).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }
    public Number getMinimum() { return minimum; }
    public Number getMaximum() { return maximum; }
    public Number getIncrement() { return increment; }
}
