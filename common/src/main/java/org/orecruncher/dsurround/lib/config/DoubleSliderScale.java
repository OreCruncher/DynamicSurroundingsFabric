package org.orecruncher.dsurround.lib.config;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The positions of a double slider: min, min + step, ... up to max. Cloth only has integer sliders, so a double
 * slider is an integer slider over the positions' indexes, and this converts between the two.
 * <p>
 * Values are worked out in decimal, so a step of 0.1 gives 0.3 rather than 0.30000000000000004, and text has the
 * decimal places of the step (or of the minimum, if it has more), so every position has the same width: 1.50 rather
 * than 1.5 when the step is 0.25. Text always uses "." as the decimal point, as the config file does.
 */
public final class DoubleSliderScale {

    // More than this and the step is probably not meant as a decimal (e.g. 1.0 / 3)
    static final int MAX_DECIMALS = 6;
    // A slider is a couple of hundred pixels wide; more positions than this can't be picked out
    static final int MAX_POSITIONS = 10_000;

    private final BigDecimal min;
    private final BigDecimal step;
    private final int lastIndex;
    private final int decimals;

    /**
     * @throws IllegalArgumentException if min isn't below max, the step isn't positive, the step or minimum has more
     *                                  than {@value #MAX_DECIMALS} decimal places, or the step doesn't divide the range
     *                                  into a whole number of steps
     */
    DoubleSliderScale(double min, double max, double step) {
        if (!(min < max))
            throw new IllegalArgumentException(String.format("the minimum %s isn't below the maximum %s", min, max));
        if (!(step > 0) || Double.isInfinite(step))
            throw new IllegalArgumentException(String.format("the step %s isn't a positive number", step));

        this.min = BigDecimal.valueOf(min);
        this.step = BigDecimal.valueOf(step);
        this.decimals = Math.max(0, Math.max(scaleOf(this.min), scaleOf(this.step)));
        if (this.decimals > MAX_DECIMALS)
            throw new IllegalArgumentException(String.format("the minimum %s or step %s has more than %d decimal places", min, step, MAX_DECIMALS));

        var steps = this.stepsTo(BigDecimal.valueOf(max));
        if (steps == null)
            throw new IllegalArgumentException(String.format("the step %s doesn't divide the range %s to %s evenly", step, min, max));
        if (steps.compareTo(BigDecimal.valueOf(MAX_POSITIONS)) > 0)
            throw new IllegalArgumentException(String.format("the step %s gives more than %d positions", step, MAX_POSITIONS));
        this.lastIndex = steps.intValueExact();
    }

    private static int scaleOf(BigDecimal value) {
        return value.stripTrailingZeros().scale();
    }

    /**
     * The number of whole steps from the minimum to {@code value}, or null if it isn't a whole number.
     */
    private BigDecimal stepsTo(BigDecimal value) {
        var span = value.subtract(this.min);
        // The quotient of two short decimals is exact if it is whole; anything else isn't a position
        var quotient = span.divide(this.step, 0, RoundingMode.DOWN);
        return quotient.multiply(this.step).compareTo(span) == 0 ? quotient : null;
    }

    /**
     * The index of the last position (the maximum); the first is 0.
     */
    public int lastIndex() {
        return this.lastIndex;
    }

    /**
     * Whether {@code value} is exactly one of the positions.
     */
    public boolean isPosition(double value) {
        if (!Double.isFinite(value))
            return false;
        var steps = this.stepsTo(BigDecimal.valueOf(value));
        return steps != null && steps.signum() >= 0 && steps.compareTo(BigDecimal.valueOf(this.lastIndex)) <= 0;
    }

    /**
     * The index of the position nearest {@code value}, limited to the slider.
     */
    public int indexOf(double value) {
        if (Double.isNaN(value))
            return 0;
        var steps = BigDecimal.valueOf(Math.clamp(value, -Double.MAX_VALUE, Double.MAX_VALUE))
                .subtract(this.min)
                .divide(this.step, 0, RoundingMode.HALF_UP);
        if (steps.signum() < 0)
            return 0;
        return steps.compareTo(BigDecimal.valueOf(this.lastIndex)) > 0 ? this.lastIndex : steps.intValueExact();
    }

    /**
     * The value at position {@code index}.
     */
    public double valueAt(int index) {
        return this.decimalAt(index).doubleValue();
    }

    /**
     * The value at position {@code index} as text, e.g. "1.50".
     */
    public String format(int index) {
        return this.decimalAt(index).setScale(this.decimals, RoundingMode.HALF_UP).toPlainString();
    }

    private BigDecimal decimalAt(int index) {
        return this.min.add(this.step.multiply(BigDecimal.valueOf(Math.clamp(index, 0, this.lastIndex))));
    }
}
