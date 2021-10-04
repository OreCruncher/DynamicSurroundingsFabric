package org.orecruncher.dsurround.lib.math;

/**
 * Specialization of the EMA based on measuring time. The time unit it expects
 * to deal with is nanoseconds.
 */
@SuppressWarnings("unused")
public class TimerEMA extends EMA {

    public TimerEMA(final String name) {
        super(name);
    }

    public TimerEMA(final String name, final int periods) {
        super(name, periods);
    }

    public double getMSecs() {
        return super.get() / 1000000;
    }

    @Override
    public String toString() {
        return String.format("%s:%7.3fms", name(), getMSecs());
    }

}