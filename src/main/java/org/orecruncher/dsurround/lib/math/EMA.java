package org.orecruncher.dsurround.lib.math;

/**
 * Simple EMA calculator.
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

    public double get() {
        return this.ema;
    }

}