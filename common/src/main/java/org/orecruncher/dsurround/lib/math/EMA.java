package org.orecruncher.dsurround.lib.math;

/**
 * Exponential moving average over roughly the last {@code periods} samples. The first sample is taken as is.
 * Not thread-safe.
 */
public class EMA {

    private final String name;
    private final double factor;
    private double ema;

    public EMA() {
        this("UNNAMED");
    }

    public EMA(final String name) {
        this(name, 100);
    }

    public EMA(final String name, final int periods) {
        this.name = name;
        this.factor = 2D / (periods + 1);
        this.ema = Double.NaN;
    }

    public double update(final double newValue) {
        if (Double.isNaN(this.ema)) {
            this.ema = newValue;
        } else {
            this.ema = newValue * this.factor + this.ema * (1 - this.factor);
        }
        return this.ema;
    }

    public String name() {
        return this.name;
    }

    /**
     * The current average, or 0 if there have been no samples yet.
     */
    public double get() {
        return Double.isNaN(this.ema) ? 0D : this.ema;
    }

}