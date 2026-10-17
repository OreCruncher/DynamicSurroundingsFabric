package org.orecruncher.dsurround.lib.math;

/**
 * An on/off switch driven by a value, with a gap between switching on and switching off: on once the value reaches
 * the upper threshold, off only once it falls below the lower one. Something switched by it doesn't flicker on and off
 * as the value wavers around a single line.
 */
public final class Hysteresis {

    private final float on;
    private final float off;
    private boolean state;

    /**
     * @param on  the value from which it switches on
     * @param off the value below which it switches off again; at most {@code on}
     */
    public Hysteresis(float on, float off) {
        if (off > on)
            throw new IllegalArgumentException("Switches off (%s) above where it switches on (%s)".formatted(off, on));
        this.on = on;
        this.off = off;
    }

    /**
     * Takes the latest value, and gives whether it is on now
     */
    public boolean update(float value) {
        this.state = this.state ? value >= this.off : value >= this.on;
        return this.state;
    }

    public boolean isOn() {
        return this.state;
    }

    /**
     * Switches it off, whatever the value was
     */
    public void reset() {
        this.state = false;
    }

    public float onThreshold() {
        return this.on;
    }

    public float offThreshold() {
        return this.off;
    }
}
