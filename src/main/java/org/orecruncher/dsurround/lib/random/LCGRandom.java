package org.orecruncher.dsurround.lib.random;

/**
 * Simple Linear congruential generator for integer psuedo random numbers.
 * Intended to be fast. Limit is that it can only generate random numbers 0 -
 * 32K.
 */
public final class LCGRandom {

    private long v;

    /**
     * Creates and seeds an LCG using an integer from XorShiftRandom.
     */
    public LCGRandom() {
        this(XorShiftRandom.current().nextLong());
    }

    /**
     * Creates and initializes an LCG generator using a seed value.
     *
     * @param seed Seed to initialize the LCG generator with
     */
    public LCGRandom(final long seed) {
        this.v = seed;
    }

    /**
     * Generates a random number between 0 and the bound specified.
     *
     * @param bound upper bound of the random integer generated
     * @return Pseudo random integer between 0 and bound
     */
    public int nextInt(final int bound) {
        this.v = (2862933555777941757L * this.v + 3037000493L);
        return ((int) ((this.v >> 32) & 0x7FFFFFFF)) % bound;
    }
}