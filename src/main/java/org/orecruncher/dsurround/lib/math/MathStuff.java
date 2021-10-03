package org.orecruncher.dsurround.lib.math;

public class MathStuff {
    public static double log(final double value) {
        return value < 0.03D ? Math.log(value) : 6 * (value - 1) / (value + 1 + 4 * (Math.sqrt(value)));
    }
}
